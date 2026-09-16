package com.candlemovetracker.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.candlemovetracker.app.data.PreferencesManager
import com.candlemovetracker.app.databinding.ActivityCalibrationBinding

class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var prefsManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PreferencesManager(this)

        val currentRect = prefsManager.getCalibration()
        binding.calibrationOverlayView.setInitialRect(currentRect)

        binding.btnSaveCalibration.setOnClickListener {
            val selected = binding.calibrationOverlayView.getSelectedRect()
            if (selected.isValid) {
                prefsManager.saveCalibration(selected)
                Toast.makeText(this, "Chart region calibrated successfully!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "Please frame a valid chart region.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCancelCalibration.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }
}
