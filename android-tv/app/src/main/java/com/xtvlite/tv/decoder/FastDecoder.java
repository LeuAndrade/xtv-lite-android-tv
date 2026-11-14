package com.xtvlite.tv.decoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.SystemClock;
import android.view.Surface;

import androidx.annotation.Nullable;

import org.webrtc.EglBase;
import org.webrtc.EncodedImage;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCodecStatus;
import org.webrtc.VideoDecoder;
import org.webrtc.VideoFrame;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * FastDecoder: controla diretamente o android.media.MediaCodec com fila mínima
 * e entrega o quadro decodificado via SurfaceTexture antes da composição na tela.
 */
public class FastDecoder implements VideoDecoder {

    private final EglBase.Context eglContext;
    @Nullable private MediaCodec codec;
    @Nullable private SurfaceTextureHelper textureHelper;
    @Nullable private Surface textureSurface;
    @Nullable private Callback decodeCallback;
    private volatile boolean running = false;
    private Thread outputThread;

    public FastDecoder(EglBase.Context eglContext) {
        this.eglContext = eglContext;
    }

    @Override
    public VideoCodecStatus initDecode(Settings settings, Callback callback) {
        this.decodeCallback = callback;
        try {
            textureHelper = SurfaceTextureHelper.create("FastDecoderThread", eglContext);
            textureHelper.setTextureSize(Math.max(settings.width, 1280), Math.max(settings.height, 720));
            textureSurface = new Surface(textureHelper.getSurfaceTexture());

            textureHelper.startListening(frame -> {
                if (decodeCallback != null) {
                    decodeCallback.onDecodedFrame(frame, 4, null);
                }
            });

            MediaFormat format = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    Math.max(settings.width, 1280),
                    Math.max(settings.height, 720)
            );
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
            }
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);

            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(format, textureSurface, null, 0);
            codec.start();
            running = true;
            startOutputDrain();
            return VideoCodecStatus.OK;
        } catch (IOException | IllegalStateException e) {
            release();
            return VideoCodecStatus.FALLBACK_SOFTWARE;
        }
    }

    @Override
    public VideoCodecStatus decode(EncodedImage frame, DecodeInfo info) {
        if (!running || codec == null) return VideoCodecStatus.UNINITIALIZED;
        try {
            int inIdx = codec.dequeueInputBuffer(8000);
            if (inIdx >= 0) {
                ByteBuffer inputBuf = codec.getInputBuffer(inIdx);
                if (inputBuf != null) {
                    inputBuf.clear();
                    ByteBuffer src = frame.buffer.slice();
                    inputBuf.put(src);
                    long ptsUs = frame.captureTimeNs / 1000L;
                    codec.queueInputBuffer(inIdx, 0, src.limit(), ptsUs, 0);
                }
            }
            return VideoCodecStatus.OK;
        } catch (IllegalStateException e) {
            return VideoCodecStatus.ERROR;
        }
    }

    private void startOutputDrain() {
        outputThread = new Thread(() -> {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            while (running && codec != null) {
                try {
                    int outIdx = codec.dequeueOutputBuffer(bufferInfo, 8000);
                    if (outIdx >= 0) {
                        codec.releaseOutputBuffer(outIdx, true);
                    }
                } catch (IllegalStateException ignored) {
                    SystemClock.sleep(4);
                }
            }
        }, "FastDecoderDrain");
        outputThread.start();
    }

    @Override
    public VideoCodecStatus release() {
        running = false;
        if (outputThread != null) {
            outputThread.interrupt();
            outputThread = null;
        }
        if (codec != null) {
            try { codec.stop(); } catch (Exception ignored) {}
            try { codec.release(); } catch (Exception ignored) {}
            codec = null;
        }
        if (textureSurface != null) {
            textureSurface.release();
            textureSurface = null;
        }
        if (textureHelper != null) {
            textureHelper.stopListening();
            textureHelper.dispose();
            textureHelper = null;
        }
        return VideoCodecStatus.OK;
    }

    @Override
    public String getImplementationName() {
        return "FastDecoder-MediaCodec-Texture";
    }
}
