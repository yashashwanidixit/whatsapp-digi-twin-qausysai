package com.example.quassy_ai
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.os.Handler
import android.os.Looper
class WhatsAppAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingSearchCheck: Runnable? = null
    companion object {
        var instance: WhatsAppAccessibilityService? = null
        private const val TAG = "QuassyService"
    }

    private enum class Step { IDLE, RESETTING,WAITING_FOR_WHATSAPP, WAITING_FOR_SEARCH_FIELD, WAITING_FOR_SEARCH_RESULTS, WAITING_FOR_CHAT, DONE }
    private var currentStep = Step.IDLE
    private var targetContact = ""
    private var targetMessage = ""

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate() called")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "onServiceConnected() called — service is now ACTIVE")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind() called — system is disconnecting the service")
        instance = null
        return super.onUnbind(intent)
    }
    private var resetAttempts = 0


    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy() called — service is being destroyed")
        instance = null
    }

    fun sendMessage(contact: String, message: String) {
        Log.d(TAG, "sendMessage() called with contact=$contact message=$message")
        targetContact = contact
        targetMessage = message
        resetAttempts = 0
        currentStep = Step.RESETTING
        val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (intent == null) {
            Log.e(TAG, "Could not get launch intent for com.whatsapp — is it installed?")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Log.d(TAG, "WhatsApp launch intent started")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d(TAG, "onAccessibilityEvent fired — package=${event?.packageName} step=$currentStep")
        if (event?.packageName != "com.whatsapp") return
        when (currentStep) {
            Step.RESETTING -> resetToMainScreen()
            Step.WAITING_FOR_WHATSAPP -> { Log.d(TAG, "-> calling openSearch()"); openSearch() }
            Step.WAITING_FOR_SEARCH_FIELD -> { Log.d(TAG, "-> calling typeContactIntoSearch()"); typeContactIntoSearch() }
            Step.WAITING_FOR_SEARCH_RESULTS -> { Log.d(TAG, "-> scheduling search result check"); scheduleSearchResultCheck() }
            Step.WAITING_FOR_CHAT -> { Log.d(TAG, "-> calling typeAndSendMessage()"); typeAndSendMessage() }
            else -> Log.d(TAG, "-> no matching step, doing nothing")
        }
    }
    //to not cause error when whatsapp is already opened and is not rendered from the start
    private fun resetToMainScreen() {
        val root = rootInActiveWindow
        if (root == null) { Log.e(TAG, "resetToMainScreen: rootInActiveWindow is NULL"); return }

        // Check if we're already on the main chat list screen
        val alreadyThere = root.findAccessibilityNodeInfosByViewId(
            "com.whatsapp:id/search_bar_inner_layout"
        ).firstOrNull() != null

        if (alreadyThere) {
            Log.d(TAG, "resetToMainScreen: already on main chat list")
            currentStep = Step.WAITING_FOR_WHATSAPP
            resetAttempts = 0
            return
        }

        resetAttempts++
        Log.d(TAG, "resetToMainScreen: not on main screen yet, pressing back (attempt $resetAttempts)")
        if (resetAttempts > 10) {
            Log.e(TAG, "resetToMainScreen: gave up after $resetAttempts attempts")
            currentStep = Step.IDLE
            resetAttempts = 0
            return
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
    }


    private fun openSearch() {
        val root = rootInActiveWindow
        if (root == null) { Log.e(TAG, "openSearch: rootInActiveWindow is NULL"); return }
        val searchIcon = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/search_bar_inner_layout").firstOrNull()
        if (searchIcon == null) { Log.e(TAG, "openSearch: search icon NOT FOUND"); return }
        searchIcon.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "openSearch: clicked search icon successfully")
        currentStep = Step.WAITING_FOR_SEARCH_FIELD
    }

    private fun typeContactIntoSearch() {
        val root = rootInActiveWindow
        if (root == null) { Log.e(TAG, "typeContactIntoSearch: rootInActiveWindow is NULL"); return }
        val searchField = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/search_input").firstOrNull()
        if (searchField == null) { Log.e(TAG, "typeContactIntoSearch: search field NOT FOUND"); return }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, targetContact)
        }
        searchField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "typeContactIntoSearch: typed '$targetContact' successfully")
        currentStep = Step.WAITING_FOR_SEARCH_RESULTS
    }
    private fun scheduleSearchResultCheck() {
        // STEP 1: cancel whatever was previously scheduled (if anything)
        pendingSearchCheck?.let { handler.removeCallbacks(it) }

        // STEP 2: create a NEW task — "run tapFirstSearchResult() when triggered"
        val checkRunnable = Runnable { tapFirstSearchResult() }

        // STEP 3: save this new task into pendingSearchCheck, replacing whatever was there before
        pendingSearchCheck = checkRunnable

        // STEP 4: tell the handler "run this task in 400ms from now"
        handler.postDelayed(checkRunnable, 400)
    }

    private fun tapFirstSearchResult() {
        val root = rootInActiveWindow
        if (root == null) { Log.e(TAG, "tapFirstSearchResult: rootInActiveWindow is NULL"); return }
        val results = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/contact_row_container")
        Log.d(TAG, "tapFirstSearchResult: found ${results.size} candidate rows")
        val match = results.firstOrNull { row ->
            val nameNode = row.findAccessibilityNodeInfosByViewId("com.whatsapp:id/conversations_row_contact_name").firstOrNull()
            val name = nameNode?.text?.toString()
            Log.d(TAG, "tapFirstSearchResult: candidate row name = $name")
            name?.equals(targetContact, ignoreCase = true) == true
        }
        if (match == null) { Log.e(TAG, "tapFirstSearchResult: NO MATCH for '$targetContact'");
            // stay in this step — next onAccessibilityEvent will reschedule another check
            return }
        match.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "tapFirstSearchResult: matched and clicked '$targetContact'")
        currentStep = Step.WAITING_FOR_CHAT
    }

    private fun typeAndSendMessage() {
        val root = rootInActiveWindow
        if (root == null) { Log.e(TAG, "typeAndSendMessage: rootInActiveWindow is NULL"); return }
        val messageField = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry").firstOrNull()
        if (messageField == null) { Log.e(TAG, "typeAndSendMessage: message field NOT FOUND"); return }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, targetMessage)
        }
        messageField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "typeAndSendMessage: typed message successfully")
        val sendButton = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send").firstOrNull()
        if (sendButton == null) { Log.e(TAG, "typeAndSendMessage: send button NOT FOUND"); return }
        sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "typeAndSendMessage: send button clicked")
        currentStep = Step.DONE
    }

    override fun onInterrupt() {
        Log.e(TAG, "onInterrupt() called — system interrupted the service")
        pendingSearchCheck?.let { handler.removeCallbacks(it) }
        currentStep = Step.IDLE
        targetContact = ""
        targetMessage = ""
    }
}
