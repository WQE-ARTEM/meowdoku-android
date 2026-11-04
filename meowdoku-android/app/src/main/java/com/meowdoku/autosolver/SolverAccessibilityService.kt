package com.meowdoku.autosolver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
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
import android.widget.LinearLayout
import android.widget.TextView

class SolverAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var controlPanel: LinearLayout? = null
    private var boardOverlay: BoardOverlayView? = null
    private var statusText: TextView? = null

    private var gridSize: Int = 8
    private var doubleTapMode: Boolean = false
    private var autoTapEnabled: Boolean = true
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlays()
    }

    private fun setupOverlays() {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        // 1. Полупрозрачный прицел-сетка поверх игрового поля (не перехватывает клики по игре)
        boardOverlay = BoardOverlayView(this).apply {
            val side = (screenW * 0.88f).toInt()
            val left = (screenW - side) / 2f
            val top = (screenH - side) / 2f
            boardRect.set(left, top, left + side, top + side)
        }

        val boardParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager?.addView(boardOverlay, boardParams)

        // 2. Компактная плавающая панель управления (перетаскиваемая)
        val panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 90
        }

        controlPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(220, 24, 24, 32))
            setPadding(24, 16, 24, 16)

            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val sizeBtn = Button(context).apply {
                text = "${gridSize}x${gridSize}"
                setOnClickListener {
                    gridSize = if (gridSize >= 10) 5 else gridSize + 1
                    text = "${gridSize}x${gridSize}"
                    boardOverlay?.solvedCats = emptyList()
                    boardOverlay?.invalidate()
                }
            }

            val minusFrameBtn = Button(context).apply {
                text = "Рамка -"
                setOnClickListener { resizeFrame(-16f) }
            }

            val plusFrameBtn = Button(context).apply {
                text = "Рамка +"
                setOnClickListener { resizeFrame(16f) }
            }

            val moveDownBtn = Button(context).apply {
                text = "↕ Сдвиг"
                setOnClickListener { shiftFrameY() }
            }

            topRow.addView(sizeBtn)
            topRow.addView(minusFrameBtn)
            topRow.addView(plusFrameBtn)
            topRow.addView(moveDownBtn)

            val bottomRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val tapModeBtn = Button(context).apply {
                text = "Авто: 1x Тап"
                setOnClickListener {
                    if (autoTapEnabled && !doubleTapMode) {
                        doubleTapMode = true
                        text = "Авто: 2x Тап"
                    } else if (autoTapEnabled && doubleTapMode) {
                        autoTapEnabled = false
                        text = "Только показ"
                    } else {
                        autoTapEnabled = true
                        doubleTapMode = false
                        text = "Авто: 1x Тап"
                    }
                }
            }

            val solveBtn = Button(context).apply {
                text = "🐱 РЕШИТЬ"
                setBackgroundColor(Color.rgb(22, 163, 74))
                setTextColor(Color.WHITE)
                setOnClickListener { captureAndSolve() }
            }

            val clearBtn = Button(context).apply {
                text = "✖"
                setOnClickListener {
                    boardOverlay?.solvedCats = emptyList()
                    boardOverlay?.invalidate()
                    statusText?.text = "Наведите зеленую сетку на поле"
                }
            }

            bottomRow.addView(tapModeBtn)
            bottomRow.addView(solveBtn)
            bottomRow.addView(clearBtn)

            statusText = TextView(context).apply {
                text = "Совместите зеленую сетку с полем и нажмите 🐱 РЕШИТЬ"
                setTextColor(Color.WHITE)
                textSize = 12f
            }

            // Перетаскивание панели пальцем за строку статуса
            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f
            statusText?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = panelParams.x
                        initialY = panelParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        panelParams.x = initialX + (event.rawX - touchX).toInt()
                        panelParams.y = initialY + (event.rawY - touchY).toInt()
                        windowManager?.updateViewLayout(controlPanel, panelParams)
                        true
                    }
                    else -> false
                }
            }

            addView(statusText)
            addView(topRow)
            addView(bottomRow)
        }

        windowManager?.addView(controlPanel, panelParams)
    }

    private fun resizeFrame(delta: Float) {
        val overlay = boardOverlay ?: return
        val r = overlay.boardRect
        val newWidth = (r.width() + delta * 2).coerceIn(300f, resources.displayMetrics.widthPixels.toFloat())
        val cx = r.centerX()
        val cy = r.centerY()
        r.set(cx - newWidth / 2f, cy - newWidth / 2f, cx + newWidth / 2f, cy + newWidth / 2f)
        overlay.solvedCats = emptyList()
        overlay.invalidate()
    }

    private fun shiftFrameY() {
        val overlay = boardOverlay ?: return
        val r = overlay.boardRect
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        val step = 35f
        if (r.bottom + step < screenH - 80f) {
            r.offset(0f, step)
        } else {
            val side = r.width()
            val top = screenH * 0.22f
            r.top = top
            r.bottom = top + side
        }
        overlay.solvedCats = emptyList()
        overlay.invalidate()
    }

    private fun captureAndSolve() {
        val overlay = boardOverlay ?: return
        statusText?.text = "Захват экрана..."

        // Скрываем оверлей на 120 мс, чтобы зеленая сетка не искажала цвета клеток
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
                            statusText?.text = "Ошибка чтения буфера скриншота"
                            return
                        }

                        processScreenshot(softBitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        overlay.visibility = View.VISIBLE
                        controlPanel?.visibility = View.VISIBLE
                        statusText?.text = "Ошибка скриншота (код $errorCode). Подождите 1 сек."
                    }
                }
            )
        }, 120L)
    }

    private fun processScreenshot(bitmap: Bitmap) {
        val overlay = boardOverlay ?: return
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val screenH = resources.displayMetrics.heightPixels.toFloat()

        // Масштабируем координаты рамки на случай отличия разрешения скриншота от метрик
        val scaleX = bitmap.width.toFloat() / screenW
        val scaleY = bitmap.height.toFloat() / screenH
        val scaledRect = RectF(
            overlay.boardRect.left * scaleX,
            overlay.boardRect.top * scaleY,
            overlay.boardRect.right * scaleX,
            overlay.boardRect.bottom * scaleY
        )

        val startTime = System.nanoTime()
        val regionGrid = VisionAnalyzer.extractRegionGrid(bitmap, scaledRect, gridSize)
        val solution = MeowdokuSolver(regionGrid).solve()
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
        bitmap.recycle()

        if (solution == null) {
            statusText?.text = "Решение не найдено. Проверьте размер ${gridSize}x${gridSize} и рамку."
            overlay.solvedCats = emptyList()
            overlay.invalidate()
            return
        }

        overlay.solvedCats = solution
        overlay.invalidate()
        statusText?.text = "Решено за %.1f мс! Котов: %d".format(elapsedMs, solution.size)

        if (autoTapEnabled) {
            performAutoTaps(solution, overlay.boardRect)
        }
    }

    private fun performAutoTaps(cats: List<Pair<Int, Int>>, rect: RectF) {
        val cellW = rect.width() / gridSize.toFloat()
        val cellH = rect.height() / gridSize.toFloat()

        var delayMs = 150L
        for ((r, c) in cats) {
            val tapX = rect.left + (c + 0.5f) * cellW
            val tapY = rect.top + (r + 0.5f) * cellH

            mainHandler.postDelayed({ dispatchSingleTap(tapX, tapY) }, delayMs)
            delayMs += 95L

            if (doubleTapMode) {
                mainHandler.postDelayed({ dispatchSingleTap(tapX, tapY) }, delayMs)
                delayMs += 95L
            }
        }
    }

    private fun dispatchSingleTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 45L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        boardOverlay?.let { windowManager?.removeView(it) }
        controlPanel?.let { windowManager?.removeView(it) }
    }

    /**
     * Кастомный View для отрисовки прицельной сетки N x N и найденных котов 🐱 поверх игры.
     */
    inner class BoardOverlayView(context: Context) : View(context) {
        val boardRect = RectF()
        var solvedCats: List<Pair<Int, Int>> = emptyList()

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 34, 197, 94)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 34, 197, 94)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        private val catTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 48f
        }

        private val markerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(165, 22, 163, 74)
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(boardRect, borderPaint)

            val cellW = boardRect.width() / gridSize.toFloat()
            val cellH = boardRect.height() / gridSize.toFloat()

            for (i in 1 until gridSize) {
                val x = boardRect.left + i * cellW
                val y = boardRect.top + i * cellH
                canvas.drawLine(x, boardRect.top, x, boardRect.bottom, gridPaint)
                canvas.drawLine(boardRect.left, y, boardRect.right, y, gridPaint)
            }

            catTextPaint.textSize = (cellW * 0.55f).coerceIn(28f, 72f)
            for ((r, c) in solvedCats) {
                val cx = boardRect.left + (c + 0.5f) * cellW
                val cy = boardRect.top + (r + 0.5f) * cellH
                canvas.drawCircle(cx, cy, cellW * 0.32f, markerBgPaint)
                canvas.drawText("🐱", cx, cy + catTextPaint.textSize * 0.35f, catTextPaint)
            }
        }
    }
}
