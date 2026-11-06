package com.meowdoku.autosolver

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(18, 18, 24))
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "🐱 Meowdoku AutoSolver"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val instructions = TextView(this).apply {
            text = """
                Инструкция по запуску:
                
                1. Нажмите кнопку ниже и включите службу «Meowdoku Экранный Решатель» в разделе Специальные возможности (Accessibility).
                2. На экране появится зеленая сетка-прицел и плавающая панель.
                3. Откройте игру Meowdoku / Queens.
                4. Выберите размер поля (5x5 .. 10x10), совместите зеленую сетку с полем и нажмите «🐱 РЕШИТЬ».
            """.trimIndent()
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 48)
        }

        val openSettingsBtn = Button(this).apply {
            text = "Включить службу в Настройках"
            setBackgroundColor(Color.rgb(34, 197, 94))
            setTextColor(Color.BLACK)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        root.addView(title)
        root.addView(instructions)
        root.addView(openSettingsBtn)
        setContentView(root)
    }
}
