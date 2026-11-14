package com.xtvlite.tv.config

import java.io.Serializable

enum class RenderEngine {
    NATIVE,
    WEBVIEW
}

enum class DecoderMode(val label: String) {
    COMPATIBLE("Compatível"),
    FAST_DECODER("Baixa latência própria"),
    SURFACE_DECODER("Saída direta MediaCodec")
}

data class StreamConfig(
    val title: String = "Starfield",
    val productId: String = "9NCJSXWZTP88",
    val engine: RenderEngine = RenderEngine.NATIVE,
    val decoderMode: DecoderMode = DecoderMode.SURFACE_DECODER,
    val showStats: Boolean = true,
    val resolution: String = "720p",
    val bitrateMbps: Int = 8,
    val codec: String = "h264_low",
    val serverRegion: String = "brazilsouth",
    val lowRamMode: Boolean = true,
    val gsToken: String = ""
) : Serializable
