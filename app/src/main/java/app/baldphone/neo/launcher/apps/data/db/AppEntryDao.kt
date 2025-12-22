package app.baldphone.neo.launcher.apps.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

import kotlinx.coroutines.flow.Flow

@Dao
interface AppEntryDao {
    // Auto-invalidated streams by Room on table changes


    @Query("SELECT * FROM app_cache")
    fun observeAll(): Flow<List<AppEntry>>

    @Query("SELECT * FROM app_cache WHERE pinned = 1 ORDER BY label ASC")
    fun observeAllPinned(): Flow<List<AppEntry>>

    @Query("SELECT COUNT(*) FROM app_cache")
    fun observeCount(): Flow<Int>

    // One-shot suspend queries — for sync engine (runs on Dispatchers.IO)

    @Query("SELECT * FROM app_cache")
    suspend fun getAll(): List<AppEntry>

    @Query("SELECT * FROM app_cache ORDER BY LOWER(label)")
    suspend fun getAllOrderedByABC(): List<AppEntry>

    @Query("SELECT * FROM app_cache WHERE pinned = 1 ORDER BY label ASC")
    suspend fun getAllPinned(): List<AppEntry>

    @Query("SELECT * FROM app_cache WHERE package_name = :packageName AND user_id = :userId")
    suspend fun findByPackageName(packageName: String, userId: Long): List<AppEntry>

    @Query("SELECT COUNT(*) FROM app_cache")
    suspend fun getNumberOfRows(): Int

    // Suspend write operations — must be called from a coroutine context

    /**
     * Upsert a single app entry.
     * If [entry]'s primary keys already exist, the row is updated; otherwise a new row is inserted.
     */
    @Upsert
    suspend fun upsert(entry: AppEntry)

    /**
     * Upsert a batch of app entries in a single operation.
     */
    @Upsert
    suspend fun upsertAll(entries: List<AppEntry>)

    @Query("UPDATE app_cache SET pinned = :pinned WHERE package_name = :packageName AND class_name = :className AND user_id = :userId")
    suspend fun updatePinned(packageName: String, className: String, userId: Long, pinned: Boolean)

    @Query("DELETE FROM app_cache WHERE package_name = :packageName AND user_id = :userId")
    suspend fun deleteByPackageName(packageName: String, userId: Long)

    @Delete
    suspend fun deleteAll(entries: List<AppEntry>)

    @Query("DELETE FROM app_cache")
    suspend fun deleteAll()

    // Transaction helpers — for atomic sync operations

    /**
     * Atomically upserts new/changed apps and deletes stale apps in a single transaction.
     */
    @Transaction
    suspend fun syncDiff(toUpsert: List<AppEntry>, toDelete: List<AppEntry>) {
        if (toUpsert.isNotEmpty()) {
            upsertAll(toUpsert)
        }
        if (toDelete.isNotEmpty()) {
            deleteAll(toDelete)
        }
    }
}
