package com.example.quassy_ai
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.accessibilityservice.AccessibilityService
import android.content.Intent
class WhatsAppAccessibilityService :AccessibilityService() {

    companion object {
        var instance: WhatsAppAccessibilityService? = null
    }
    // tracks where we are in the multi-step process
    private enum class Step { IDLE, WAITING_FOR_WHATSAPP, WAITING_FOR_SEARCH, WAITING_FOR_CHAT, DONE }
    private var currentStep = Step.IDLE
    private var targetContact = ""
    private var targetMessage = ""


    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // called once, when the service successfully starts
    }


    fun sendMessage(contact: String, message: String) {
        targetContact = contact
        targetMessage = message
        currentStep = Step.WAITING_FOR_WHATSAPP
        val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        // WhatsApp is now launching — we WAIT for onAccessibilityEvent
        // to tell us it's actually ready, we don't do anything else here
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This fires every time something happens in the UI system-wide
        // (only for apps you've configured it to watch — see config below)
        if (event?.packageName != "com.whatsapp") return
        when (currentStep) {
            Step.WAITING_FOR_WHATSAPP -> openSearchAndTypeContact()
            Step.WAITING_FOR_SEARCH -> tapFirstSearchResult()
            Step.WAITING_FOR_CHAT -> typeAndSendMessage()
            else -> {}
        }

    }
    // ---- STEP 1: WhatsApp is open, now find the search icon and type the contact ----
    private fun openSearchAndTypeContact() {
        val root = rootInActiveWindow ?: return
        val searchIcon = root.findAccessibilityNodeInfosByViewId(
            "com.whatsapp:id/menuitem_search"   // VERIFY this ID via Layout Inspector
        ).firstOrNull() ?: return

        searchIcon.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        // Note: typing into the search box is itself a second sub-step —
        // you'll likely need another small wait/check here before the
        // search input field actually exists on screen. Keep it simple
        // first: get the click working, then add the text-entry part.

        currentStep = Step.WAITING_FOR_SEARCH
    }
    private fun tapFirstSearchResult() {

    }

    private fun typeAndSendMessage() {

    }


    override fun onInterrupt() {
        // called if the system needs to stop the service abruptly
    }






}
