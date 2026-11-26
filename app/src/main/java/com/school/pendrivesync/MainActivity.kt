package com.school.pendrivesync

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {
    companion object {
        var unlocked = false
        var pausedAt = 0L
        private var fails = 0
        private var lockUntil = 0L
        private var askedAccess = false
    }

    private lateinit var store: Store
    private var status: TextView? = null
    private var list: ListView? = null
    private lateinit var cur: File
    private var items: List<File> = emptyList()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        cur = store.collected()
        AppLog.listener = { runOnUiThread { if (unlocked) refresh() } }
    }

    override fun onResume() {
        super.onResume()
        // re-lock after 2 minutes away from the app
        if (unlocked && System.currentTimeMillis() - pausedAt > 120_000) unlocked = false
        if (!Access.ok(this)) {
            if (!askedAccess) { askedAccess = true; askAccess() }
        } else {
            store.collected().mkdirs()
            startSvc()
        }
        if (unlocked) showMain() else showLock()
    }

    override fun onPause() {
        super.onPause()
        pausedAt = System.currentTimeMillis()
    }

    private fun startSvc() {
        val s = Intent(this, SyncService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s) else startService(s)
    }

    // ---------------- PIN lock screen ----------------
    private fun showLock() {
        status = null; list = null
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        val title = TextView(this).apply { text = getString(R.string.app_name); textSize = 26f }
        val msg = TextView(this).apply { text = "Enter PIN to open" }
        val pin = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        val go = Button(this).apply { text = "Unlock" }
        go.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now < lockUntil) {
                msg.text = "Too many tries. Wait ${(lockUntil - now) / 1000 + 1}s"
                return@setOnClickListener
            }
            if (store.checkPin(pin.text.toString())) {
                fails = 0; unlocked = true; showMain()
            } else {
                fails++
                pin.text.clear()
                msg.text = "Wrong PIN"
                if (fails >= 5) { fails = 0; lockUntil = now + 30_000; msg.text = "Too many tries. Wait 30s" }
            }
        }
        pin.setOnEditorActionListener { _, _, _ -> go.performClick(); true }
        col.addView(title); col.addView(msg); col.addView(pin); col.addView(go)
        setContentView(col)
    }

    // ---------------- main screen ----------------
    private fun showMain() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = getString(R.string.app_name); textSize = 22f }
        val menu = Button(this).apply { text = "Menu"; setOnClickListener { showMenu() } }
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(menu)
        val st = TextView(this)
        val lv = ListView(this)
        status = st; list = lv
        col.addView(row)
        col.addView(st)
        col.addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        lv.setOnItemClickListener { _, _, pos, _ ->
            val f = items[pos]
            if (f.isDirectory) { cur = f; refresh() } else open(f)
        }
        setContentView(col)
        refresh()
    }

    private fun showMenu() {
        val where = if (store.saveOnDevice) "Device storage" else "App storage"
        val other = if (store.saveOnDevice) "App storage" else "Device storage"
        val opts = arrayOf(
            "Save location: $where (tap to switch to $other)",
            "Change PIN",
            "Show detected drives",
            "Arm: next drive = ALT master",
            "Reset masters",
            "Grant file access",
            "Lock now"
        )
        AlertDialog.Builder(this).setTitle("Menu").setItems(opts) { _, i ->
            when (i) {
                0 -> {
                    store.saveOnDevice = !store.saveOnDevice
                    store.collected().mkdirs()
                    cur = store.collected()
                    AppLog.add("Save location is now: $other. New copies go there; older files stay where they were.")
                }
                1 -> changePin()
                2 -> showDrives()
                3 -> { store.armAlt = true; AppLog.add("Next inserted drive will become an ALT master") }
                4 -> { store.masters = emptySet(); store.armAlt = false; AppLog.add("Masters reset") }
                5 -> askAccess()
                6 -> { unlocked = false; showLock() }
            }
        }.show()
    }

    private fun changePin() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }
        fun field(h: String) = EditText(this).apply {
            hint = h
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val oldPin = field("Current PIN")
        val new1 = field("New PIN (at least 4 digits)")
        val new2 = field("Repeat new PIN")
        box.addView(oldPin); box.addView(new1); box.addView(new2)
        AlertDialog.Builder(this).setTitle("Change PIN").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val a = new1.text.toString()
                when {
                    !store.checkPin(oldPin.text.toString()) -> toast("Current PIN is wrong")
                    a.length < 4 || !a.all { it.isDigit() } -> toast("New PIN must be at least 4 digits")
                    a != new2.text.toString() -> toast("New PINs don't match")
                    else -> { store.setPin(a); toast("PIN changed") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun showDrives() {
        val sm = getSystemService(StorageManager::class.java)
        sm.storageVolumes.forEach { v ->
            val dir = if (Build.VERSION.SDK_INT >= 30) v.directory else null
            AppLog.add("Drive '${v.getDescription(this)}' id=${v.uuid} removable=${v.isRemovable} state=${v.state} items=${dir?.list()?.size ?: "?"}")
        }
    }

    private fun askAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                catch (_: Exception) { AppLog.add("Cannot open All-files-access settings; grant it manually") }
            }
        } else {
            requestPermissions(arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        if (Access.ok(this)) { store.collected().mkdirs(); startSvc() }
    }

    private fun refresh() {
        val st = status ?: return
        val lv = list ?: return
        st.text = "Masters: ${store.masters.size}" + (if (store.armAlt) "  (alt armed)" else "") +
            "\nSaving to: " + (if (store.saveOnDevice) "Device storage" else "App storage") +
            "\n" + AppLog.tail(6).joinToString("\n") + "\n\n" + cur.path
        items = (cur.listFiles()?.filter { !it.name.endsWith(".part") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))) ?: emptyList()
        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            items.map { (if (it.isDirectory) "[dir] " else "") + it.name })
    }

    private fun open(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fp", f)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) { AppLog.add("Cannot open ${f.name}: ${e.message}") }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (unlocked && cur != store.collected() && cur.parentFile != null) { cur = cur.parentFile!!; refresh() }
        else super.onBackPressed()
    }
}
