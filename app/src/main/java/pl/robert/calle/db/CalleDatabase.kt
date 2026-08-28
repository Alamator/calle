package pl.robert.calle.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WalkedWayEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CalleDatabase : RoomDatabase() {
    abstract fun walkedWayDao(): WalkedWayDao

    companion object {
        fun create(context: Context): CalleDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CalleDatabase::class.java,
                "calle.db",
            ).build()
        }
    }
}
