package ti.android.transport

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.actors
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Persistent message outbox for storing messages when offline
 * Uses SQLite database to store messages until they can be delivered
 */
class MessageOutbox private constructor(
    private val context: Context,
    private val onMessageReadyToSend: (String) -> Unit
) {

    companion object {
        private var instance: MessageOutbox? = null
        private val DB_NAME = "message_outbox.db"
        private val TABLE_NAME = "outbox_messages"
        private val COLUMN_ID = "id"
        private val COLUMN_MESSAGE = "message"
        private val COLUMN_TIMESTAMP = "timestamp"
        private val COLUMN_ATTEMPTS = "attempts"
        private val MAX_ATTEMPTS = 5

        fun getInstance(context: Context, 
                       onMessageReadyToSend: (String) -> Unit): MessageOutbox {
            return synchronized(this) {
                if (instance == null) {
                    instance = MessageOutbox(context, onMessageReadyToSend)
                }
                instance!!
            }
        }
    }

    private val dbHelper = object : DatabaseHelper(context) {}
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val sendChannel = Channel<String>(Channel.UNLIMITED)
    private var isProcessing = false

    init {
        startProcessingQueue()
    }

    /**
     * Add a message to the outbox for later delivery
     */
    fun addMessage(message: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_MESSAGE, message)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
            put(COLUMN_ATTEMPTS, 0)
        }
        
        db.insert(TABLE_NAME, null, values)
        db.close()
        
        // Signal that there's a message to process
        sendChannel.send("notify")
    }

    /**
     * Start processing the message queue
     */
    private fun startProcessingQueue() {
        scope.launch {
            while (isActive) {
                // Wait for signal that there might be messages to process
                receive { 
                    onReceive { msg ->
                        // Just consume the notification
                    }
                }
                
                // Process all available messages
                processNextMessage()
                
                // Small delay to prevent tight loop
                delay(100)
            }
        }
    }

    /**
     * Process the next message in the queue
     */
    private fun processNextMessage() {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_ID, COLUMN_MESSAGE, COLUMN_ATTEMPTS),
            "$COLUMN_ATTEMPTS < ?",
            arrayOf(MAX_ATTEMPTS.toString()),
            null,
            null,
            "$COLUMN_TIMESTAMP ASC",
            "1" // Limit to 1
        )

        val messageId = cursor.moveToFirst()?.let {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
            val message = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE))
            val attempts = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ATTEMPTS))
            
            // Try to send the message
            val success = try {
                // Attempt to send via callback
                onMessageReadyToSend(message)
                true
            } catch (e: Exception) {
                false
            }

            if (success) {
                // Message sent successfully, remove from queue
                db.delete(
                    TABLE_NAME,
                    "$COLUMN_ID = ?",
                    arrayOf(id.toString())
                )
                Timber.d("Message sent and removed from outbox: $message")
            } else {
                // Failed to send, increment attempt counter
                val newAttempts = attempts + 1
                val updateValues = ContentValues().apply {
                    put(COLUMN_ATTEMPTS, newAttempts)
                }
                
                db.update(
                    TABLE_NAME,
                    updateValues,
                    "$COLUMN_ID = ?",
                    arrayOf(id.toString())
                )
                
                if (newAttempts >= MAX_ATTEMPTS) {
                    // Max attempts reached, remove and log error
                    db.delete(
                        TABLE_NAME,
                        "$COLUMN_ID = ?",
                        arrayOf(id.toString())
                    )
                    Timber.e("Message failed after $MAX_ATTEMPTS attempts, removed from outbox")
                } else {
                    Timber.w("Message send attempt $newAttempts failed, will retry: $message")
                }
            }
            
            id
        } ?: run {
            // No messages to process
            null
        }

        cursor.close()
        db.close()
        
        // If we processed a message, wait a bit before checking again
        // Otherwise wait longer to avoid excessive DB queries
        val delayMs = if (messageId != null) 500L else 5000L
        // Note: We're in a coroutine, so we'd normally delay here
        // But since this is called from the processing loop, we rely on the loop delay
    }

    /**
     * Get count of messages in outbox
     */
    fun getPendingMessageCount(): Int {
        val db = dbHelper.readableDatabase
        val count = DatabaseUtils.queryNumEntries(db, TABLE_NAME)
        db.close()
        return count.toInt()
    }

    /**
     * Clear all messages from outbox (use with caution)
     */
    fun clear() {
        val db = dbHelper.writableDatabase
        db.delete(TABLE_NAME, null, null)
        db.close()
        Timber.i("Message outbox cleared")
    }

    private val isActive: Boolean
        get() = !job.isCancelled

    private fun cleanup() {
        job.cancel()
        dbHelper.close()
    }

    /**
     * Database helper for the outbox
     */
    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            val createTable = """
                CREATE TABLE $TABLE_NAME (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_MESSAGE TEXT NOT NULL,
                    $COLUMN_TIMESTAMP INTEGER NOT NULL,
                    $COLUMN_ATTEMPTS INTEGER DEFAULT 0
                )
            """.trimIndent()
            db.execSQL(createTable)
            
            // Create index for faster queries
            val indexSql = """
                CREATE INDEX idx_${TABLE_NAME}_attempts_timestamp 
                ON $TABLE_NAME ($COLUMN_ATTEMPTS, $COLUMN_TIMESTAMP)
            """.trimIndent()
            db.execSQL(indexSql)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Handle database upgrades if needed
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
        }
    }
}