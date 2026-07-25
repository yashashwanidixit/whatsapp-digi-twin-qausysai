package com.example.quassy_ai
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.accessibilityservice.AccessibilityService
class WhatsAppAccessibilityService :AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This fires every time something happens in the UI system-wide
        // (only for apps you've configured it to watch — see config below)
        if (event?.packageName == "com.whatsapp") {
            // WhatsApp's screen just changed — this is where you'd
            // check "did the chat screen open, is it ready for input"
        }
    }

    override fun onInterrupt() {
        // called if the system needs to stop the service abruptly
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // called once, when the service successfully starts
    }

    fun sendMessage(contactName: String, message: String) {
        val rootNode = rootInActiveWindow ?: return

        // find the message input field — exact viewId depends on
        // WhatsApp's current UI, you'll need to inspect this (see below)
        val messageField = rootNode.findAccessibilityNodeInfosByViewId(
            "com.whatsapp:id/entry"
        ).firstOrNull() ?: return

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                message
            )
        }
        messageField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        val sendButton = rootNode.findAccessibilityNodeInfosByViewId(
            "com.whatsapp:id/send"
        ).firstOrNull() ?: return

        sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }



}
