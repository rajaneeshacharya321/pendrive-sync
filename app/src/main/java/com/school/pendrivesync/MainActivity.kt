package com.school.pendrivesync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * No options, no menus. The screen is your logo centred on your colour - the same picture Android shows
 * while the app starts - and it stays that way. The real work happens in SyncService.
 */
class MainActivity : Activity() {
    companion object { private var asked = false }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val dm = resources.displayMetrics
        val shorter = minOf(dm.widthPixels, dm.heightPixels)
        // "normal" = 288dp, exactly what the Android start-up screen uses, so there is no visible jump
        val canvas = when (Store(this).logoSize) {
            "large" -> (shorter * 0.75f).toInt()
            "xlarge" -> (shorter * 1.125f).toInt()
            else -> (288 * dm.density).toInt()
        }
        val root = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.screen_bg)) }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.splash_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(logo, FrameLayout.LayoutParams(canvas, canvas, Gravity.CENTER))
        setContentView(root)
    }

    @Suppress("DEPRECATION")
    private fun goImmersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    /** The screen "stays like that": Back does nothing. (Home still works.) */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {}

    override fun onResume() {
        super.onResume()
        if (Access.ok(this)) startSvc()
        else if (!asked) { asked = true; askAccess() }
    }

    private fun startSvc() {
        Store(this).collected().mkdirs()
        val s = Intent(this, SyncService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s) else startService(s)
    }

    private fun askAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch (_: Exception) {}
            }
        } else {
            requestPermissions(arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        if (Access.ok(this)) startSvc()
    }
}
