import os
from pathlib import Path

PROJECT_DIR = Path("BlockBlastBot")
FILES = {
    ".gitignore": "*.iml\n.gradle\n/local.properties\n/.idea\n.DS_Store\n/build\n",
    "settings.gradle.kts": """
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "BlockBlastBot"
include(":app")
""",
    "build.gradle.kts": """
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
""",
    "gradle.properties": """
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""",
    ".github/workflows/android.yml": """
name: Build APK
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '17' }
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
        with: { gradle-version: '8.7' }
      - run: gradle assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: BlockBlastBot-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
""",
    "app/build.gradle.kts": """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.blockblastbot"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.blockblastbot"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { viewBinding = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.jakewharton.timber:timber:5.0.1")
}
""",
    "app/src/main/AndroidManifest.xml": """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.MaterialComponents.DayNight.DarkActionBar">
        <activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service android:name=".capture.ScreenCaptureService" android:enabled="true" android:exported="false" android:foregroundServiceType="mediaProjection" />
        <service android:name=".input.TouchService" android:enabled="true" android:exported="true" android:label="Block Blast Bot Touch Service" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data android:name="android.accessibilityservice" android:resource="@xml/accessibility_service_config" />
        </service>
    </application>
</manifest>
""",
    "app/src/main/res/values/strings.xml": '<resources><string name="app_name">Block Blast Bot</string></resources>\n',
    "app/src/main/res/xml/accessibility_service_config.xml": """
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="false"
    android:description="@string/app_name"
    android:notificationTimeout="100" />
""",
    "app/src/main/res/layout/activity_main.xml": """
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent" android:padding="16dp">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical">
        <TextView android:id="@+id/tvStatus" android:layout_width="match_parent" android:layout_height="wrap_content" android:background="#222222" android:padding="12dp" android:text="Статус: Ожидание разрешений" android:textColor="#00FF99" android:textSize="15sp" />
        <Button android:id="@+id/btnAccessibility" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="12dp" android:text="1. Включить службу доступности" />
        <Button android:id="@+id/btnOverlay" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="8dp" android:text="2. Разрешить оверлей" />
        <Button android:id="@+id/btnStartService" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="8dp" android:backgroundTint="#2E7D32" android:text="3. Запустить захват экрана" android:textColor="#FFFFFF" />
        <TextView android:id="@+id/tvDelayLabel" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="16dp" android:text="Задержка между ходами: 650 мс" />
        <SeekBar android:id="@+id/sbDelay" android:layout_width="match_parent" android:layout_height="wrap_content" android:max="1500" android:progress="650" />
        <TextView android:id="@+id/tvOffsetLabel" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="12dp" android:text="Компенсация сдвига пальца: 200 px" />
        <SeekBar android:id="@+id/sbDragOffset" android:layout_width="match_parent" android:layout_height="wrap_content" android:max="400" android:progress="200" />
        <TextView android:id="@+id/tvBoardTopLabel" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="12dp" android:text="Верхняя граница поля: 20%" />
        <SeekBar android:id="@+id/sbBoardTop" android:layout_width="match_parent" android:layout_height="wrap_content" android:max="40" android:progress="20" />
        <RadioGroup android:id="@+id/rgStrategy" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="8dp">
            <RadioButton android:id="@+id/rbBalanced" android:layout_width="wrap_content" android:layout_height="wrap_content" android:checked="true" android:text="Максимум очков" />
            <RadioButton android:id="@+id/rbSafe" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Безопасная" />
            <RadioButton android:id="@+id/rbClear" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Очистка поля" />
        </RadioGroup>
    </LinearLayout>
</ScrollView>
""",
    "app/src/main/res/layout/overlay_controls.xml": """
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content" android:layout_height="wrap_content" android:background="#CC1E1E1E" android:orientation="horizontal" android:padding="6dp">
    <Button android:id="@+id/btnOverlayToggle" android:layout_width="wrap_content" android:layout_height="38dp" android:backgroundTint="#2E7D32" android:text="СТАРТ" android:textColor="#FFFFFF" android:textSize="12sp" />
    <Button android:id="@+id/btnOverlayStop" android:layout_width="wrap_content" android:layout_height="38dp" android:layout_marginStart="6dp" android:backgroundTint="#C62828" android:text="СТОП" android:textColor="#FFFFFF" android:textSize="12sp" />
    <TextView android:id="@+id/tvOverlayInfo" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_gravity="center_vertical" android:layout_marginStart="8dp" android:layout_marginEnd="6dp" android:text="Готов" android:textColor="#FFFFFF" android:textSize="12sp" />
</LinearLayout>
""",
    "app/src/main/java/com/example/blockblastbot/core/BotConfig.kt": """
package com.example.blockblastbot.core
import android.content.Context
import android.content.SharedPreferences
enum class BotStrategy { BALANCED, SAFE, CLEAR_FIELD }
class BotConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("block_blast_bot_prefs", Context.MODE_PRIVATE)
    var moveDelayMs: Long get() = prefs.getLong("move_delay_ms", 650L); set(v) = prefs.edit().putLong("move_delay_ms", v).apply()
    var dragOffsetY: Float get() = prefs.getFloat("drag_offset_y", 200f); set(v) = prefs.edit().putFloat("drag_offset_y", v).apply()
    var boardTopRatio: Float get() = prefs.getFloat("board_top_ratio", 0.20f); set(v) = prefs.edit().putFloat("board_top_ratio", v).apply()
    var boardSideMarginRatio: Float get() = prefs.getFloat("board_margin_ratio", 0.06f); set(v) = prefs.edit().putFloat("board_margin_ratio", v).apply()
    var trayCenterYRatio: Float get() = prefs.getFloat("tray_y_ratio", 0.78f); set(v) = prefs.edit().putFloat("tray_y_ratio", v).apply()
    var strategy: BotStrategy get() = BotStrategy.valueOf(prefs.getString("strategy", BotStrategy.BALANCED.name) ?: BotStrategy.BALANCED.name); set(v) = prefs.edit().putString("strategy", v.name).apply()
}
""",
    "app/src/main/java/com/example/blockblastbot/solver/BitboardSolver.kt": """
package com.example.blockblastbot.solver
import android.graphics.PointF
import com.example.blockblastbot.core.BotStrategy
data class Piece(val slotIndex: Int, val cells: List<Pair<Int, Int>>, val trayOriginScreen: PointF, val height: Int = (cells.maxOfOrNull { it.first } ?: 0) + 1, val width: Int = (cells.maxOfOrNull { it.second } ?: 0) + 1)
data class Move(val piece: Piece, val targetRow: Int, val targetCol: Int)
data class Solution(val moves: List<Move>, val score: Double)
object BitboardSolver {
    private val ROW_MASKS = ULongArray(8) { r -> 0xFFUL shl (r * 8) }
    private val COL_MASKS = ULongArray(8) { c -> var m = 0UL; for (r in 0..7) m = m or (1UL shl (r * 8 + c)); m }
    fun findBestSequence(initialBoard: ULong, pieces: List<Piece>, strategy: BotStrategy): Solution? {
        if (pieces.isEmpty()) return null
        var best: Solution? = null
        for (order in permute(pieces)) {
            val cand = dfs(initialBoard, order, 0, 0, emptyList(), strategy)
            if (cand != null && (best == null || cand.score > best.score)) best = cand
        }
        return best
    }
    private fun dfs(board: ULong, pieces: List<Piece>, index: Int, clearedTotal: Int, moves: List<Move>, strategy: BotStrategy): Solution? {
        if (index == pieces.size) return Solution(moves, evaluate(board, clearedTotal, strategy))
        val piece = pieces[index]
        if (piece.height > 8 || piece.width > 8) return null
        var bestBranch: Solution? = null
        for (r in 0..(8 - piece.height)) {
            for (c in 0..(8 - piece.width)) {
                var mask = 0UL
                for ((dr, dc) in piece.cells) mask = mask or (1UL shl ((r + dr) * 8 + (c + dc)))
                if ((board and mask) == 0UL) {
                    val (nextBoard, clearedNow) = placeAndClear(board, mask)
                    val branch = dfs(nextBoard, pieces, index + 1, clearedTotal + clearedNow, moves + Move(piece, r, c), strategy)
                    if (branch != null && (bestBranch == null || branch.score > bestBranch.score)) bestBranch = branch
                }
            }
        }
        return bestBranch
    }
    private fun placeAndClear(board: ULong, pieceMask: ULong): Pair<ULong, Int> {
        val placed = board or pieceMask
        var clearMask = 0UL; var lines = 0
        for (r in 0..7) if ((placed and ROW_MASKS[r]) == ROW_MASKS[r]) { clearMask = clearMask or ROW_MASKS[r]; lines++ }
        for (c in 0..7) if ((placed and COL_MASKS[c]) == COL_MASKS[c]) { clearMask = clearMask or COL_MASKS[c]; lines++ }
        return Pair(placed and clearMask.inv(), lines)
    }
    private fun evaluate(board: ULong, clearedLines: Int, strategy: BotStrategy): Double {
        val occupied = board.countOneBits()
        var transitions = 0; var isolatedHoles = 0
        for (r in 0..7) for (c in 0..7) {
            val cur = (board and (1UL shl (r * 8 + c))) != 0UL
            if (c < 7 && cur != ((board and (1UL shl (r * 8 + c + 1))) != 0UL)) transitions++
            if (r < 7 && cur != ((board and (1UL shl ((r + 1) * 8 + c))) != 0UL)) transitions++
            if (!cur) {
                val up = r == 0 || (board and (1UL shl ((r - 1) * 8 + c))) != 0UL
                val down = r == 7 || (board and (1UL shl ((r + 1) * 8 + c))) != 0UL
                val left = c == 0 || (board and (1UL shl (r * 8 + c - 1))) != 0UL
                val right = c == 7 || (board and (1UL shl (r * 8 + c + 1))) != 0UL
                if (up && down && left && right) isolatedHoles++
            }
        }
        return when (strategy) {
            BotStrategy.BALANCED -> (clearedLines * 140.0) - (occupied * 2.5) - (transitions * 3.2) - (isolatedHoles * 24.0)
            BotStrategy.SAFE -> (clearedLines * 90.0) - (occupied * 3.0) - (transitions * 5.0) - (isolatedHoles * 45.0)
            BotStrategy.CLEAR_FIELD -> (clearedLines * 180.0) - (occupied * 6.0) - (transitions * 2.0) - (isolatedHoles * 15.0)
        }
    }
    private fun permute(list: List<Piece>): List<List<Piece>> {
        if (list.size <= 1) return listOf(list)
        val out = mutableListOf<List<Piece>>()
        for (i in list.indices) {
            val item = list[i]; val rest = list.take(i) + list.drop(i + 1)
            for (p in permute(rest)) out.add(listOf(item) + p)
        }
        return out
    }
}
""",
    "app/src/main/java/com/example/blockblastbot/vision/VisionRecognizer.kt": """
package com.example.blockblastbot.vision
import android.graphics.Color
import android.graphics.PointF
import android.media.Image
import com.example.blockblastbot.core.BotConfig
import com.example.blockblastbot.solver.Piece
import java.nio.ByteBuffer
data class RecognizedFrame(val board: ULong, val pieces: List<Piece>, val boardLeft: Float, val boardTop: Float, val cellSize: Float)
class VisionRecognizer(private val config: BotConfig) {
    fun analyzeImage(image: Image): RecognizedFrame? {
        val plane = image.planes[0]; val buf = plane.buffer
        val w = image.width; val h = image.height
        val pStride = plane.pixelStride; val rStride = plane.rowStride
        val bLeft = w * config.boardSideMarginRatio
        val bRight = w * (1f - config.boardSideMarginRatio)
        val cellSize = (bRight - bLeft) / 8f
        val bTop = h * config.boardTopRatio
        var boardMask = 0UL
        for (r in 0..7) for (c in 0..7) {
            val cx = (bLeft + (c + 0.5f) * cellSize).toInt()
            val cy = (bTop + (r + 0.5f) * cellSize).toInt()
            if (isBlock(buf, cx, cy, w, h, rStride, pStride)) boardMask = boardMask or (1UL shl (r * 8 + c))
        }
        val trayCell = cellSize * 0.52f
        val trayY = h * config.trayCenterYRatio
        val pieces = mutableListOf<Piece>()
        for ((idx, sx) in listOf(w * 0.21f, w * 0.50f, w * 0.79f).withIndex()) {
            val raw = mutableListOf<Pair<Int, Int>>()
            for (dr in -2..2) for (dc in -2..2) {
                val px = (sx + dc * trayCell).toInt(); val py = (trayY + dr * trayCell).toInt()
                if (isBlock(buf, px, py, w, h, rStride, pStride)) raw.add(Pair(dr, dc))
            }
            if (raw.isNotEmpty()) {
                val minR = raw.minOf { it.first }; val minC = raw.minOf { it.second }
                val maxR = raw.maxOf { it.first }; val maxC = raw.maxOf { it.second }
                pieces.add(Piece(idx, raw.map { Pair(it.first - minR, it.second - minC) }, PointF(sx + ((minC + maxC)/2f)*trayCell, trayY + ((minR + maxR)/2f)*trayCell)))
            }
        }
        return RecognizedFrame(boardMask, pieces, bLeft, bTop, cellSize)
    }
    private fun isBlock(buf: ByteBuffer, x: Int, y: Int, w: Int, h: Int, rStride: Int, pStride: Int): Boolean {
        val cx = x.coerceIn(1, w - 2); val cy = y.coerceIn(1, h - 2)
        val off = cy * rStride + cx * pStride
        if (off + 2 >= buf.limit()) return false
        val red = buf.get(off).toInt() and 0xFF
        val green = buf.get(off + 1).toInt() and 0xFF
        val blue = buf.get(off + 2).toInt() and 0xFF
        val hsv = FloatArray(3)
        Color.RGBToHSV(red, green, blue, hsv)
        return hsv[2] > 0.46f && hsv[1] > 0.32f
    }
}
""",
    "app/src/main/java/com/example/blockblastbot/input/TouchService.kt": """
package com.example.blockblastbot.input
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.view.accessibility.AccessibilityEvent
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
class TouchService : AccessibilityService() {
    companion object { var instance: TouchService? = null; private set }
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }
    suspend fun performPieceDrag(start: PointF, target: PointF, offsetY: Float): Boolean = suspendCancellableCoroutine { cont ->
        val path = Path().apply { moveTo(start.x, start.y); quadTo((start.x + target.x)/2f, (start.y + target.y)/2f, target.x, target.y + offsetY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 320L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(g: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
    }
}
""",
    "app/src/main/java/com/example/blockblastbot/core/BotEngine.kt": """
package com.example.blockblastbot.core
import android.graphics.PointF
import android.media.ImageReader
import com.example.blockblastbot.input.TouchService
import com.example.blockblastbot.solver.BitboardSolver
import com.example.blockblastbot.vision.VisionRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random
class BotEngine(private val config: BotConfig, private val readerProvider: () -> ImageReader?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recognizer = VisionRecognizer(config)
    private var job: Job? = null
    private val _status = MutableStateFlow("Остановлен"); val statusFlow: StateFlow<String> = _status
    val isRunning: Boolean get() = job?.isActive == true
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try { runLoop() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _status.value = "Ошибка: " + (e.message ?: e.javaClass.simpleName) }
        }
    }
    fun pause() { job?.cancel(); job = null; _status.value = "Пауза" }
    fun stop() { job?.cancel(); job = null; _status.value = "Стоп" }
    fun destroy() { stop(); scope.cancel() }
    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            val ts = TouchService.instance
            if (ts == null) { _status.value = "Включите TouchService!"; delay(1000L); continue }
            val reader = readerProvider()
            if (reader == null) { delay(200L); continue }
            val img = reader.acquireLatestImage()
            if (img == null) { delay(200L); continue }
            val rec = try { recognizer.analyzeImage(img) } finally { img.close() }
            if (rec == null || rec.pieces.isEmpty()) { delay(300L); continue }
            _status.value = "Ход..."
            val sol = BitboardSolver.findBestSequence(rec.board, rec.pieces, config.strategy)
            if (sol == null || sol.moves.isEmpty()) { _status.value = "Нет решений"; pause(); break }
            val m = sol.moves.first()
            val target = PointF(
                rec.boardLeft + (m.targetCol + m.piece.width / 2f) * rec.cellSize,
                rec.boardTop + (m.targetRow + m.piece.height / 2f) * rec.cellSize
            )
            ts.performPieceDrag(m.piece.trayOriginScreen, target, config.dragOffsetY)
            delay(config.moveDelayMs + Random.nextLong(-100L, 100L))
        }
    }
}
""",
    "app/src/main/java/com/example/blockblastbot/capture/ScreenCaptureService.kt": """
package com.example.blockblastbot.capture
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.example.blockblastbot.R
import com.example.blockblastbot.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "START"
        private const val CHANNEL_ID = "bb_channel"
        private const val NOTIF_ID = 1001
    }
    private var mp: MediaProjection? = null
    private var reader: ImageReader? = null
    private var overlay: View? = null
    private var wm: WindowManager? = null
    private lateinit var engine: BotEngine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate() {
        super.onCreate()
        engine = BotEngine(BotConfig(this)) { reader }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) { stopSelf(); return START_NOT_STICKY }
        val code = intent.getIntExtra("code", Activity.RESULT_CANCELED)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra("data", Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra("data") as? Intent
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Block Blast Bot", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Block Blast Bot")
            .setContentText("Бот работает")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        mp = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code, data)
        val m = resources.displayMetrics
        reader = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 2)
        mp?.createVirtualDisplay("V", m.widthPixels, m.heightPixels, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, null)
        if (Settings.canDrawOverlays(this)) showOverlay()
        return START_NOT_STICKY
    }
    private fun showOverlay() {
        if (overlay != null) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (12 * density).toInt()
            y = (60 * density).toInt()
        }
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_controls, null)
        val btnToggle = view.findViewById<Button>(R.id.btnOverlayToggle)
        val btnStop = view.findViewById<Button>(R.id.btnOverlayStop)
        val tvInfo = view.findViewById<TextView>(R.id.tvOverlayInfo)
        btnToggle.setOnClickListener {
            if (engine.isRunning) { engine.pause(); btnToggle.text = "СТАРТ" }
            else { engine.start(); btnToggle.text = "ПАУЗА" }
        }
        btnStop.setOnClickListener { engine.stop(); stopSelf() }
        scope.launch {
            engine.statusFlow.collectLatest { tvInfo.text = it }
        }
        overlay = view
        wm?.addView(view, lp)
    }
    override fun onDestroy() {
        engine.destroy()
        scope.cancel()
        overlay?.let { runCatching { wm?.removeView(it) } }
        overlay = null
        reader?.close(); reader = null
        mp?.stop(); mp = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
""",
    "app/src/main/java/com/example/blockblastbot/MainActivity.kt": """
package com.example.blockblastbot
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.blockblastbot.capture.ScreenCaptureService
import com.example.blockblastbot.databinding.ActivityMainBinding
import com.example.blockblastbot.input.TouchService
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val notifPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val projLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val i = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra("code", res.resultCode)
                putExtra("data", res.data)
            }
            startForegroundService(i)
            Toast.makeText(this, "Сервис запущен!", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            } else {
                Toast.makeText(this, "Разрешение уже выдано", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnStartService.setOnClickListener {
            if (TouchService.instance == null) {
                Toast.makeText(this, "Включите TouchService (Шаг 1)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projLauncher.launch(mpm.createScreenCaptureIntent())
        }
        binding.sbDelay.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, u: Boolean) {
                binding.tvDelayLabel.text = "Задержка между ходами: " + p + " мс"
                com.example.blockblastbot.core.BotConfig(this@MainActivity).moveDelayMs = p.toLong()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.sbDragOffset.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, u: Boolean) {
                binding.tvOffsetLabel.text = "Компенсация сдвига пальца: " + p + " px"
                com.example.blockblastbot.core.BotConfig(this@MainActivity).dragOffsetY = p.toFloat()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.sbBoardTop.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, u: Boolean) {
                binding.tvBoardTopLabel.text = "Верхняя граница поля: " + p + "%"
                com.example.blockblastbot.core.BotConfig(this@MainActivity).boardTopRatio = p / 100f
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.rgStrategy.setOnCheckedChangeListener { _, checkedId ->
            val cfg = com.example.blockblastbot.core.BotConfig(this)
            cfg.strategy = when (checkedId) {
                R.id.rbSafe -> com.example.blockblastbot.core.BotStrategy.SAFE
                R.id.rbClear -> com.example.blockblastbot.core.BotStrategy.CLEAR_FIELD
                else -> com.example.blockblastbot.core.BotStrategy.BALANCED
            }
        }
    }
}
"""
}

for path, content in FILES.items():
    p = PROJECT_DIR / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content.strip() + "\n", encoding="utf-8")

print("Структура успешно создана: " + str(PROJECT_DIR))
