package pl.robert.calle.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WalkedWayDao {
    @Query("SELECT wayId FROM walked_ways")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM walked_ways")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun mark(entity: WalkedWayEntity)

    @Query("DELETE FROM walked_ways WHERE wayId = :wayId")
    suspend fun unmark(wayId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM walked_ways WHERE wayId = :wayId)")
    suspend fun isWalked(wayId: Long): Boolean
}
