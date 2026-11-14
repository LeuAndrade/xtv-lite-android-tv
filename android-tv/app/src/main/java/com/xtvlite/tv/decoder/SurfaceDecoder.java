package com.xtvlite.tv.decoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.SystemClock;
import android.view.Surface;

import androidx.annotation.Nullable;

import org.webrtc.EncodedImage;
import org.webrtc.VideoCodecStatus;
import org.webrtc.VideoDecoder;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * SurfaceDecoder: decodifica quadros H.264/HEVC diretamente na Surface física da SurfaceView,
 * eliminando cópias intermediárias de textura OpenGL. Reduz uso de RAM e latência (dt) em TVs 1GB.
 */
public class SurfaceDecoder implements VideoDecoder {

    private final Surface outputSurface;
    @Nullable private MediaCodec codec;
    private volatile boolean running = false;
    private Thread drainThread;

    public SurfaceDecoder(Surface outputSurface) {
        this.outputSurface = outputSurface;
    }

    @Override
    public VideoCodecStatus initDecode(Settings settings, Callback callback) {
        if (!outputSurface.isValid()) {
            return VideoCodecStatus.ERR_PARAMETER;
        }
        try {
            int width = Math.max(settings.width, 1280);
            int height = Math.max(settings.height, 720);
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
            }
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height / 2);

            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(format, outputSurface, null, 0);
            codec.start();
            running = true;
            startDirectDrain();
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
            int inIdx = codec.dequeueInputBuffer(5000);
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

    private void startDirectDrain() {
        drainThread = new Thread(() -> {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (running && codec != null) {
                try {
                    int outIdx = codec.dequeueOutputBuffer(info, 5000);
                    if (outIdx >= 0) {
                        // render=true envia diretamente ao SurfaceView sem passar por textura GPU
                        codec.releaseOutputBuffer(outIdx, true);
                    }
                } catch (IllegalStateException ignored) {
                    SystemClock.sleep(3);
                }
            }
        }, "SurfaceDecoderDirect");
        drainThread.start();
    }

    @Override
    public VideoCodecStatus release() {
        running = false;
        if (drainThread != null) {
            drainThread.interrupt();
            drainThread = null;
        }
        if (codec != null) {
            try { codec.stop(); } catch (Exception ignored) {}
            try { codec.release(); } catch (Exception ignored) {}
            codec = null;
        }
        return VideoCodecStatus.OK;
    }

    @Override
    public String getImplementationName() {
        return "SurfaceDecoder-DirectMediaCodec";
    }
}
