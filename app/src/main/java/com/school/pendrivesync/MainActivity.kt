package com.school.pendrivesync

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar

/**
 * No options, no menus. Shows either
 *   - the screen image chosen at build time (assets/screen_image), full screen and for good, or
 *   - a plain loading spinner if no image was chosen.
 * The real work happens in SyncService.
 */
class MainActivity : Activity() {
    companion object { private var asked = false }

    private var hasImage = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val bmp = loadScreenImage()
        hasImage = bmp != null
        if (bmp != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val iv = ImageView(this).apply {
                setBackgroundColor(Color.BLACK)
                setImageBitmap(bmp)
                scaleType = if (Store(this@MainActivity).fillScreen) ImageView.ScaleType.CENTER_CROP
                            else ImageView.ScaleType.FIT_CENTER
            }
            setContentView(iv)
        } else {
            val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#101418")) }
            root.addView(ProgressBar(this), FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            setContentView(root)
        }
    }

    /** Reads assets/screen_image (PNG or JPEG), scaled down if it is huge. Returns null when there is none. */
    private fun loadScreenImage(): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open("screen_image").use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2600 || bounds.outHeight / sample > 2600) sample *= 2
        assets.open("screen_image").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (_: Exception) { null }

    @Suppress("DEPRECATION")
    private fun goImmersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && hasImage) goImmersive()
    }

    /** With a screen image the app "stays like that": Back does nothing. (Home still works.) */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (!hasImage) super.onBackPressed()
    }

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
