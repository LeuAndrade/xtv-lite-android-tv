package com.xtvlite.tv.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.xtvlite.tv.config.StreamConfig

class WebViewStreamActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var overlayBar: StreamOverlayBar
    private lateinit var statsView: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val config = (intent.getSerializableExtra("stream_config") as? StreamConfig) ?: StreamConfig()
        val root = FrameLayout(this)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            // Otimização de RAM: desativa zoom e rolagem pesada
            settings.setSupportZoom(false)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = WebViewClient()
        }
        root.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        statsView = TextView(this).apply {
            setBackgroundColor(0x99000000.toInt())
            setTextColor(0xFF22C55E.toInt())
            textSize = 12f
            setPadding(20, 10, 20, 10)
            visibility = if (config.showStats) View.VISIBLE else View.GONE
            text = "Modo WebView | ${config.resolution} | ${config.bitrateMbps} Mbps | ${config.serverRegion}"
        }
        root.addView(statsView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ))

        overlayBar = StreamOverlayBar(
            context = this,
            onToggleKeyboard = {},
            onToggleGamepad = {},
            onToggleMic = { true },
            onToggleStats = {
                val visible = statsView.visibility != View.VISIBLE
                statsView.visibility = if (visible) View.VISIBLE else View.GONE
                visible
            },
            onPasteText = { text ->
                val escaped = text.replace("'", "\\'")
                webView.evaluateJavascript(
                    "document.execCommand('insertText', false, '$escaped')",
                    null
                )
            },
            onExitStream = { finish() }
        )
        root.addView(overlayBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ))

        setContentView(root)
        webView.loadUrl("https://www.xbox.com/pt-BR/play/launch/${config.productId}")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                overlayBar.toggleVisibility()
            }
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            overlayBar.toggleVisibility()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
