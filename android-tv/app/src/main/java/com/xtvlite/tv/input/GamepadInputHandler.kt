package com.xtvlite.tv.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converte eventos do controle Xbox/Bluetooth/D-Pad no formato binário compacto
 * enviado pelo DataChannel "input" do modelo WebRTC xStreaming/xCloud.
 */
class GamepadInputHandler(
    private val onPacketReady: (ByteBuffer) -> Unit
) {
    private var buttonMask: Int = 0
    private var leftTrigger: Short = 0
    private var rightTrigger: Short = 0
    private var leftThumbX: Short = 0
    private var leftThumbY: Short = 0
    private var rightThumbX: Short = 0
    private var rightThumbY: Short = 0
    private var sequence: Int = 0

    // Buffer pré-alocado para evitar Garbage Collection em Android TV de 1GB RAM
    private val packetBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(32).order(ByteOrder.LITTLE_ENDIAN)

    fun handleKeyEvent(event: KeyEvent): Boolean {
        val pressed = event.action == KeyEvent.ACTION_DOWN
        val bit = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> 0x0010
            KeyEvent.KEYCODE_BUTTON_B -> 0x0020
            KeyEvent.KEYCODE_BUTTON_X -> 0x0040
            KeyEvent.KEYCODE_BUTTON_Y -> 0x0080
            KeyEvent.KEYCODE_DPAD_UP -> 0x0100
            KeyEvent.KEYCODE_DPAD_DOWN -> 0x0200
            KeyEvent.KEYCODE_DPAD_LEFT -> 0x0400
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0x0800
            KeyEvent.KEYCODE_BUTTON_START -> 0x0004
            KeyEvent.KEYCODE_BUTTON_SELECT -> 0x0008
            KeyEvent.KEYCODE_BUTTON_L1 -> 0x1000
            KeyEvent.KEYCODE_BUTTON_R1 -> 0x2000
            KeyEvent.KEYCODE_BUTTON_THUMBL -> 0x4000
            KeyEvent.KEYCODE_BUTTON_THUMBR -> 0x8000
            else -> return false
        }

        buttonMask = if (pressed) (buttonMask or bit) else (buttonMask and bit.inv())
        emitFrame()
        return true
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
            return false
        }
        leftThumbX = toInt16(event.getAxisValue(MotionEvent.AXIS_X))
        leftThumbY = toInt16(-event.getAxisValue(MotionEvent.AXIS_Y))
        rightThumbX = toInt16(event.getAxisValue(MotionEvent.AXIS_Z))
        rightThumbY = toInt16(-event.getAxisValue(MotionEvent.AXIS_RZ))
        leftTrigger = toUInt16(event.getAxisValue(MotionEvent.AXIS_LTRIGGER))
        rightTrigger = toUInt16(event.getAxisValue(MotionEvent.AXIS_RTRIGGER))
        emitFrame()
        return true
    }

    private fun emitFrame() {
        packetBuffer.clear()
        packetBuffer.put(0x04.toByte()) // Report type: Gamepad
        packetBuffer.putInt(++sequence)
        packetBuffer.putLong(System.currentTimeMillis())
        packetBuffer.putShort(buttonMask.toShort())
        packetBuffer.putShort(leftThumbX)
        packetBuffer.putShort(leftThumbY)
        packetBuffer.putShort(rightThumbX)
        packetBuffer.putShort(rightThumbY)
        packetBuffer.putShort(leftTrigger)
        packetBuffer.putShort(rightTrigger)
        packetBuffer.flip()
        onPacketReady(packetBuffer)
    }

    private fun toInt16(v: Float): Short =
        (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    private fun toUInt16(v: Float): Short =
        (v.coerceIn(0f, 1f) * 65535f).toInt().toShort()
}
