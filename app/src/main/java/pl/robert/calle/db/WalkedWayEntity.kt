package pl.robert.calle.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "walked_ways")
data class WalkedWayEntity(
    @PrimaryKey val wayId: Long,
    val markedAtEpochMs: Long,
    val source: String,
)
