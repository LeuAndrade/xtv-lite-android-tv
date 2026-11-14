package com.xtvlite.tv.ui

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.xtvlite.tv.config.StreamConfig
import com.xtvlite.tv.input.GamepadInputHandler
import com.xtvlite.tv.webrtc.XCloudWebRtcClient
import org.webrtc.EglBase

class NativeStreamActivity : AppCompatActivity() {

    private lateinit var config: StreamConfig
    private lateinit var eglBase: EglBase
    private var rtcClient: XCloudWebRtcClient? = null
    private lateinit var gamepadHandler: GamepadInputHandler
    private lateinit var overlayBar: StreamOverlayBar
    private lateinit var statsView: TextView
    private var micEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        config = (intent.getSerializableExtra("stream_config") as? StreamConfig) ?: StreamConfig()
        eglBase = EglBase.create()

        val root = FrameLayout(this)
        val surfaceView = SurfaceView(this)
        root.addView(surfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        statsView = TextView(this).apply {
            setBackgroundColor(0x99000000.toInt())
            setTextColor(0xFF22C55E.toInt())
            textSize = 12f
            setPadding(20, 10, 20, 10)
            visibility = if (config.showStats) View.VISIBLE else View.GONE
            text = "Modo Nativo | ${config.decoderMode.label} | ${config.resolution}"
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
            onToggleMic = {
                micEnabled = !micEnabled
                rtcClient?.setMicrophoneEnabled(micEnabled)
                micEnabled
            },
            onToggleStats = {
                val visible = statsView.visibility != View.VISIBLE
                statsView.visibility = if (visible) View.VISIBLE else View.GONE
                visible
            },
            onPasteText = {},
            onExitStream = { finish() }
        )
        root.addView(overlayBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ))

        setContentView(root)

        gamepadHandler = GamepadInputHandler { packet ->
            rtcClient?.sendGamepadPacket(packet)
        }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                rtcClient = XCloudWebRtcClient(
                    context = applicationContext,
                    config = config,
                    eglBase = eglBase,
                    directSurface = holder.surface,
                    onStatsUpdate = { line ->
                        runOnUiThread { statsView.text = line }
                    }
                ).apply {
                    initialize(null)
                    startStatsLoop()
                }
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                rtcClient?.dispose()
                rtcClient = null
            }
        })

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
        if (gamepadHandler.handleKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (gamepadHandler.handleMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        rtcClient?.dispose()
        eglBase.release()
    }
}
