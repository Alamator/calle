# Calle

Offline Venice street-walking map for Android. Package `pl.robert.calle`.

Calle snaps GPS to OpenStreetMap pedestrian ways in the *centro storico* and Giudecca, paints unwalked calli muted slate and walked ones amber, and stores walked way IDs locally. No Google Maps key. The street graph is bundled in the APK.

## What it does

- Dark MapLibre map of Venice alleys, without online tiles
- GPS snapped to OSM footways / pedestrian / steps / living streets
- Canal-crossing snaps rejected unless the way is a *ponte*
- Hysteresis so the fix does not chatter between neighbouring calli
- Room persistence of walked OSM way IDs
- Foreground Start / Stop tracking (Polish UI)
- Tap a street to mark or unmark it by hand
- Debug overlay: raw GPS (red) vs snapped point (amber)

Built for a Pixel 8 Pro (`minSdk 26`, `targetSdk 35`).

## Clone and install

```bash
git clone https://github.com/Alamator/calle.git
cd calle
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On the phone: allow location (and notifications on Android 13+), then **Start śledzenia**. Walk; streets turn amber. Toggle **Debug** to see raw vs snapped.

## Build requirements

- JDK 17+
- Android SDK platform 35 and build-tools 35.0.0 (`ANDROID_HOME` / `local.properties` `sdk.dir`)

## Regenerating the offline graph

```bash
python3 scripts/fetch_venice_graph.py app/src/main/assets/venice_graph.geojson
```

Data © OpenStreetMap contributors.
