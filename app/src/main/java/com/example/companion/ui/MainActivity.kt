package com.example.companion.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.companion.R
import com.example.companion.service.DeviceCompanionService

class MainActivity : ComponentActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnStartMirror: Button
    private var isStreaming = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, DeviceCompanionService::class.java).apply {
                action = DeviceCompanionService.ACTION_START_STREAM
                putExtra(DeviceCompanionService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(DeviceCompanionService.EXTRA_DATA, result.data)
            }
            startForegroundService(serviceIntent)
            btnStartMirror.text = "Streaming..."
            isStreaming = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnStartMirror = findViewById(R.id.btnStartMirror)
        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
        val btnNotifications = findViewById<Button>(R.id.btnNotifications)

        // Start background companion service cleanly in dataSync mode
        val startServiceIntent = Intent(this, DeviceCompanionService::class.java)
        startForegroundService(startServiceIntent)

        btnStartMirror.setOnClickListener {
            if (!isStreaming) {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnNotifications.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }
}
