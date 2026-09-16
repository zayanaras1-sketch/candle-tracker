package com.candlemovetracker.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.candlemovetracker.app.CandleMoveTrackerApp
import com.candlemovetracker.app.MainActivity
import com.candlemovetracker.app.R
import com.candlemovetracker.app.data.CandleHistoryRepository
import com.candlemovetracker.app.data.PreferencesManager
import com.candlemovetracker.app.detector.CandleAnalysisResult
import com.candlemovetracker.app.detector.VisualCandleDetector
import com.candlemovetracker.app.model.CalibrationRect
import com.candlemovetracker.app.model.CandleRecord
import com.candlemovetracker.app.model.TrackerStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class MediaProjectionService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private lateinit var detector: VisualCandleDetector
    private lateinit var prefsManager: PreferencesManager
    private lateinit var historyRepo: CandleHistoryRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var candleTimerJob: Job? = null
    private var notificationUpdateJob: Job? = null

    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = DisplayMetrics.DENSITY_DEFAULT

    private var isProcessingFrame = false
    private var lastProcessTimestamp = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        detector = VisualCandleDetector()
        prefsManager = PreferencesManager(this)
        historyRepo = CandleHistoryRepository(this)

        backgroundThread = HandlerThread("CandleFrameProcessor").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)

        queryScreenDimensions()
    }

    private fun queryScreenDimensions() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (action == ACTION_STOP) {
            stopAnalysis()
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }

        if (resultCode != -1 && data != null) {
            startForegroundServiceNotification()
            startScreenCapture(resultCode, data)
            startCandleTimer()
            TrackerStateHolder.setRunning(true)
            observeStateForNotification()

            if (prefsManager.isOverlayEnabled()) {
                FloatingOverlayService.start(this)
            }
        }

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val stopIntent = Intent(this, MediaProjectionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CandleMoveTrackerApp.CHANNEL_ID)
            .setContentTitle("Candle Move Tracker Active")
            .setContentText("Analyzing active 1-minute candle frames...")
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(mainPendingIntent)
            .addAction(R.drawable.ic_stop, "Stop Analyzer", stopPendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                CandleMoveTrackerApp.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(CandleMoveTrackerApp.NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("WrongConstant")
    private fun startScreenCapture(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        // Capture in 1/2 or 1/3 resolution to maintain high processing FPS without GC pressure
        val captureWidth = screenWidth / 2
        val captureHeight = screenHeight / 2

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "CandleCaptureDisplay",
            captureWidth,
            captureHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            handleNewFrame(reader, captureWidth, captureHeight)
        }, backgroundHandler)
    }

    private fun handleNewFrame(reader: ImageReader, captureW: Int, captureH: Int) {
        val now = System.currentTimeMillis()
        // Process every ~40-60ms (~20 FPS) for smooth visual tick capture
        if (now - lastProcessTimestamp < 40) {
            val img = reader.acquireLatestImage()
            img?.close()
            return
        }
        lastProcessTimestamp = now

        val image = reader.acquireLatestImage() ?: return
        if (isProcessingFrame) {
            image.close()
            return
        }

        isProcessingFrame = true
        try {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * captureW

            val bitmap = Bitmap.createBitmap(
                captureW + rowPadding / pixelStride,
                captureH,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            // Map user calibration region to capture dimensions
            val calib = prefsManager.getCalibration()
            val croppedBitmap = cropToRegion(bitmap, calib, captureW, captureH)
            bitmap.recycle()

            if (croppedBitmap != null) {
                val result = detector.analyzeFrame(croppedBitmap, calib)
                croppedBitmap.recycle()

                if (result.hasMovement) {
                    when (result.movementDirection) {
                        CandleAnalysisResult.MovementDirection.UP -> {
                            TrackerStateHolder.incrementUp()
                        }
                        CandleAnalysisResult.MovementDirection.DOWN -> {
                            TrackerStateHolder.incrementDown()
                        }
                        CandleAnalysisResult.MovementDirection.NONE -> {}
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isProcessingFrame = false
        }
    }

    private fun cropToRegion(fullBitmap: Bitmap, calib: CalibrationRect, capW: Int, capH: Int): Bitmap? {
        val origW = if (calib.screenWidth > 0) calib.screenWidth else screenWidth
        val origH = if (calib.screenHeight > 0) calib.screenHeight else screenHeight

        val scaleX = capW.toFloat() / origW
        val scaleY = capH.toFloat() / origH

        val cropLeft = (calib.left * scaleX).toInt().coerceIn(0, capW - 10)
        val cropTop = (calib.top * scaleY).toInt().coerceIn(0, capH - 10)
        val cropRight = (calib.right * scaleX).toInt().coerceIn(cropLeft + 10, capW)
        val cropBottom = (calib.bottom * scaleY).toInt().coerceIn(cropTop + 10, capH)

        val cropW = (cropRight - cropLeft).coerceAtLeast(10)
        val cropH = (cropBottom - cropTop).coerceAtLeast(10)

        return try {
            Bitmap.createBitmap(fullBitmap, cropLeft, cropTop, cropW, cropH)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 1-Minute Candle Timer (Wall-Clock Synchronized):
     * Trading platforms synchronize 1-minute candles to standard clock seconds (:00 to :59).
     * By calculating the exact remaining seconds until the next minute boundary, the app
     * automatically detects if a candle is already 35s in progress and synchronizes perfectly!
     */
    private fun startCandleTimer() {
        candleTimerJob?.cancel()
        candleTimerJob = serviceScope.launch {
            var lastRecordedMinute = (System.currentTimeMillis() / 60000)

            while (isActive) {
                val nowMs = System.currentTimeMillis()
                val currentMinute = nowMs / 60000
                val secOfMinute = ((nowMs / 1000) % 60).toInt()
                val remainingSeconds = if (secOfMinute == 0) 60 else (60 - secOfMinute)

                TrackerStateHolder.updateTimer(remainingSeconds)

                // Minute rolled over -> candle closed at :00 boundary
                if (currentMinute > lastRecordedMinute) {
                    lastRecordedMinute = currentMinute
                    val currentState = TrackerStateHolder.state.value
                    val finalUp = currentState.currentUpCount
                    val finalDown = currentState.currentDownCount

                    // Record completed candle if tracking was active
                    val record = CandleRecord(
                        upCount = finalUp,
                        downCount = finalDown,
                        durationSeconds = 60
                    )
                    historyRepo.addRecord(record)
                    TrackerStateHolder.completeCandle(record)
                    detector.resetForNewCandle()
                }

                // Sleep precisely until the next second
                val sleepTime = 1000L - (System.currentTimeMillis() % 1000L)
                delay(sleepTime.coerceIn(100L, 1000L))
            }
        }
    }

    private fun observeStateForNotification() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = serviceScope.launch {
            TrackerStateHolder.state.collectLatest { state ->
                updateForegroundNotification(state)
            }
        }
    }

    private fun updateForegroundNotification(state: TrackerState) {
        val stopIntent = Intent(this, MediaProjectionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CandleMoveTrackerApp.CHANNEL_ID)
            .setContentTitle("Candle Move Tracker • ${state.formatTimer()}")
            .setContentText("UP: ${state.currentUpCount} | DOWN: ${state.currentDownCount}")
            .setSmallIcon(R.drawable.ic_play)
            .addAction(R.drawable.ic_stop, "Stop Analyzer", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(CandleMoveTrackerApp.NOTIFICATION_ID, notification)
    }

    private fun stopAnalysis() {
        candleTimerJob?.cancel()
        notificationUpdateJob?.cancel()
        TrackerStateHolder.setRunning(false)
        FloatingOverlayService.stop(this)

        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null

        backgroundThread?.quitSafely()
        backgroundThread = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAnalysis()
    }

    companion object {
        const val ACTION_STOP = "com.candlemovetracker.app.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, MediaProjectionService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_PROJECTION_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaProjectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
