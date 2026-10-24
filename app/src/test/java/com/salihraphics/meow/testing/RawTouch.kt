package com.salihraphics.meow.testing

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View

/**
 * Sends real Android touch events to [view], so a test can use a stylus (and a palm next to it):
 * Compose's test API only injects fingers. Times are whatever the test says, in milliseconds.
 */
class RawTouch(private val view: View) {
    private class Pointer(val id: Int, var x: Float, var y: Float, val stylus: Boolean, var pressure: Float)

    private val down = ArrayList<Pointer>()
    private var downTime = 0L

    fun down(id: Int, x: Float, y: Float, time: Long, stylus: Boolean = false, pressure: Float = 1f) {
        val p = Pointer(id, x, y, stylus, pressure)
        down += p
        if (down.size == 1) {
            downTime = time
            send(MotionEvent.ACTION_DOWN, time)
        } else {
            send(MotionEvent.ACTION_POINTER_DOWN or (down.indexOf(p) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), time)
        }
    }

    fun move(id: Int, x: Float, y: Float, time: Long, pressure: Float? = null) {
        down.first { it.id == id }.apply { this.x = x; this.y = y; pressure?.let { this.pressure = it } }
        send(MotionEvent.ACTION_MOVE, time)
    }

    fun up(id: Int, time: Long) {
        val p = down.first { it.id == id }
        if (down.size == 1) send(MotionEvent.ACTION_UP, time) else send(MotionEvent.ACTION_POINTER_UP or (down.indexOf(p) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), time)
        down.remove(p)
    }

    /** A stylus line from (x0,y0) to (x1,y1) in [steps] moves, starting at [start] ms. */
    fun stylusLine(x0: Float, y0: Float, x1: Float, y1: Float, start: Long, steps: Int = 12, id: Int = 7, pressures: ((Int) -> Float)? = null): Long {
        down(id, x0, y0, start, stylus = true, pressure = pressures?.invoke(0) ?: 1f)
        var t = start
        for (i in 1..steps) {
            t += 8
            move(id, x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps, t, pressures?.invoke(i))
        }
        up(id, t + 8)
        return t + 8
    }

    private fun send(action: Int, time: Long) {
        val props = down.map { p ->
            MotionEvent.PointerProperties().apply {
                id = p.id
                toolType = if (p.stylus) MotionEvent.TOOL_TYPE_STYLUS else MotionEvent.TOOL_TYPE_FINGER
            }
        }.toTypedArray()
        val coords = down.map { p -> MotionEvent.PointerCoords().apply { x = p.x; y = p.y; pressure = p.pressure; size = 0.1f } }.toTypedArray()
        val source = if (down.any { it.stylus }) InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_TOUCHSCREEN
        val event = MotionEvent.obtain(downTime, time, action, down.size, props, coords, 0, 0, 1f, 1f, 0, 0, source, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }
}
