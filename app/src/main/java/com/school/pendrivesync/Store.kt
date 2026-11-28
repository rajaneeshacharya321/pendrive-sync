package com.school.pendrivesync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Paths {
    /** Shared "device storage" location (visible in any file manager). */
    val deviceRoot: File get() = File(Environment.getExternalStorageDirectory(), "StudyMaterials")
}

object Access {
    fun ok(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

object AppLog {
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    @Volatile var dir: File? = null

    @Synchronized fun add(msg: String) {
        val d = dir ?: return
        try {
            d.mkdirs()
            File(d, "log.txt").appendText("${fmt.format(Date())}  $msg\n")
        } catch (_: Exception) {}
    }
}

/**
 * All settings are decided when the APK is BUILT (assets/config.json). There are no options inside the app.
 *   saveLocation : "app"    -> files live in the app's private storage (default)
 *                  "device" -> files live in /StudyMaterials on the device (visible in a file manager)
 *   masterIds    : pendrive IDs that receive an export of everything collected.
 *                  Empty list -> the very first pendrive inserted becomes the master.
 *   logoSize     : "normal" | "large" | "xlarge" - how big the logo is on the permanent screen.
 */
class Store(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "sync.db", null, 1) {
    private val appCtx = ctx.applicationContext
    private val prefs = appCtx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    private val cfg: JSONObject by lazy {
        try { JSONObject(appCtx.assets.open("config.json").bufferedReader().use { it.readText() }) }
        catch (_: Exception) { JSONObject() }
    }

    val saveOnDevice: Boolean get() = cfg.optString("saveLocation", "app").equals("device", ignoreCase = true)
    /** Size of the logo on the permanent screen: "normal" (same as the Android start-up screen), "large" or "xlarge". */
    val logoSize: String get() = cfg.optString("logoSize", "normal").lowercase()

    private val presetMasters: Set<String> by lazy {
        val out = HashSet<String>()
        val arr = cfg.optJSONArray("masterIds")
        if (arr != null) for (i in 0 until arr.length()) {
            val s = arr.optString(i).trim().uppercase()
            if (s.isNotEmpty()) out.add(s)
        }
        out
    }

    fun root(): File = if (saveOnDevice) Paths.deviceRoot else File(appCtx.filesDir, "StudyMaterials")
    fun collected(): File = File(root(), "Collected")

    init { AppLog.dir = root() }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE copied(uuid TEXT, rel TEXT, size INTEGER, mtime INTEGER, PRIMARY KEY(uuid, rel, size, mtime))")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    /** Master drives = the ones set at build time + (only if none were set) the first drive ever inserted. */
    var masters: Set<String>
        get() {
            val m = HashSet(prefs.getStringSet("masters", emptySet()) ?: emptySet())
            m.addAll(presetMasters)
            return m
        }
        set(v) { prefs.edit().putStringSet("masters", HashSet(v)).apply() }

    fun has(uuid: String, rel: String, size: Long, mtime: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM copied WHERE uuid=? AND rel=? AND size=? AND mtime=?",
            arrayOf(uuid, rel, size.toString(), mtime.toString())
        ).use { it.moveToFirst() }

    fun add(uuid: String, rel: String, size: Long, mtime: Long) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO copied VALUES(?,?,?,?)", arrayOf<Any>(uuid, rel, size, mtime))
    }
}
