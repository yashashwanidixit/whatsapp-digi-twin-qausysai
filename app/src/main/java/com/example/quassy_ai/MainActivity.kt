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
import org.json.JSONObject

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
                outputText.text = result
            }
        }
    }

    private suspend fun callLocalModel(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("prompt", "Extract the contact and message from: $prompt")
                    put("n_predict", 150)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://127.0.0.1:8080/completion")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string() ?: "no response"
                    JSONObject(responseText).getString("content")
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }
}