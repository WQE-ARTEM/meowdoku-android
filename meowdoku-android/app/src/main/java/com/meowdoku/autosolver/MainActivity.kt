package com.meowdoku.autosolver

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val prefs = getSharedPreferences("meowdoku_prefs", Context.MODE_PRIVATE)

            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(18, 18, 26))
                setPadding(32, 48, 32, 48)
            }

            val scrollView = ScrollView(this).apply {
                isFillViewport = true
                addView(rootLayout)
            }

            val title = TextView(this).apply {
                text = "🐱 Meowdoku AutoSolver"
                setTextColor(Color.WHITE)
                textSize = 22f
                setPadding(0, 0, 0, 16)
            }
            rootLayout.addView(title)

            val instructions = TextView(this).apply {
                text = "Инструкция по запуску:\n" +
                        "1. Включите службу «Meowdoku Экранный Решатель» в Специальных возможностях.\n" +
                        "2. Откройте игру и используйте плавающую панель."
                setTextColor(Color.LTGRAY)
                textSize = 13f
                setPadding(0, 0, 0, 24)
            }
            rootLayout.addView(instructions)

            val enableBtn = Button(this).apply {
                text = "Включить службу в Настройках"
                setBackgroundColor(Color.rgb(22, 163, 74))
                setTextColor(Color.WHITE)
                setPadding(16, 16, 16, 16)
                setOnClickListener {
                    try {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (_: Exception) {}
                }
            }
            rootLayout.addView(enableBtn)

            val settingsTitle = TextView(this).apply {
                text = "\n⚙️ Настройки ИИ"
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(0, 24, 0, 12)
            }
            rootLayout.addView(settingsTitle)

            val apiKeyInput = EditText(this).apply {
                setText(prefs.getString("api_key", ""))
                hint = "Вставьте API ключ"
                textSize = 13f
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.WHITE)
                setPadding(16, 12, 16, 12)
            }
            rootLayout.addView(apiKeyInput)

            val saveKeyBtn = Button(this).apply {
                text = "💾 Сохранить ключ"
                setBackgroundColor(Color.rgb(37, 99, 235))
                setTextColor(Color.WHITE)
                setPadding(16, 12, 16, 12)
                setOnClickListener {
                    val keyStr = apiKeyInput.text.toString().trim()
                    prefs.edit().putString("api_key", keyStr).apply()
                    Toast.makeText(this@MainActivity, "Ключ сохранен!", Toast.LENGTH_SHORT).show()
                }
            }
            rootLayout.addView(saveKeyBtn)

            setContentView(scrollView)
        } catch (e: Exception) {
            val tv = TextView(this).apply {
                text = "Ошибка инициализации: ${e.message}"
                setTextColor(Color.RED)
            }
            setContentView(tv)
        }
    }
}
