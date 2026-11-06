package com.meowdoku.autosolver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SolverAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var controlPanel: LinearLayout? = null
    private var boardOverlay: BoardOverlayView? = null
    private var stealthButton: View? = null

    private var statusText: TextView? = null
    private var apiKeyInput: EditText? = null
    private var sizeBtn: Button? = null
    private var frameModeBtn: Button? = null
    private var modeBtn: Button? = null
    private var tapCountBtn: Button? = null

    private var manualGridSize: Int = 0
    private var currentDisplayGridSize: Int = 9
    private var autoFrameEnabled: Boolean = true
    private var isAutoTapMode: Boolean = false
    private var isDoubleTap: Boolean = true
    private var isStealthMode: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlays()
    }

    private fun setupOverlays() {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val density = metrics.density

        boardOverlay = BoardOverlayView(this).apply {
            val side = (screenW * 0.92f).toInt()
            val left = (screenW - side) / 2f
            val top = (screenH * 0.34f)
            boardRect.set(left, top, left + side, top + side)
        }

        val boardParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        windowManager?.addView(boardOverlay, boardParams)

        val panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 30
        }

        controlPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(245, 18, 18, 26))
            setPadding(12, 10, 12, 10)
            isFocusable = true
            isFocusableInTouchMode = true

            statusText = TextView(context).apply {
                text = "Вставьте DeepSeek ключ и нажмите 💾 или 📋"
                setTextColor(Color.WHITE)
                textSize = 11f
                setPadding(8, 2, 8, 4)
            }

            val keyRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 2, 0, 4)
            }

            apiKeyInput = EditText(context).apply {
                hint = "Вставьте DeepSeek API Ключ"
                textSize = 11f
                setSingleLine(true)
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.WHITE)
                setPadding(10, 6, 10, 6)
                isFocusable = true
                isFocusableInTouchMode = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val pasteKeyBtn = Button(context).apply {
                text = "📋 Вставить"
                setOnClickListener {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val pasteData = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                        if (!pasteData.isNullOrEmpty()) {
                            apiKeyInput?.setText(pasteData)
                            EmbeddedAiBrain.apiKey = pasteData
                            statusText?.text = "✅ Ключ вставлен и сохранен!"
                            Toast.makeText(context, "Ключ вставлен из буфера", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Ошибка вставки", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val saveKeyBtn = Button(context).apply {
                text = "💾"
                setOnClickListener {
                    val key = apiKeyInput?.text.toString().trim()
                    if (key.isNotEmpty()) {
                        EmbeddedAiBrain.apiKey = key
                        statusText?.text = "✅ Ключ сохранен!"
                        Toast.makeText(context, "API ключ сохранен", Toast.LENGTH_SHORT).show()
                    } else {
                        statusText?.text = "⚠️ Введите ключ!"
                    }
                }
            }

            keyRow.addView(apiKeyInput)
            keyRow.addView(pasteKeyBtn)
            keyRow.addView(saveKeyBtn)

            val hScroll = HorizontalScrollView(context).apply {
                isFillViewport = true
                isHorizontalScrollBarEnabled = false
            }

            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            sizeBtn = Button(context).apply {
                text = "Сетка: АВТО"
                setOnClickListener {
                    manualGridSize = when (manualGridSize) {
                        0 -> 5
                        15 -> 0
                        else -> manualGridSize + 1
                    }
                    if (manualGridSize == 0) {
                        text = "Сетка: АВТО"
                    } else {
                        currentDisplayGridSize = manualGridSize
                        text = "Сетка: ${manualGridSize}x${manualGridSize}"
                    }
                    boardOverlay?.solvedCats = emptyList()
                    boardOverlay?.invalidate()
                }
            }

            frameModeBtn = Button(context).apply {
                text = "Поле: АВТО"
                setOnClickListener {
                    autoFrameEnabled = !autoFrameEnabled
                    text = if (autoFrameEnabled) "Поле: АВТО" else "Поле: РУЧН"
                }
            }

            modeBtn = Button(context).apply {
                text = "👁 Показ"
                setOnClickListener {
                    isAutoTapMode = !isAutoTapMode
                    text = if (isAutoTapMode) "⚡ Авто-тап" else "👁 Показ"
                }
            }

            tapCountBtn = Button(context).apply {
                text = "2 Тапа"
                setOnClickListener {
                    isDoubleTap = !isDoubleTap
                    text = if (isDoubleTap) "2 Тапа" else "1 Тап"
                }
            }

            topRow.addView(sizeBtn)
            topRow.addView(frameModeBtn)
            topRow.addView(modeBtn)
            topRow.addView(tapCountBtn)
            hScroll.addView(topRow)

            val bottomRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 0)
            }

            val botBtn = Button(context).apply {
                text = "🤖 БОТ"
                setBackgroundColor(Color.rgb(22, 163, 74))
                setTextColor(Color.WHITE)
                setOnClickListener { captureAndSolveBot() }
            }

            val aiBtn = Button(context).apply {
                text = "🧠 ИИ"
                setBackgroundColor(Color.rgb(37, 99, 235))
                setTextColor(Color.WHITE)
                setOnClickListener { captureAndRunEmbeddedAi() }
            }

            val stealthBtn = Button(context).apply {
                text = "👻"
                setBackgroundColor(Color.rgb(55, 65, 81))
                setTextColor(Color.WHITE)
                setOnClickListener { enterStealthMode() }
            }

            val closeAppBtn = Button(context).apply {
                text = "✕"
                setBackgroundColor(Color.rgb(220, 38, 38))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    onDestroy()
                    disableSelf()
                }
            }

            bottomRow.addView(botBtn)
            bottomRow.addView(aiBtn)
            bottomRow.addView(stealthBtn)
            bottomRow.addView(closeAppBtn)

            addView(statusText)
            addView(keyRow)
            addView(hScroll)
            addView(bottomRow)
        }

        windowManager?.addView(controlPanel, panelParams)

        val stealthSize = (110 * density).toInt()
        val stealthParams = WindowManager.LayoutParams(
            stealthSize,
            stealthSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 0
            y = 0
        }

        stealthButton = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            isLongClickable = true
            setOnClickListener { captureAndSolveBot() }
            setOnLongClickListener {
                exitStealthMode()
                true
            }
        }

        windowManager?.addView(stealthButton, stealthParams)
    }

    private fun captureAndRunEmbeddedAi() {
        if (EmbeddedAiBrain.apiKey.isBlank()) {
            statusText?.text = "⚠️ Введите DeepSeek ключ!"
            Toast.makeText(this, "Сначала введите API ключ", Toast.LENGTH_SHORT).show()
            return
        }

        val overlay = boardOverlay ?: return
        statusText?.text = "🧠 DeepSeek анализирует экран..."

        overlay.visibility = View.INVISIBLE
        controlPanel?.visibility = View.INVISIBLE

        mainHandler.postDelayed({
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        overlay.visibility = View.VISIBLE
                        controlPanel?.visibility = View.VISIBLE

                        val hwBuffer = screenshotResult.hardwareBuffer
                        val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, screenshotResult.colorSpace)
                        val softBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hwBuffer.close()
                        hwBitmap?.recycle()

                        if (softBitmap == null) {
                            statusText?.text = "Ошибка скриншота"
                            return
                        }

                        serviceScope.launch {
                            val screenW = resources.displayMetrics.widthPixels.toFloat()
                            val screenH = resources.displayMetrics.heightPixels.toFloat()

                            val actions = withContext(Dispatchers.IO) {
                                EmbeddedAiBrain.analyzeAndGetAction(softBitmap)
                            }
                            softBitmap.recycle()

                            if (actions.isEmpty()) {
                                statusText?.text = "ИИ не нашел действий"
                            } else {
                                statusText?.text = "🧠 DeepSeek выполнил ход!"
                                var delay = 100L
                                for (act in actions) {
                                    val px = act.x * screenW
                                    val py = act.y * screenH
                                    mainHandler.postDelayed({ dispatchSingleTap(px, py) }, delay)
                                    delay += act.delayMs + 50L
                                }
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        overlay.visibility = View.VISIBLE
                        controlPanel?.visibility = View.VISIBLE
                        statusText?.text = "Ошибка скриншота ($errorCode)"
                    }
                }
            )
        }, 120L)
    }

    private fun enterStealthMode() {
        isStealthMode = true
        controlPanel?.visibility = View.GONE
        boardOverlay?.solvedCats = emptyList()
        boardOverlay?.invalidate()
        boardOverlay?.visibility = View.GONE
        stealthButton?.visibility = View.VISIBLE
        Toast.makeText(this, "Скрытый режим: тап в угол = бот, удержание = меню", Toast.LENGTH_SHORT).show()
    }

    private fun exitStealthMode() {
        isStealthMode = false
        stealthButton?.visibility = View.GONE
        controlPanel?.visibility = View.VISIBLE
        boardOverlay?.visibility = View.VISIBLE
        boardOverlay?.invalidate()
    }

    private fun captureAndSolveBot() {
        val overlay = boardOverlay ?: return
        if (!isStealthMode) {
            statusText?.text = "Бот сканирует поле..."
            overlay.visibility = View.INVISIBLE
            controlPanel?.visibility = View.INVISIBLE
        }

        mainHandler.postDelayed({
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        if (!isStealthMode) {
                            overlay.visibility = View.VISIBLE
                            controlPanel?.visibility = View.VISIBLE
                        }

                        val hwBuffer = screenshotResult.hardwareBuffer
                        val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, screenshotResult.colorSpace)
                        val softBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hwBuffer.close()
                        hwBitmap?.recycle()

                        if (softBitmap == null) return
                        processScreenshot(softBitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        if (!isStealthMode) {
                            overlay.visibility = View.VISIBLE
                            controlPanel?.visibility = View.VISIBLE
                        }
                    }
                }
            )
        }, if (isStealthMode) 40L else 120L)
    }

    private fun processScreenshot(bitmap: Bitmap) {
        val overlay = boardOverlay ?: return
        val viewW = if (overlay.width > 0) overlay.width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
        val viewH = if (overlay.height > 0) overlay.height.toFloat() else resources.displayMetrics.heightPixels.toFloat()

        val scaleX = bitmap.width.toFloat() / viewW
        val scaleY = bitmap.height.toFloat() / viewH

        val bitmapFallbackRect = RectF(
            overlay.boardRect.left * scaleX,
            overlay.boardRect.top * scaleY,
            overlay.boardRect.right * scaleX,
            overlay.boardRect.bottom * scaleY
        )

        val forcedN = if (manualGridSize in 5..15) manualGridSize else null
        val analysis = VisionAnalyzer.analyzeAndSolve(bitmap, bitmapFallbackRect, forcedN, autoFrameEnabled)
        bitmap.recycle()

        overlay.boardRect.set(
            analysis.boardRect.left / scaleX,
            analysis.boardRect.top / scaleY,
            analysis.boardRect.right / scaleX,
            analysis.boardRect.bottom / scaleY
        )
        currentDisplayGridSize = analysis.gridSize

        val solution = analysis.solution ?: return
        if (!isStealthMode) {
            overlay.solvedCats = solution
            overlay.invalidate()
            statusText?.text = "Бот решил ${analysis.gridSize}x${analysis.gridSize}"
        }

        if (isStealthMode || isAutoTapMode) {
            val catsToTap = solution.filter { !analysis.existingCats.contains(it) }
            performAutoTaps(catsToTap, analysis.boardRect, analysis.gridSize)
        }
    }

    private fun performAutoTaps(cats: List<Pair<Int, Int>>, displayRect: RectF, n: Int) {
        val cellW = displayRect.width() / n.toFloat()
        val cellH = displayRect.height() / n.toFloat()
        var delayMs = if (isStealthMode) 60L else 140L
        for ((r, c) in cats) {
            val tapX = displayRect.left + (c + 0.5f) * cellW
            val tapY = displayRect.top + (r + 0.5f) * cellH

            mainHandler.postDelayed({ dispatchSingleTap(tapX, tapY) }, delayMs)
            delayMs += 75L
            if (isDoubleTap) {
                mainHandler.postDelayed({ dispatchSingleTap(tapX, tapY) }, delayMs)
            }
        }
    }

    private fun dispatchSingleTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val strokeDesc = GestureDescription.StrokeDescription(path, 0L, 40L)
        val gesture = GestureDescription.Builder().addStroke(strokeDesc).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            boardOverlay?.let { windowManager?.removeView(it) }
            controlPanel?.let { windowManager?.removeView(it) }
            stealthButton?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
    }

    inner class BoardOverlayView(context: Context) : View(context) {
        val boardRect = RectF()
        var solvedCats: List<Pair<Int, Int>> = emptyList()

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 34, 197, 94)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 34, 197, 94)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        private val catTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 44f
        }
        private val markerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 22, 163, 74)
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (isStealthMode) return
            canvas.drawRect(boardRect, borderPaint)
            val n = currentDisplayGridSize.coerceIn(5, 15)
            val cellW = boardRect.width() / n.toFloat()
            val cellH = boardRect.height() / n.toFloat()

            for (i in 1 until n) {
                val x = boardRect.left + i * cellW
                val y = boardRect.top + i * cellH
                canvas.drawLine(x, boardRect.top, x, boardRect.bottom, gridPaint)
                canvas.drawLine(boardRect.left, y, boardRect.right, y, gridPaint)
            }

            catTextPaint.textSize = (cellW * 0.52f).coerceIn(16f, 60f)
            for ((r, c) in solvedCats) {
                val cx = boardRect.left + (c + 0.5f) * cellW
                val cy = boardRect.top + (r + 0.5f) * cellH
                canvas.drawCircle(cx, cy, cellW * 0.30f, markerBgPaint)
                canvas.drawText("🐱", cx, cy + catTextPaint.textSize * 0.35f, catTextPaint)
            }
        }
    }
}
