package com.example.quassy_ai

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val inputBox = findViewById<EditText>(R.id.inputBox)
        val sendButton = findViewById<Button>(R.id.sendButton)
        val outputText = findViewById<TextView>(R.id.outputText)

        sendButton.setOnClickListener {
            outputText.text = "Loading..."
            lifecycleScope.launch {
                val result = callLocalModel(inputBox.text.toString())
                val parts = result.split("|", limit = 2).map { it.trim() }

                if (parts.size == 2) {
                    val contact = parts[0]
                    val message = parts[1]
                    outputText.text = "Contact: $contact\nMessage: $message"

                    val service = WhatsAppAccessibilityService.instance
                    if (service != null) {
                        outputText.text = "hello123"
                        service.sendMessage(contact, message)
                    } else {
                        outputText.text = "Accessibility service is not running/enabled"
                    }
                } else {
                    outputText.text = "Couldn't parse: $result"
                }
            }
        }
    }

    private suspend fun callLocalModel(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(180, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", """
            Extract the contact name and the message from the user's command.
            Reply with ONLY this format: contact|message
            Do not add explanations, quotes, or extra text.

            Examples:
            User: tell mom that i will be late
            Reply: mom|I will be late

            User: message john i am on my way
            Reply: john|I am on my way

            User: send priya happy birthday
            Reply: priya|Happy birthday

            User: let dad know dinner is ready
            Reply: dad|Dinner is ready
        """.trimIndent())
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
                val json = JSONObject().apply {
                    put("messages", messages)
                    put("max_tokens", 60)
                    put("temperature", 0.0)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://127.0.0.1:8080/v1/chat/completions")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string() ?: "no response"
                    val jsonResponse = JSONObject(responseText)
                    jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }
}