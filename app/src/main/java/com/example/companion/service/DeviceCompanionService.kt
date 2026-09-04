package com.example.companion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.companion.manager.FileTransferManager
import com.example.companion.webrtc.WebRtcScreenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit

class DeviceCompanionService : Service(), WebRtcScreenManager.SignalingCallback {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var webRtcManager: WebRtcScreenManager? = null
    private val fileManager by lazy { FileTransferManager(this) }
    private var pendingFileName: String? = null
    private var lastProjectionIntent: Intent? = null
    private var isStreamingActive = false
    private var serverIp: String = "10.97.225.1"

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    companion object {
        const val CHANNEL_ID = "CompanionServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_DISCONNECT = "ACTION_DISCONNECT"
        const val ACTION_START_STREAM = "ACTION_START_STREAM"
        const val ACTION_STOP_STREAM = "ACTION_STOP_STREAM"
        const val ACTION_FORWARD_NOTIFICATION = "ACTION_FORWARD_NOTIFICATION"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newIp = intent?.getStringExtra("EXTRA_SERVER_IP")
        if (!newIp.isNullOrEmpty() && newIp != serverIp) {
            serverIp = newIp
            webSocket?.close(1000, "IP Changed")
            webSocket = null
        }

        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_STREAM -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }

                if (resultCode != -1 && data != null) {
                    lastProjectionIntent = data
                    isStreamingActive = true
                    startScreenStream(resultCode, data)
                }
            }
            ACTION_STOP_STREAM -> {
                isStreamingActive = false
                webRtcManager?.stop()
                webRtcManager = null
                startInitialForeground("Connected to PC ($serverIp)")
            }
            ACTION_FORWARD_NOTIFICATION -> {
                val data = intent.getStringExtra("data")
                if (!data.isNullOrEmpty()) {
                    webSocket?.send(data)
                }
            }
            else -> {
                startInitialForeground("Connecting to PC ($serverIp)...")
            }
        }

        if (webSocket == null) {
            connectToDesktop()
        }

        return START_STICKY
    }

    private fun startInitialForeground(statusText: String) {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        startForeground(NOTIFICATION_ID, createNotification(statusText), serviceType)
    }

    private fun startScreenStream(resultCode: Int, data: Intent) {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }
        startForeground(NOTIFICATION_ID, createNotification("Streaming Screen to PC ($serverIp)"), serviceType)

        webRtcManager = WebRtcScreenManager(this, this)
        webRtcManager?.startScreenCapture(resultCode, data)
        webRtcManager?.createOffer()
    }

    private fun connectToDesktop() {
        serviceScope.launch {
            val request = Request.Builder()
                .url("ws://$serverIp:8080")
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    webSocket = ws
                    val registerMsg = JSONObject().apply {
                        put("role", "phone")
                    }
                    ws.send(registerMsg.toString())
                    updateNotification("Connected to PC ($serverIp)")

                    if (isStreamingActive && lastProjectionIntent != null) {
                        webRtcManager?.createOffer()
                    }
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleRemoteMessage(text)
                }

                override fun onMessage(ws: WebSocket, bytes: ByteString) {
                    pendingFileName?.let {
                        fileManager.handleIncomingChunk(bytes.toByteArray(), it)
                    }
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    webSocket = null
                    updateNotification("Connecting to PC ($serverIp)...")
                    serviceScope.launch {
                        delay(3000)
                        connectToDesktop()
                    }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    webSocket = null
                    updateNotification("Disconnected. Reconnecting...")
                    serviceScope.launch {
                        delay(3000)
                        connectToDesktop()
                    }
                }
            })
        }
    }

    private fun handleRemoteMessage(message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "request_offer" -> {
                    if (isStreamingActive && lastProjectionIntent != null) {
                        webRtcManager?.createOffer()
                    }
                }
                "answer" -> {
                    val sdp = SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp"))
                    webRtcManager?.handleAnswer(sdp)
                }
                "candidate" -> {
                    val candidate = IceCandidate(
                        json.getString("sdpMid"),
                        json.getInt("sdpMLineIndex"),
                        json.getString("candidate")
                    )
                    webRtcManager?.addIceCandidate(candidate)
                }
                "touch_event" -> {
                    val x = json.getDouble("x").toFloat()
                    val y = json.getDouble("y").toFloat()
                    CompanionAccessibilityService.instance?.injectTap(x, y)
                }
                "system_action" -> {
                    val action = json.getString("action")
                    CompanionAccessibilityService.instance?.performSystemAction(action)
                }
                "switch_source" -> {
                    val source = json.getString("source")
                    webRtcManager?.switchStreamSource(source, lastProjectionIntent)
                }
                "file_start" -> {
                    pendingFileName = json.getString("fileName")
                }
                "file_end" -> {
                    pendingFileName?.let { fileManager.finalizeTransfer(it) }
                    pendingFileName = null
                }
                "request_file_list" -> {
                    webSocket?.send(fileManager.listDownloads())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLocalDescription(sdp: SessionDescription) {
        val json = JSONObject().apply {
            put("role", "phone")
            put("type", sdp.type.canonicalForm())
            put("sdp", sdp.description)
        }
        webSocket?.send(json.toString())
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        val json = JSONObject().apply {
            put("role", "phone")
            put("type", "candidate")
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("candidate", candidate.sdp)
        }
        webSocket?.send(json.toString())
    }

    private fun createNotification(contentText: String): Notification {
        val disconnectIntent = Intent(this, DeviceCompanionService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingDisconnect = PendingIntent.getService(
            this, 0, disconnectIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AirDroid Companion")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", pendingDisconnect)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Device Companion Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        webRtcManager?.stop()
        serviceScope.cancel()
        webSocket?.close(1000, "Service Destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
