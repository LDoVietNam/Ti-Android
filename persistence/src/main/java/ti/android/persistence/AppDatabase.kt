package ti.android.persistence

import androidx.room.*

// ─── Entities ─────────────────────────────────────────────────────

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val taskId: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "attempts") val attempts: Int = 0,
)

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "trace_id") val traceId: String? = null,
)

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "message_type") val messageType: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "delivered") val delivered: Boolean = false,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
)

// ─── DAOs ─────────────────────────────────────────────────────────

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE taskId = :taskId")
    suspend fun get(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun getActive(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updated_at = :now WHERE taskId = :taskId")
    suspend fun updateStatus(taskId: String, status: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM tasks WHERE created_at < :before")
    suspend fun cleanup(before: Long)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<EventEntity>

    @Insert
    suspend fun insert(event: EventEntity)

    @Query("DELETE FROM events WHERE timestamp < :before")
    suspend fun cleanup(before: Long)
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE delivered = 0 ORDER BY created_at ASC")
    suspend fun getPending(): List<OutboxEntity>

    @Insert
    suspend fun insert(message: OutboxEntity)

    @Query("UPDATE outbox SET delivered = 1 WHERE id = :id")
    suspend fun markDelivered(id: Long)

    @Query("UPDATE outbox SET retry_count = retry_count + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("DELETE FROM outbox WHERE created_at < :before")
    suspend fun cleanup(before: Long)
}

// ─── Database ─────────────────────────────────────────────────────

@Database(
    entities = [TaskEntity::class, EventEntity::class, OutboxEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun eventDao(): EventDao
    abstract fun outboxDao(): OutboxDao
}
