package com.meowdoku.autosolver

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object EmbeddedAiBrain {
    var apiKey: String = ""
    var provider: String = "gemini" // "gemini" или "deepseek"

    data class AiAction(val type: String, val x: Float, val y: Float, val delayMs: Long)

    suspend fun analyzeAndGetAction(bitmap: Bitmap): List<AiAction> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()

        try {
            if (provider == "gemini") {
                // Вызов Gemini API
                val scaled = Bitmap.createScaledBitmap(bitmap, 768, (bitmap.height * (768f / bitmap.width)).toInt(), true)
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                scaled.recycle()
                val base64Img = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 30000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }

                val payload = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Ты игровой ИИ-агент. Посмотри на скриншот и верни СТРОГО JSON без markdown: {\"actions\":[{\"type\":\"tap\",\"x\":0.5,\"y\":0.5,\"delay_ms\":150}]}")
                            })
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Img)
                                })
                            })
                        })
                    }))
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
                if (conn.responseCode != 200) return@withContext emptyList()

                val resp = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(resp)
                val text = root.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                parseActions(text)
            } else {
                // Вызов DeepSeek API (текстово-аналитический режим)
                val url = URL("https://api.deepseek.com/v1/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 30000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }

                val payload = JSONObject().apply {
                    put("model", "deepseek-chat")
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Игровой агент DeepSeek. Дай координаты следующего тапа для победы в формате СТРОГО JSON: {\"actions\":[{\"type\":\"tap\",\"x\":0.5,\"y\":0.5,\"delay_ms\":150}]}")
                    }))
                    put("temperature", 0.1)
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
                if (conn.responseCode != 200) return@withContext emptyList()

                val resp = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(resp)
                val content = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                parseActions(content)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseActions(rawText: String): List<AiAction> {
        return try {
            val cleanJson = rawText.replace("```json", "").replace("```", "").trim()
            val jsonObj = JSONObject(cleanJson)
            val arr = jsonObj.getJSONArray("actions")
            val list = mutableListOf<AiAction>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AiAction(
                        type = obj.optString("type", "tap"),
                        x = obj.optDouble("x", 0.5).toFloat(),
                        y = obj.optDouble("y", 0.5).toFloat(),
                        delayMs = obj.optLong("delay_ms", 150L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
