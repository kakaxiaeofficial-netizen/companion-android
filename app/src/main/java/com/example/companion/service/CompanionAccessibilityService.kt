package com.example.companion.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class CompanionAccessibilityService : AccessibilityService() {

    companion object {
        var instance: CompanionAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun injectTap(x: Float, y: Float) {
        val displayMetrics = resources.displayMetrics
        val realX = x * displayMetrics.widthPixels
        val realY = y * displayMetrics.heightPixels

        val path = Path().apply { moveTo(realX, realY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun performSystemAction(action: String) {
        val globalAction = when (action) {
            "BACK" -> GLOBAL_ACTION_BACK
            "HOME" -> GLOBAL_ACTION_HOME
            "RECENTS" -> GLOBAL_ACTION_RECENTS
            "LOCK" -> GLOBAL_ACTION_LOCK_SCREEN
            else -> return
        }
        performGlobalAction(globalAction)
    }
}
