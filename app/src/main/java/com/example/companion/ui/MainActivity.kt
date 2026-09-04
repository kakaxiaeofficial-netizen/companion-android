package com.example.companion.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.companion.R
import com.example.companion.service.DeviceCompanionService

class MainActivity : ComponentActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var etServerIp: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnStartMirror: Button
    private var isStreaming = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val ip = etServerIp.text.toString().trim()
            val serviceIntent = Intent(this, DeviceCompanionService::class.java).apply {
                action = DeviceCompanionService.ACTION_START_STREAM
                putExtra("EXTRA_SERVER_IP", ip)
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
        etServerIp = findViewById(R.id.etServerIp)
        btnConnect = findViewById(R.id.btnConnect)
        btnStartMirror = findViewById(R.id.btnStartMirror)
        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
        val btnNotifications = findViewById<Button>(R.id.btnNotifications)

        // SharedPreferences se last saved IP load karein
        val prefs = getSharedPreferences("companion_prefs", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("server_ip", "10.97.225.1")
        etServerIp.setText(savedIp)

        fun connectService(ip: String) {
            prefs.edit().putString("server_ip", ip).apply()
            val startServiceIntent = Intent(this, DeviceCompanionService::class.java).apply {
                putExtra("EXTRA_SERVER_IP", ip)
            }
            startForegroundService(startServiceIntent)
            tvStatus.text = "Status: Connecting to $ip:8080..."
            Toast.makeText(this, "Connecting to $ip...", Toast.LENGTH_SHORT).show()
        }

        btnConnect.setOnClickListener {
            val ip = etServerIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                connectService(ip)
            }
        }

        // Auto-connect on open
        val initialIp = etServerIp.text.toString().trim()
        if (initialIp.isNotEmpty()) {
            connectService(initialIp)
        }

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
