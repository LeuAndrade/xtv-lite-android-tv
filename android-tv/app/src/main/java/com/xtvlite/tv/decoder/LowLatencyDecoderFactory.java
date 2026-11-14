package com.xtvlite.tv.decoder;

import android.view.Surface;

import androidx.annotation.Nullable;

import com.xtvlite.tv.config.DecoderMode;

import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.EglBase;
import org.webrtc.VideoCodecInfo;
import org.webrtc.VideoDecoder;
import org.webrtc.VideoDecoderFactory;

/**
 * Seleciona o pipeline de decodificação de hardware ideal para TVs com 1GB de RAM:
 * - COMPATIBLE: DefaultVideoDecoderFactory do WebRTC
 * - FAST_DECODER: FastDecoder (MediaCodec -> SurfaceTexture)
 * - SURFACE_DECODER: SurfaceDecoder (MediaCodec -> SurfaceView direto, zero cópia)
 */
public class LowLatencyDecoderFactory implements VideoDecoderFactory {

    private final DecoderMode mode;
    private final EglBase.Context eglContext;
    @Nullable
    private final Surface directSurface;
    private final DefaultVideoDecoderFactory fallbackFactory;

    public LowLatencyDecoderFactory(
            DecoderMode mode,
            EglBase.Context eglContext,
            @Nullable Surface directSurface
    ) {
        this.mode = mode;
        this.eglContext = eglContext;
        this.directSurface = directSurface;
        this.fallbackFactory = new DefaultVideoDecoderFactory(eglContext);
    }

    @Nullable
    @Override
    public VideoDecoder createDecoder(VideoCodecInfo info) {
        String name = info.name != null ? info.name.toUpperCase() : "";
        boolean isSupportedHw = name.contains("H264") || name.contains("H265") || name.contains("HEVC") || name.contains("VP9");

        if (isSupportedHw && mode == DecoderMode.SURFACE_DECODER && directSurface != null) {
            return new SurfaceDecoder(directSurface);
        }

        if (isSupportedHw && mode == DecoderMode.FAST_DECODER) {
            return new FastDecoder(eglContext);
        }

        return fallbackFactory.createDecoder(info);
    }

    @Override
    public VideoCodecInfo[] getSupportedCodecs() {
        return fallbackFactory.getSupportedCodecs();
    }
}
