package com.xtvlite.tv

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.xtvlite.tv.config.DecoderMode
import com.xtvlite.tv.config.RenderEngine
import com.xtvlite.tv.config.StreamConfig
import com.xtvlite.tv.ui.NativeStreamActivity
import com.xtvlite.tv.ui.WebViewStreamActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = WebViewClient()
            addJavascriptInterface(XtvBridge(), "XtvNative")
        }
        setContentView(webView)

        // Carrega a interface PWA ultraleve hospedada ou empacotada em assets/www/index.html
        webView.loadUrl("file:///android_asset/www/index.html")
    }

    inner class XtvBridge {
        @JavascriptInterface
        fun launchGame(
            title: String,
            productId: String,
            engine: String,
            decoder: String,
            showStats: Boolean,
            resolution: String,
            bitrateMbps: Int,
            codec: String,
            serverRegion: String
        ) {
            val decoderMode = when (decoder) {
                "compatible" -> DecoderMode.COMPATIBLE
                "fast_decoder" -> DecoderMode.FAST_DECODER
                else -> DecoderMode.SURFACE_DECODER
            }
            val renderEngine = if (engine == "webview") RenderEngine.WEBVIEW else RenderEngine.NATIVE

            val config = StreamConfig(
                title = title,
                productId = productId,
                engine = renderEngine,
                decoderMode = decoderMode,
                showStats = showStats,
                resolution = resolution,
                bitrateMbps = bitrateMbps,
                codec = codec,
                serverRegion = serverRegion
            )

            val target = if (renderEngine == RenderEngine.NATIVE) {
                NativeStreamActivity::class.java
            } else {
                WebViewStreamActivity::class.java
            }

            runOnUiThread {
                val intent = Intent(this@MainActivity, target).apply {
                    putExtra("stream_config", config)
                }
                startActivity(intent)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            webView.clearCache(false)
        }
    }
}
