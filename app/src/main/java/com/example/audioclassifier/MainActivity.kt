package com.example.audioclassifier

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.audioclassifier.service.AudioClassificationService

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 123
    }

    private var isServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.startButton).setOnClickListener {
            Log.d(TAG, "Start button clicked")
            if (!isServiceRunning) {
                requestPermissions()
            }
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            if (isServiceRunning) {
                stopAudioService()
            }
        }
    }

    private fun requestPermissions() {
        Log.d(TAG, "Requesting permissions")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 이상
            val requiredPermissions = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            )

            // 권한 상태 로깅 추가
            requiredPermissions.forEach { permission ->
                val isGranted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Permission $permission: ${if (isGranted) "GRANTED" else "DENIED"}")
            }

            val missingPermissions = requiredPermissions.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }

            Log.d(TAG, "Missing permissions count: ${missingPermissions.size}")

            if (missingPermissions.isNotEmpty()) {
                Log.d(TAG, "Missing permissions: $missingPermissions")
                requestPermissions(missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
            } else {
                Log.d(TAG, "All permissions granted, starting service")
                startAudioService()
            }
        } else {
            // Android 12 이하
            val audioPermission = checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            Log.d(TAG, "Audio permission status: $audioPermission")

            if (audioPermission == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Audio permission granted, starting service")
                startAudioService()
            } else {
                Log.d(TAG, "Requesting audio permission")
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "All permissions granted")
                startAudioService()
            } else {
                // 거부된 권한들의 목록을 생성
                val deniedPermissions = permissions.filterIndexed { index, _ ->
                    grantResults[index] != PackageManager.PERMISSION_GRANTED
                }
                Log.e(TAG, "Some permissions were denied: $deniedPermissions")

                Toast.makeText(
                    this,
                    "이 앱은 마이크 사용 권한이 필요합니다. 설정에서 권한을 허용해주세요.",
                    Toast.LENGTH_LONG
                ).show()
                showPermissionSettingsDialog()
            }
        }
    }

    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("권한 필요")
            .setMessage("이 앱은 마이크 사용 권한이 필요합니다. 설정 화면으로 이동하시겠습니까?")
            .setPositiveButton("설정") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun startAudioService() {
        try {
            Log.d(TAG, "Starting audio service")
            Intent(this, AudioClassificationService::class.java).also { intent ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                isServiceRunning = true
                updateButtonState()
                Log.d(TAG, "Audio service started successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio service", e)
            isServiceRunning = false
            updateButtonState()
        }
    }

    private fun stopAudioService() {
        try {
            Log.d(TAG, "Stopping audio service")
            Intent(this, AudioClassificationService::class.java).also { intent ->
                stopService(intent)
                isServiceRunning = false
                updateButtonState()
                Log.d(TAG, "Audio service stopped successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop audio service", e)
        }
    }

    private fun updateButtonState() {
        findViewById<Button>(R.id.startButton).isEnabled = !isServiceRunning
        findViewById<Button>(R.id.stopButton).isEnabled = isServiceRunning
    }

    override fun onResume() {
        super.onResume()
        // 앱이 포그라운드로 돌아올 때 버튼 상태 업데이트
        updateButtonState()
    }
}