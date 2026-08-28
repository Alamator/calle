package pl.robert.calle

import android.app.Application
import org.maplibre.android.MapLibre
import pl.robert.calle.db.CalleDatabase

class CalleApp : Application() {
    lateinit var database: CalleDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        database = CalleDatabase.create(this)
    }
}
