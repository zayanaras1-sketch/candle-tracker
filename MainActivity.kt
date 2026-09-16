package com.candlemovetracker.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.candlemovetracker.app.data.CandleHistoryRepository
import com.candlemovetracker.app.data.PreferencesManager
import com.candlemovetracker.app.databinding.ActivityMainBinding
import com.candlemovetracker.app.model.CandleRecord
import com.candlemovetracker.app.model.TrackerState
import com.candlemovetracker.app.model.TrackerStateHolder
import com.candlemovetracker.app.service.FloatingOverlayService
import com.candlemovetracker.app.service.MediaProjectionService
import com.candlemovetracker.app.ui.HistoryAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var historyRepo: CandleHistoryRepository
    private lateinit var historyAdapter: HistoryAdapter

    // Screen capture permission launcher
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            MediaProjectionService.start(this, result.resultCode, result.data!!)
            Toast.makeText(this, "Candle analyzer active", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission was declined", Toast.LENGTH_LONG).show()
        }
    }

    // Overlay permission launcher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkOverlayPermission()) {
            if (TrackerStateHolder.state.value.isRunning) {
                FloatingOverlayService.start(this)
            }
        } else {
            binding.switchOverlay.isChecked = false
            Toast.makeText(this, "Overlay permission not granted", Toast.LENGTH_SHORT).show()
        }
    }

    // Notification permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PreferencesManager(this)
        historyRepo = CandleHistoryRepository(this)

        setupRecyclerView()
        setupListeners()
        updateCalibrationInfo()
        requestNotificationPermissionIfNeeded()
        observeState()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter()
        binding.rvCandleHistory.layoutManager = LinearLayoutManager(this)
        binding.rvCandleHistory.adapter = historyAdapter
        loadHistory()
    }

    private fun loadHistory() {
        val records = historyRepo.getHistory()
        historyAdapter.submitList(records)
        binding.tvEmptyHistory.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        binding.btnStartAnalyzer.setOnClickListener {
            handleStartAnalyzer()
        }

        binding.btnStopAnalyzer.setOnClickListener {
            MediaProjectionService.stop(this)
            FloatingOverlayService.stop(this)
            Toast.makeText(this, "Candle analyzer stopped", Toast.LENGTH_SHORT).show()
        }

        binding.btnCalibrateChart.setOnClickListener {
            val intent = Intent(this, CalibrationActivity::class.java)
            startActivity(intent)
        }

        binding.btnClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }

        binding.switchOverlay.isChecked = prefsManager.isOverlayEnabled()
        binding.switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setOverlayEnabled(isChecked)
            if (isChecked) {
                if (!checkOverlayPermission()) {
                    requestOverlayPermission()
                } else if (TrackerStateHolder.state.value.isRunning) {
                    FloatingOverlayService.start(this)
                }
            } else {
                FloatingOverlayService.stop(this)
            }
        }
    }

    private fun handleStartAnalyzer() {
        if (!checkOverlayPermission()) {
            requestOverlayPermission()
            return
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = projectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            TrackerStateHolder.state.collectLatest { state ->
                renderState(state)
            }
        }
    }

    private fun renderState(state: TrackerState) {
        binding.tvLiveUpCount.text = state.currentUpCount.toString()
        binding.tvLiveDownCount.text = state.currentDownCount.toString()
        binding.tvCurrentTimer.text = "TIME ${state.formatTimer()}"

        if (state.isRunning) {
            binding.btnStartAnalyzer.isEnabled = false
            binding.btnStopAnalyzer.isEnabled = true
            binding.tvStatusBadge.text = getString(R.string.status_running)
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.candle_up))
        } else {
            binding.btnStartAnalyzer.isEnabled = true
            binding.btnStopAnalyzer.isEnabled = false
            binding.tvStatusBadge.text = getString(R.string.status_ready)
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.brand_accent))
        }

        val last = state.lastCompletedCandle
        if (last != null) {
            binding.layoutLastResult.visibility = View.VISIBLE
            val dirDesc = when (last.dominantDirection) {
                CandleRecord.Direction.UP -> "UP DOMINANT (+${last.getDifference()})"
                CandleRecord.Direction.DOWN -> "DOWN DOMINANT (+${last.getDifference()})"
                CandleRecord.Direction.TIE -> "EQUAL BALANCE"
            }
            binding.tvLastResultText.text = "UP: ${last.upCount} | DOWN: ${last.downCount} ($dirDesc)"
            loadHistory()
        } else {
            binding.layoutLastResult.visibility = View.GONE
        }
    }

    private fun updateCalibrationInfo() {
        val calib = prefsManager.getCalibration()
        binding.tvCalibrationCoordinates.text = calib.formatSummary()
    }

    override fun onResume() {
        super.onResume()
        updateCalibrationInfo()
        loadHistory()
    }

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear History")
            .setMessage("Are you sure you want to clear all recorded 1-minute candle results?")
            .setPositiveButton("Clear") { _, _ ->
                historyRepo.clearHistory()
                loadHistory()
                Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
