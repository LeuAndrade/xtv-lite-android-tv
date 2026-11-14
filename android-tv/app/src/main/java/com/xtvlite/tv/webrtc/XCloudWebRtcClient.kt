package com.xtvlite.tv.webrtc

import android.content.Context
import android.view.Surface
import com.xtvlite.tv.config.StreamConfig
import com.xtvlite.tv.decoder.LowLatencyDecoderFactory
import org.webrtc.*
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * Modelo WebRTC inspirado no XStreaming (Geocld/XStreaming):
 * - Canais DataChannel: "input", "control", "message", "chat"
 * - Modificação de SDP para priorizar H.264/HEVC/VP9 e x-google-min/max-bitrate
 * - Seleção do decodificador de hardware (Compatível, FastDecoder ou SurfaceDecoder)
 */
class XCloudWebRtcClient(
    private val context: Context,
    private val config: StreamConfig,
    private val eglBase: EglBase,
    private val directSurface: Surface?,
    private val onStatsUpdate: (String) -> Unit
) {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var inputChannel: DataChannel? = null
    private var controlChannel: DataChannel? = null
    private var micTrack: AudioTrack? = null

    fun initialize(remoteRenderer: VideoSink?) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val decoderFactory = LowLatencyDecoderFactory(
            config.decoderMode,
            eglBase.eglBaseContext,
            directSurface
        )

        factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(decoderFactory)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, false, false))
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onRenegotiationNeeded() {}
            override fun onDataChannel(dc: DataChannel) {}
            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                val track = receiver.track()
                if (track is VideoTrack && remoteRenderer != null) {
                    track.addSink(remoteRenderer)
                }
            }
        })

        // DataChannels oficiais do protocolo xCloud
        inputChannel = peerConnection?.createDataChannel("input", DataChannel.Init().apply {
            ordered = true
        })
        controlChannel = peerConnection?.createDataChannel("control", DataChannel.Init().apply {
            ordered = true
        })
        peerConnection?.createDataChannel("message", DataChannel.Init())
        peerConnection?.createDataChannel("chat", DataChannel.Init())

        // Trilha de microfone (mutada por padrão até o usuário ligar no menu superior esquerdo)
        val audioSource = factory?.createAudioSource(MediaConstraints())
        micTrack = factory?.createAudioTrack("audio_mic", audioSource)
        micTrack?.setEnabled(false)
        peerConnection?.addTrack(micTrack)
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        micTrack?.setEnabled(enabled)
    }

    fun sendGamepadPacket(buffer: ByteBuffer) {
        val dc = inputChannel ?: return
        if (dc.state() == DataChannel.State.OPEN) {
            dc.send(DataChannel.Buffer(buffer, true))
        }
    }

    fun applySdpOptimization(rawSdp: String): String {
        val minKbps = (config.bitrateMbps * 650).coerceAtLeast(1000)
        val maxKbps = config.bitrateMbps * 1000
        return rawSdp.lines().joinToString("\r\n") { line ->
            if (line.startsWith("a=fmtp:")) {
                "$line;x-google-min-bitrate=$minKbps;x-google-max-bitrate=$maxKbps"
            } else {
                line
            }
        }
    }

    fun startStatsLoop() {
        executor.scheduleAtFixedRate({
            peerConnection?.getStats { report ->
                var fps = 0.0
                var packetsLost = 0L
                for (stats in report.statsMap.values) {
                    if (stats.type == "inbound-rtp" && stats.members["kind"] == "video") {
                        fps = (stats.members["framesPerSecond"] as? Number)?.toDouble() ?: 0.0
                        packetsLost = (stats.members["packetsLost"] as? Number)?.toLong() ?: 0L
                    }
                }
                onStatsUpdate(
                    "Decoder: ${config.decoderMode.label} | ${config.resolution} | FPS: ${fps.toInt()} | Perda: $packetsLost"
                )
            }
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun dispose() {
        executor.shutdownNow()
        inputChannel?.close()
        controlChannel?.close()
        peerConnection?.close()
        factory?.dispose()
    }
}
