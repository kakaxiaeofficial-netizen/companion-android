package com.example.companion.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import org.webrtc.*

class WebRtcScreenManager(
    private val context: Context,
    private val signalingClient: SignalingCallback
) {
    interface SignalingCallback {
        fun onLocalDescription(sdp: SessionDescription)
        fun onIceCandidate(candidate: IceCandidate)
    }

    private var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var videoTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var screenCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private val rootEglBase: EglBase = EglBase.create()

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun startScreenCapture(resultCode: Int, data: Intent) {
        screenCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
            }
        })

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
        videoSource = peerConnectionFactory.createVideoSource(screenCapturer!!.isScreencast)
        
        screenCapturer!!.initialize(surfaceTextureHelper, context, videoSource!!.capturerObserver)
        screenCapturer!!.startCapture(1920, 1080, 30)

        videoTrack = peerConnectionFactory.createVideoTrack("VIDEO_TRACK_ID", videoSource)
        setupPeerConnection()
    }

    private fun setupPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                signalingClient.onIceCandidate(candidate)
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })

        videoTrack?.let {
            peerConnection?.addTrack(it)
        }
    }

    fun createOffer() {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                signalingClient.onLocalDescription(sdp)
            }
        }, constraints)
    }

    fun handleAnswer(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(SdpObserverAdapter(), sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun switchStreamSource(sourceType: String, mediaProjectionData: Intent? = null) {
        try {
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()

            screenCapturer = when (sourceType) {
                "CAMERA_FRONT" -> createCameraCapturer(Camera2Enumerator(context), isFront = true)
                "CAMERA_BACK" -> createCameraCapturer(Camera2Enumerator(context), isFront = false)
                else -> {
                    if (mediaProjectionData != null) {
                        ScreenCapturerAndroid(mediaProjectionData, object : MediaProjection.Callback() {})
                    } else null
                }
            }

            if (surfaceTextureHelper == null) {
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
            }

            if (screenCapturer != null) {
                videoSource = peerConnectionFactory.createVideoSource(sourceType == "SCREEN")
                screenCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
                screenCapturer?.startCapture(1280, 720, 30)
                videoTrack?.setEnabled(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator, isFront: Boolean): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (isFront && enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            } else if (!isFront && enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    fun stop() {
        try {
            screenCapturer?.stopCapture()
            peerConnection?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
