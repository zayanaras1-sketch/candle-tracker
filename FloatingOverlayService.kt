package com.candlemovetracker.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.candlemovetracker.app.databinding.OverlayTrackerBinding
import com.candlemovetracker.app.model.TrackerState
import com.candlemovetracker.app.model.TrackerStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayBinding: OverlayTrackerBinding? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateObserverJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingView()
        observeTrackerState()
    }

    private fun createFloatingView() {
        overlayBinding = OverlayTrackerBinding.inflate(LayoutInflater.from(this))

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 160
        }

        setupDragListener()

        overlayBinding?.tvOverlayClose?.setOnClickListener {
            stopSelf()
        }

        windowManager?.addView(overlayBinding?.root, layoutParams)
    }

    private fun setupDragListener() {
        val binding = overlayBinding ?: return
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        binding.overlayDragHeader.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(binding.root, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun observeTrackerState() {
        stateObserverJob = serviceScope.launch {
            TrackerStateHolder.state.collectLatest { state ->
                updateUi(state)
            }
        }
    }

    private fun updateUi(state: TrackerState) {
        val binding = overlayBinding ?: return
        binding.tvOverlayUpCount.text = state.currentUpCount.toString()
        binding.tvOverlayDownCount.text = state.currentDownCount.toString()
        binding.tvOverlayTimer.text = state.formatTimer()

        val last = state.lastCompletedCandle
        if (last != null) {
            binding.layoutOverlayResult.visibility = View.VISIBLE
            binding.tvOverlayResultUp.text = "UP: ${last.upCount}"
            binding.tvOverlayResultDown.text = "DOWN: ${last.downCount}"
        } else {
            binding.layoutOverlayResult.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stateObserverJob?.cancel()
        overlayBinding?.root?.let {
            windowManager?.removeView(it)
        }
        overlayBinding = null
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            context.stopService(intent)
        }
    }
}
