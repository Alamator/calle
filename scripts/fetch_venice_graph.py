#!/usr/bin/env python3
"""Download Venice pedestrian ways + canals from Overpass and write a compact GeoJSON graph."""

from __future__ import annotations

import json
import math
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

# Centro storico + Giudecca (south of Canal della Giudecca through Cannaregio).
BBOX = (45.4200, 12.3080, 45.4485, 12.3650)  # s, w, n, e
ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
]

HIGHWAY_QUERY = f"""
[out:json][timeout:180];
way["highway"~"^(footway|path|pedestrian|steps|living_street|residential|unclassified|service|cycleway|track|corridor|bridleway)$"]({BBOX[0]},{BBOX[1]},{BBOX[2]},{BBOX[3]});
out geom tags;
"""

CANAL_QUERY = f"""
[out:json][timeout:180];
(
  way["waterway"~"^(canal|river|stream)$"]({BBOX[0]},{BBOX[1]},{BBOX[2]},{BBOX[3]});
  way["natural"="water"]({BBOX[0]},{BBOX[1]},{BBOX[2]},{BBOX[3]});
);
out geom tags;
"""


def post_overpass(query: str) -> dict:
    body = query.encode("utf-8")
    last_error: Exception | None = None
    for attempt in range(4):
        for url in ENDPOINTS:
            req = urllib.request.Request(
                url,
                data=body,
                headers={
                    "Content-Type": "application/x-www-form-urlencoded",
                    "User-Agent": "calle-graph-fetch/1.0 (offline Venice walking map)",
                },
                method="POST",
            )
            try:
                with urllib.request.urlopen(req, timeout=200) as resp:
                    raw = resp.read()
                return json.loads(raw.decode("utf-8"))
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
                last_error = exc
                print(f"Overpass failed ({url}): {exc}", file=sys.stderr)
                time.sleep(2 + attempt * 3)
    raise RuntimeError(f"All Overpass endpoints failed: {last_error}")


def round_coord(lon: float, lat: float) -> list[float]:
    return [round(lon, 6), round(lat, 6)]


def way_coords(element: dict) -> list[list[float]]:
    geom = element.get("geometry") or []
    coords = [round_coord(p["lon"], p["lat"]) for p in geom if "lon" in p and "lat" in p]
    # Drop consecutive duplicates after rounding.
    cleaned: list[list[float]] = []
    for c in coords:
        if not cleaned or c != cleaned[-1]:
            cleaned.append(c)
    return cleaned


def is_bridge(tags: dict) -> bool:
    bridge = (tags.get("bridge") or "").lower()
    if bridge and bridge not in {"no", "false", "0"}:
        return True
    name = tags.get("name") or tags.get("name:it") or ""
    return "ponte" in name.lower()


def simplify(coords: list[list[float]], tolerance_m: float) -> list[list[float]]:
    if len(coords) <= 2:
        return coords

    def dist_point_seg(p, a, b) -> float:
        # Equirectangular metres around Venice.
        lat0 = math.radians((a[1] + b[1] + p[1]) / 3.0)
        mx = 111320.0 * math.cos(lat0)
        my = 110540.0
        ax, ay = a[0] * mx, a[1] * my
        bx, by = b[0] * mx, b[1] * my
        px, py = p[0] * mx, p[1] * my
        dx, dy = bx - ax, by - ay
        if dx == 0 and dy == 0:
            return math.hypot(px - ax, py - ay)
        t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
        return math.hypot(px - (ax + t * dx), py - (ay + t * dy))

    def rec(pts: list[list[float]]) -> list[list[float]]:
        if len(pts) <= 2:
            return pts
        a, b = pts[0], pts[-1]
        max_d, idx = -1.0, 0
        for i in range(1, len(pts) - 1):
            d = dist_point_seg(pts[i], a, b)
            if d > max_d:
                max_d, idx = d, i
        if max_d > tolerance_m:
            left = rec(pts[: idx + 1])
            right = rec(pts[idx:])
            return left[:-1] + right
        return [a, b]

    return rec(coords)


def feature(kind: str, element: dict, coords: list[list[float]]) -> dict | None:
    if len(coords) < 2:
        return None
    tags = element.get("tags") or {}
    name = tags.get("name") or tags.get("name:it")
    props = {
        "id": int(element["id"]),
        "kind": kind,
        "name": name,
        "bridge": is_bridge(tags),
        "highway": tags.get("highway"),
        "waterway": tags.get("waterway"),
    }
    return {
        "type": "Feature",
        "properties": props,
        "geometry": {"type": "LineString", "coordinates": coords},
    }


def main() -> int:
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("app/src/main/assets/venice_graph.geojson")
    out.parent.mkdir(parents=True, exist_ok=True)

    print("Fetching pedestrian ways…", flush=True)
    highways = post_overpass(HIGHWAY_QUERY)
    print("Fetching canals…", flush=True)
    canals = post_overpass(CANAL_QUERY)

    features: list[dict] = []
    street_count = 0
    for el in highways.get("elements", []):
        if el.get("type") != "way":
            continue
        tags = el.get("tags") or {}
        # Skip indoor corridors that are not useful outdoors.
        if tags.get("indoor") == "yes" or tags.get("level"):
            continue
        coords = simplify(way_coords(el), 1.6)
        feat = feature("street", el, coords)
        if feat:
            features.append(feat)
            street_count += 1

    canal_count = 0
    for el in canals.get("elements", []):
        if el.get("type") != "way":
            continue
        tags = el.get("tags") or {}
        # Lagoon-scale water polygons as ways are huge; keep canal-like water only.
        if tags.get("natural") == "water" and not tags.get("waterway"):
            water = (tags.get("water") or "").lower()
            name = (tags.get("name") or "").lower()
            if water in {"lagoon", "bay"} or "laguna" in name:
                continue
        coords = simplify(way_coords(el), 2.4)
        feat = feature("canal", el, coords)
        if feat:
            features.append(feat)
            canal_count += 1

    collection = {
        "type": "FeatureCollection",
        "name": "calle-venice",
        "attribution": "© OpenStreetMap contributors",
        "bbox": [BBOX[1], BBOX[0], BBOX[3], BBOX[2]],
        "features": features,
    }
    text = json.dumps(collection, ensure_ascii=False, separators=(",", ":"))
    out.write_text(text, encoding="utf-8")
    print(f"Wrote {out} ({out.stat().st_size / 1024:.0f} KiB, {street_count} streets, {canal_count} canals)")
    if street_count < 200:
        print("ERROR: too few streets — Overpass payload looks incomplete", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
