// MainActivity.kt
package com.kavitababy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kavitababy.data.PdfScannerService
import com.kavitababy.service.FloatingWidgetService
import com.kavitababy.ui.ChatScreen
import com.kavitababy.ui.theme.KavitaBabyTheme
import com.kavitababy.voice.VoiceManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var voiceManager: VoiceManager
    private lateinit var pdfScanner: PdfScannerService

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Whether granted or not, re-check before starting the widget.
        startFloatingWidgetIfPermitted()
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Mic won't work without this; the widget will still show but
            // voice input will silently fail until the user grants it from
            // Settings. Nothing to crash on here — just no-op.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceManager = VoiceManager(this)
        pdfScanner = PdfScannerService(this)

        requestMicPermissionIfNeeded()
        runInitialPdfScan()
        requestOverlayPermissionThenStartWidget()

        setContent {
            KavitaBabyTheme {
                ChatScreen(voiceManager)
            }
        }
    }

    private fun requestMicPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestOverlayPermissionThenStartWidget() {
        if (Settings.canDrawOverlays(this)) {
            startFloatingWidgetIfPermitted()
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun startFloatingWidgetIfPermitted() {
        // Guard is essential: FloatingWidgetService calls windowManager.addView()
        // on creation, which crashes immediately without this permission.
        if (!Settings.canDrawOverlays(this)) return

        val serviceIntent = Intent(this, FloatingWidgetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun runInitialPdfScan() {
        lifecycleScope.launch {
            // Safe to call every launch — PdfScannerService skips PDFs
            // that are already indexed, so this is cheap after the first run.
            pdfScanner.scanAllPdfs()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.destroy()
    }
}
