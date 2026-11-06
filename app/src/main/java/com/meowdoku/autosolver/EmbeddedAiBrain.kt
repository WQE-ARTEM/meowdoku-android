package com.meowdoku.autosolver

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import org.json.JSONObject

object EmbeddedAiBrain {
    var apiKey: String = ""

    data class AiAction(val type: String, val x: Float, val y: Float, val delayMs: Long)

    suspend fun analyzeAndGetAction(bitmap: Bitmap): List<AiAction> {
        if (apiKey.isBlank()) return emptyList()

        return try {
            val model = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )

            val prompt = """
                Ты — универсальный игровой ИИ-агент. Посмотри на скриншот мобильной игры. Опредери лучший следующий шаг или решение головоломки.
                Верни ответ СТРОГО в формате JSON без markdown-оберток:
                {
                  "actions": [
                    {"type": "tap", "x": 0.5, "y": 0.5, "delay_ms": 150}
                  ]
                }
                Координаты x и y указывай в диапазоне от 0.0 до 1.0 (процент от ширины и высоты экрана).
            """.trimIndent()

            val inputContent = content {
                image(bitmap)
                text(prompt)
            }

            val response = model.generateContent(inputContent)
            val text = response.text?.trim() ?: return emptyList()

            val cleanJson = text.replace("```json", "").replace("```", "").trim()
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
