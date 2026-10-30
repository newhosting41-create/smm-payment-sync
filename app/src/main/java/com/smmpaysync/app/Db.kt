package com.smmpaysync.app

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

object St {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    const val DUPLICATE = "DUPLICATE"
    const val FLAGGED = "FLAGGED"
    const val FAILED = "FAILED"
    const val REJECTED = "REJECTED"
    const val USED = "USED"          // customer used it on the website

    /** Success page: server confirmed the TrxID was used on the website. */
    val success = listOf(USED)
    /** Everything not used yet (not sent, sent but waiting, failed...). */
    val waiting = listOf(PENDING, FAILED, REJECTED, SYNCED, DUPLICATE, FLAGGED)
    /** Delivered to server, waiting for the customer to claim. */
    val onServer = listOf(SYNCED, DUPLICATE, FLAGGED)
    /** Still has to be (re)sent. */
    val retryable = listOf(PENDING, FAILED, REJECTED)
    /** SMS text no longer needed. */
    val done = listOf(SYNCED, DUPLICATE, FLAGGED, USED)
}

data class Ev(
    val localId: String,
    val provider: String,
    val trxId: String,
    val amount: Double,
    val sender: String,
    val smsTime: String,
    val raw: String,
    val status: String,
    val error: String?,
    val createdAt: Long
)

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "events.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE events (" +
                "localId TEXT PRIMARY KEY, " +
                "provider TEXT NOT NULL DEFAULT 'bkash', " +
                "trxId TEXT NOT NULL UNIQUE, " +
                "amount REAL NOT NULL, " +
                "sender TEXT, " +
                "smsTime TEXT, " +
                "raw TEXT, " +
                "status TEXT NOT NULL, " +
                "error TEXT, " +
                "attempts INTEGER NOT NULL DEFAULT 0, " +
                "createdAt INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE events ADD COLUMN provider TEXT NOT NULL DEFAULT 'bkash'")
        }
    }

    private fun inList(statuses: List<String>) =
        "status IN (" + statuses.joinToString(",") { "'$it'" } + ")"

    /** Returns false when the same TrxID already exists (local duplicate guard). */
    fun insert(e: Ev): Boolean {
        val cv = ContentValues()
        cv.put("localId", e.localId)
        cv.put("provider", e.provider)
        cv.put("trxId", e.trxId)
        cv.put("amount", e.amount)
        cv.put("sender", e.sender)
        cv.put("smsTime", e.smsTime)
        cv.put("raw", e.raw)
        cv.put("status", e.status)
        cv.put("createdAt", e.createdAt)
        cv.put("error", e.error)
        return writableDatabase.insertWithOnConflict(
            "events", null, cv, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    private fun read(where: String?, args: Array<String>?, order: String, limit: String): List<Ev> {
        val out = ArrayList<Ev>()
        val sql = "SELECT localId, provider, trxId, amount, sender, smsTime, raw, status, error, createdAt " +
            "FROM events " + (if (where != null) "WHERE $where " else "") +
            "ORDER BY $order LIMIT $limit"
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Ev(
                        localId = c.getString(0),
                        provider = c.getString(1) ?: "bkash",
                        trxId = c.getString(2),
                        amount = c.getDouble(3),
                        sender = c.getString(4) ?: "",
                        smsTime = c.getString(5) ?: "",
                        raw = c.getString(6) ?: "",
                        status = c.getString(7),
                        error = c.getString(8),
                        createdAt = c.getLong(9)
                    )
                )
            }
        }
        return out
    }

    fun pending(): List<Ev> = read("status='PENDING'", null, "createdAt ASC", "20")

    fun listByStatus(statuses: List<String>, limit: Int): List<Ev> =
        read(inList(statuses), null, "createdAt DESC", limit.toString())

    fun find(trxId: String): Ev? = read("trxId=?", arrayOf(trxId), "createdAt DESC", "1").firstOrNull()

    fun mark(id: String, status: String, error: String?) {
        val cv = ContentValues()
        cv.put("status", status)
        cv.put("error", error)
        // Privacy: once the server accepted it, we no longer need the SMS text.
        // REJECTED / FAILED keep the text so they can be re-sent after a fix.
        if (status in St.done) cv.put("raw", "")
        writableDatabase.update("events", cv, "localId=?", arrayOf(id))
        writableDatabase.execSQL(
            "UPDATE events SET attempts = attempts + 1 WHERE localId = ?", arrayOf<Any>(id)
        )
    }

    fun resetToPending(id: String, raw: String) {
        val cv = ContentValues()
        cv.put("status", St.PENDING)
        cv.putNull("error")
        cv.put("raw", raw)
        writableDatabase.update("events", cv, "localId=?", arrayOf(id))
    }

    /** Re-queue everything that did not reach the server and still has its SMS text. */
    fun retryFailed() {
        writableDatabase.execSQL(
            "UPDATE events SET status='PENDING', error=NULL " +
                "WHERE trxId NOT LIKE 'UNREAD-%' AND (status='FAILED' " +
                "OR (status='REJECTED' AND raw IS NOT NULL AND raw != ''))"
        )
    }

    fun count(statuses: List<String>?): Int =
        DatabaseUtils.queryNumEntries(
            readableDatabase, "events", if (statuses == null) null else inList(statuses)
        ).toInt()

    fun purge(beforeMillis: Long) {
        writableDatabase.delete(
            "events",
            "createdAt < ? AND status NOT IN ('PENDING','FAILED')",
            arrayOf(beforeMillis.toString())
        )
    }

    companion object {
        @Volatile
        private var inst: Db? = null

        fun get(ctx: Context): Db =
            inst ?: synchronized(this) {
                inst ?: Db(ctx.applicationContext).also { inst = it }
            }
    }
}
