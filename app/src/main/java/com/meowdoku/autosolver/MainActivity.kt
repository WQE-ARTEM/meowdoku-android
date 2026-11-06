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

        // Заголовок
        val title = TextView(this).apply {
            text = "🐱 Meowdoku AutoSolver"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(title)

        // Инструкция
        val instructions = TextView(this).apply {
            text = "Инструкция по запуску:\n" +
                    "1. Нажмите кнопку ниже и включите службу «Meowdoku Экранный Решатель» в Специальных возможностях.\n" +
                    "2. На экране появится зеленая сетка и плавающая панель.\n" +
                    "3. Откройте игру Meowdoku / Queens или другую игру.\n" +
                    "4. В игре переключайтесь между БОТом и ИИ-Агентом."
            setTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(0, 0, 0, 24)
        }
        rootLayout.addView(instructions)

        // Кнопка включения службы
        val enableBtn = Button(this).apply {
            text = "Включить службу в Настройках"
            setBackgroundColor(Color.rgb(22, 163, 74))
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        rootLayout.addView(enableBtn)

        // Разделитель / Заголовок настроек
        val settingsTitle = TextView(this).apply {
            text = "\n⚙️ Настройки ИИ-Агента"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 24, 0, 12)
        }
        rootLayout.addView(settingsTitle)

        // Выбор провайдера (Gemini vs DeepSeek)
        val provLabel = TextView(this).apply {
            text = "Выберите провайдер ИИ:"
            setTextColor(Color.GRAY)
            textSize = 12f
            setPadding(0, 0, 0, 6)
        }
        rootLayout.addView(provLabel)

        val provRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }

        val geminiBtn = Button(this).apply {
            text = "🌐 Gemini (VPN)"
            textSize = 12f
            setBackgroundColor(Color.rgb(37, 99, 235))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 8
            }
        }

        val deepseekBtn = Button(this).apply {
            text = "⚡ DeepSeek"
            textSize = 12f
            setBackgroundColor(Color.rgb(80, 80, 90))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 8
            }
        }

        fun updateProvButtons(selected: String) {
            if (selected == "gemini") {
                geminiBtn.setBackgroundColor(Color.rgb(22, 163, 74))
                deepseekBtn.setBackgroundColor(Color.rgb(80, 80, 90))
            } else {
                geminiBtn.setBackgroundColor(Color.rgb(80, 80, 90))
                deepseekBtn.setBackgroundColor(Color.rgb(22, 163, 74))
            }
        }

        val currentProv = prefs.getString("provider", "gemini") ?: "gemini"
        updateProvButtons(currentProv)

        geminiBtn.setOnClickListener {
            prefs.edit().putString("provider", "gemini").apply()
            updateProvButtons("gemini")
            Toast.makeText(this, "Выбран Gemini (нужен VPN)", Toast.LENGTH_SHORT).show()
        }

        deepseekBtn.setOnClickListener {
            prefs.edit().putString("provider", "deepseek").apply()
            updateProvButtons("deepseek")
            Toast.makeText(this, "Выбран DeepSeek", Toast.LENGTH_SHORT).show()
        }

        provRow.addView(geminiBtn)
        provRow.addView(deepseekBtn)
        rootLayout.addView(provRow)

        // Поле ввода API ключа
        val keyLabel = TextView(this).apply {
            text = "API Ключ:"
            setTextColor(Color.GRAY)
            textSize = 12f
            setPadding(0, 0, 0, 6)
        }
        rootLayout.addView(keyLabel)

        val keyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        val apiKeyInput = EditText(this).apply {
            setText(prefs.getString("api_key", ""))
            hint = "Введите или вставьте API ключ"
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 8
            }
        }

        val pasteBtn = Button(this).apply {
            text = "📋 Вставить"
            textSize = 12f
            setBackgroundColor(Color.rgb(55, 65, 81))
            setTextColor(Color.WHITE)
            setOnClickListener {
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = clipboard.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val pasteText = clipData.getItemAt(0).text?.toString()?.trim()
                        if (!pasteText.isNullOrEmpty()) {
                            apiKeyInput.setText(pasteText)
                            prefs.edit().putString("api_key", pasteText).apply()
                            Toast.makeText(this@MainActivity, "Ключ вставлен и сохранен", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Не удалось прочитать буфер", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val saveKeyBtn = Button(this).apply {
            text = "💾 Сохранить"
            textSize = 12f
            setBackgroundColor(Color.rgb(22, 163, 74))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val keyStr = apiKeyInput.text.toString().trim()
                prefs.edit().putString("api_key", keyStr).apply()
                Toast.makeText(this@MainActivity, "API ключ сохранен!", Toast.LENGTH_SHORT).show()
            }
        }

        keyLayout.addView(apiKeyInput)
        keyLayout.addView(pasteBtn)
        keyLayout.addView(saveKeyBtn)
        rootLayout.addView(keyLayout)

        setContentView(scrollView)
    }
}
