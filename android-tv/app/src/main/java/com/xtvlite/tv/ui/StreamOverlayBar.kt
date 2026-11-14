package com.xtvlite.tv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu

/**
 * Replica exatamente o menu superior esquerdo ativado pelo botão Voltar:
 * [Teclado] [Controle] [Microfone] [3 Pontinhos: Estatísticas / Colar / Sair] [< Recolher]
 */
class StreamOverlayBar(
    context: Context,
    private val onToggleKeyboard: () -> Unit,
    private val onToggleGamepad: () -> Unit,
    private val onToggleMic: () -> Boolean,
    private val onToggleStats: () -> Boolean,
    private val onPasteText: (String) -> Unit,
    private val onExitStream: () -> Unit
) : LinearLayout(context) {

    private var micActive = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.parseColor("#E6202224"))
        visibility = View.GONE

        val padH = (16 * resources.displayMetrics.density).toInt()
        val padV = (10 * resources.displayMetrics.density).toInt()

        val btnKeyboard = createIconButton(android.R.drawable.ic_menu_edit, "Teclado", padH, padV) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            onToggleKeyboard()
        }

        val btnGamepad = createIconButton(android.R.drawable.ic_menu_compass, "Controle", padH, padV) {
            onToggleGamepad()
        }

        val btnMic = createIconButton(android.R.drawable.ic_btn_speak_now, "Microfone", padH, padV) {
            micActive = onToggleMic()
            it.alpha = if (micActive) 1.0f else 0.45f
        }
        btnMic.alpha = 0.45f

        val btnMore = createIconButton(android.R.drawable.ic_menu_more, "Mais opções", padH, padV) { anchor ->
            val popup = PopupMenu(context, anchor)
            popup.menu.add(0, 1, 0, "Ligar / Desligar Estatísticas")
            popup.menu.add(0, 2, 1, "Colar")
            popup.menu.add(0, 3, 2, "Sair")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { onToggleStats(); true }
                    2 -> {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip: ClipData? = cb.primaryClip
                        val text = clip?.getItemAt(0)?.text?.toString() ?: ""
                        if (text.isNotEmpty()) onPasteText(text)
                        true
                    }
                    3 -> { onExitStream(); true }
                    else -> false
                }
            }
            popup.show()
        }

        val btnCollapse = createIconButton(android.R.drawable.ic_media_previous, "Recolher", padH, padV) {
            visibility = View.GONE
        }
        btnCollapse.setBackgroundColor(Color.parseColor("#FF4A4C4E"))

        addView(btnKeyboard)
        addView(btnGamepad)
        addView(btnMic)
        addView(btnMore)
        addView(btnCollapse)
    }

    private fun createIconButton(
        resId: Int,
        desc: String,
        padH: Int,
        padV: Int,
        onClick: (View) -> Unit
    ): ImageButton {
        return ImageButton(context).apply {
            setImageResource(resId)
            setColorFilter(Color.parseColor("#FFE5E7EB"))
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = desc
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(padH, padV, padH, padV)
            setOnClickListener { onClick(this) }
        }
    }

    fun toggleVisibility() {
        visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (visibility == View.VISIBLE) {
            getChildAt(0)?.requestFocus()
        }
    }
}
