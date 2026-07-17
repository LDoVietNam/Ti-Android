package ti.android.transport

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Persistent message outbox for storing messages when offline
 * Uses SQLite database for persistence
 */
class MessageOutbox private constructor(
    private val context: Context,
    private val onMessageToSend: (String) -> Unit
) {

    companion object {
        private const val DATABASE_NAME = "outbox.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "outbox_messages"
        private const val COLUMN_ID = "id"
        private const val COLUMN_MESSAGE = "message"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_ATTEMPTS = "attempts"

        @Volatile private var INSTANCE: MessageOutbox? = null

        fun getInstance(
            context: Context,
            onMessageToSend: (String) -> Unit
        ): MessageOutbox {
            return synchronized(this) {
                if (INSTANCE == null) {
                    INSTANCE = MessageOutbox(context, onMessageToSend)
                }
                return INSTANCE!!
            }
        }
    }

    private val dbHelper = DatabaseHelper(context)
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val sendChannel = Channel<String>(Channel.UNLIMITED)
    private var isRunning = false

    init {
        startProcessing()
    }

    /**
     * Add a message to the outbox for later delivery
     */
    suspend fun addMessage(message: String) {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put(COLUMN_MESSAGE, message)
                put(COLUMN_TIMESTAMP, System.currentTimeMillis())
                put(COLUMN_ATTEMPTS, 0)
            }
            db.insert(TABLE_NAME, null, values)
            db.close()
        }
        Timber.d("Message added to outbox")
    }

    /**
     * Start processing messages from the outbox
     */
    private fun startProcessing() {
        if (isRunning) return
        isRunning = true

        // Start sender coroutine
        scope.launch {
            messageSender()
        }

        // Start processor that checks for pending messages
        scope.launch {
            messageProcessor()
        }
    }

    /**
     * Stop processing messages
     */
    fun stop() {
        isRunning = false
        job.cancel()
        sendChannel.close()
    }

    /**
     * Process messages from the database and send them to the send channel
     */
    private fun messageProcessor() {
        while (isActive) {
            val message = getNextMessage()
            if (message != null) {
                sendChannel.send(message)
            } else {
                // No messages, wait a bit before checking again
                delay(5000)
            }
        }
    }

    /**
     * Get the next message to send from the database
     */
    private suspend fun getNextMessage(): String? {
        return withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                true,
                TABLE_NAME,
                arrayOf(COLUMN_ID, COLUMN_MESSAGE, COLUMN_TIMESTAMP, COLUMN_ATTEMPTS),
                null,
                null,
                null,
                null,
                "$COLUMN_ID ASC",
                "1"
            )

            val message = if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val msg = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE))
                val attempts = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ATTEMPTS))
                
                // Increment attempt count
                val updateValues = ContentValues().apply {
                    put(COLUMN_ATTEMPTS, attempts + 1)
                }
                db.update(
                    TABLE_NAME,
                    updateValues,
                    "$COLUMN_ID = ?",
                    arrayOf(id.toString())
                )
                
                msg
            } else {
                null
            }

            cursor.close()
            db.close()
            message
        }
    }

    /**
     * Sender coroutine that takes messages from channel and sends them
     */
    private fun messageSender() {
        while (isActive) {
            try {
                val message = sendChannel.receive()
                onMessageToSend(message)
            } catch (e: Exception) {
                if (isActive) {
                    Timber.e(e, "Error sending message from outbox")
                }
            }
        }
    }

    /**
     * Remove a successfully sent message from the outbox
     */
    internal fun removeMessage(messageId: Long) {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            db.delete(
                TABLE_NAME,
                "$COLUMN_ID = ?",
                arrayOf(messageId.toString())
            )
            db.close()
        }
    }

    /**
     * Get count of pending messages
     */
    fun getPendingCount(): Long {
        return withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            val count = DatabaseUtils.queryNumEntries(db, TABLE_NAME)
            db.close()
            count
        }
    }

    /**
     * Database helper for outbox storage
     */
    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            val createTable = "CREATE TABLE $TABLE_NAME (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_MESSAGE TEXT NOT NULL, " +
                    "$COLUMN_TIMESTAMP INTEGER NOT NULL, " +
                    "$COLUMN_ATTEMPTS INTEGER DEFAULT 0)"
            db.execSQL(createTable)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Handle database upgrades if needed
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
        }
    }

    /**
     * Cleanup resources
     */
    fun shutdown() {
        stop()
        dbHelper.close()
    }
}