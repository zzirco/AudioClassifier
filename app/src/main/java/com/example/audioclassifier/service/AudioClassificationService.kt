package com.example.audioclassifier.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.audioclassifier.R
import com.example.audioclassifier.classifier.AudioClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudioClassificationService : Service() {
    companion object {
        private const val TAG = "AudioService"
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private lateinit var classifier: AudioClassifier

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate started")
        try {
            classifier = AudioClassifier(this)
            Log.d(TAG, "AudioClassifier initialized successfully")
            createNotificationChannel()
            Log.d(TAG, "Service onCreate completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand called")
        if (checkPermissions()) {
            Log.d(TAG, "Permissions checked, starting audio recording")
            startAudioRecording()
        } else {
            Log.e(TAG, "Required permissions are not granted")
        }
        return START_STICKY
    }

    private fun checkPermissions(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Audio permission check result: $hasPermission")
        return hasPermission
    }

    private fun startAudioRecording() {
        Log.d(TAG, "Starting audio recording process")
        try {
            if (!checkPermissions()) {
                Log.e(TAG, "Audio recording permission not granted")
                return
            }

            val bufferSize = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            Log.d(TAG, "Initialized buffer size: $bufferSize bytes")

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                bufferSize
            ).apply {
                if (state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize, current state: $state")
                    return
                }
                Log.d(TAG, "AudioRecord successfully initialized")
            }

            val audioBuffer = FloatArray(bufferSize)
            isRecording = true
            audioRecord?.startRecording()
            Log.d(TAG, "Audio recording started successfully")

            CoroutineScope(Dispatchers.IO).launch {
                Log.d(TAG, "Starting audio processing coroutine")
                var processingCount = 0
                while (isRecording) {
                    try {
                        val readSize = audioRecord?.read(audioBuffer, 0, bufferSize, AudioRecord.READ_BLOCKING) ?: 0
                        processingCount++
                        if (processingCount % 100 == 0) {  // 로그 과다 방지
                            Log.d(TAG, "Audio processing count: $processingCount, Last read size: $readSize")
                        }

                        if (readSize > 0) {
                            Log.d(TAG, "Processing audio buffer of size: $readSize")
                            val result = classifier.classify(audioBuffer)
                            if (result != "Unknown") {
                                Log.i(TAG, "Sound detected! Classification result: $result")
                                showNotification(result)
                            }
                        } else {
                            Log.w(TAG, "Read size is 0 or negative: $readSize")
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception while reading audio: ${e.message}", e)
                        isRecording = false
                    } catch (e: Exception) {
                        Log.e(TAG, "Error while reading audio: ${e.message}", e)
                        isRecording = false
                    }
                }
                Log.d(TAG, "Audio processing coroutine ended")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while initializing AudioRecord: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error in startAudioRecording: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        Log.d(TAG, "Creating notification channel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    "AUDIO_CHANNEL",
                    "Audio Classifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating notification channel: ${e.message}", e)
            }
        }
    }

    private fun showNotification(classification: String) {
        Log.d(TAG, "Attempting to show notification for: $classification")
        try {
            val notification = NotificationCompat.Builder(this, "AUDIO_CHANNEL")
                .setContentTitle("Sound Detected")
                .setContentText("Detected sound: $classification")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(this).notify(1, notification)
                Log.d(TAG, "Notification shown successfully")
            } else {
                Log.w(TAG, "Notification permission not granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy called")
        try {
            isRecording = false
            audioRecord?.stop()
            Log.d(TAG, "Audio recording stopped")
            audioRecord?.release()
            Log.d(TAG, "Audio recorder released")
            classifier.close()
            Log.d(TAG, "Classifier closed")
            super.onDestroy()
            Log.d(TAG, "Service destroyed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}", e)
        }
    }
}