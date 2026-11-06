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

    data class AiAction(val type: String, val x: Float, val y: Float, val delayMs: Long)

    suspend fun analyzeAndGetAction(bitmap: Bitmap): List<AiAction> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()

        try {
            // Сжимаем скриншот для быстрой отправки
            val scaled = Bitmap.createScaledBitmap(bitmap, 720, (bitmap.height * (720f / bitmap.width)).toInt(), true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            scaled.recycle()
            val base64Img = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            val url = URL("https://api.deepseek.com/v1/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 30000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val prompt = """
                Ты — универсальный игровой ИИ-агент. Проанализируй состояние экрана по описанию/изображению и выдай следующий лучший ход.
                Верни ответ СТРОГО в формате JSON без markdown-оберток:
                {
                  "actions": [
                    {"type": "tap", "x": 0.5, "y": 0.5, "delay_ms": 150}
                  ]
                }
                Координаты x и y указывай от 0.0 до 1.0 (процент от ширины и высоты экрана).
            """.trimIndent()

            val messageObj = JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$base64Img")
                        })
                    })
                })
            }

            val payload = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", JSONArray().put(messageObj))
                put("temperature", 0.1)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                it.write(payload.toString())
            }

            if (conn.responseCode != 200) {
                return@withContext emptyList()
            }

            val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(responseText)
            val choices = root.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            val content = message.getString("content").trim()

            val cleanJson = content.replace("```json", "").replace("```", "").trim()
            val jsonObj = JSONObject(cleanJson)
            val actionsArray = jsonObj.getJSONArray("actions")

            val list = mutableListOf<AiAction>()
            for (i in 0 until actionsArray.length()) {
                val obj = actionsArray.getJSONObject(i)
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
