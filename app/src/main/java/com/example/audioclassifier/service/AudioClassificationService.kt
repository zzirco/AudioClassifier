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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AudioClassificationService : Service() {
    companion object {
        private const val TAG = "AudioService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AUDIO_CHANNEL"
        private const val BUFFER_SIZE = 1024 * 128  // 여기에 추가
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private lateinit var classifier: AudioClassifier

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate started")
        try {
            createNotificationChannel()
            startForeground()  // Foreground 서비스 시작
            initializeClassifier()
            Log.d(TAG, "Service onCreate completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            stopSelf()  // 초기화 실패시 서비스 종료
        }
    }

    private fun initializeClassifier() {
        try {
            classifier = AudioClassifier(this)
            Log.d(TAG, "AudioClassifier initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioClassifier", e)
            throw e  // 상위로 예외 전파
        }
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Classification Service")
            .setContentText("Listening for sounds...")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand called")
        if (checkPermissions()) {
            Log.d(TAG, "Permissions checked, starting audio recording")
            startAudioRecording()
        } else {
            Log.e(TAG, "Required permissions are not granted")
            stopSelf()
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
                BUFFER_SIZE
            ).apply {
                if (state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize, current state: $state")
                    return
                }
                Log.d(TAG, "AudioRecord successfully initialized")
            }

            val audioBuffer = FloatArray(BUFFER_SIZE)
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
                    CHANNEL_ID,
                    "Audio Classifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Audio classification notifications"
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating notification channel: ${e.message}", e)
                throw e
            }
        }
    }

    private fun showNotification(classification: String) {
        Log.d(TAG, "Attempting to show notification for: $classification")
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sound Detected")
                .setContentText("Detected sound: $classification")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            NotificationManagerCompat.from(this).apply {
                if (ContextCompat.checkSelfPermission(
                        this@AudioClassificationService,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notify(NOTIFICATION_ID + 1, notification)  // 기존 notification과 다른 ID 사용
                    Log.d(TAG, "Notification shown successfully")
                } else {
                    Log.w(TAG, "Notification permission not granted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy called")
        try {
            isRecording = false  // 먼저 녹음 플래그를 false로 설정

            // 코루틴이 완전히 종료될 때까지 기다림
            runBlocking {
                delay(100)  // 코루틴이 안전하게 종료되도록 잠시 대기
            }

            // 그 다음 AudioRecord 해제
            audioRecord?.apply {
                stop()
                Log.d(TAG, "Audio recording stopped")
                release()
                Log.d(TAG, "Audio recorder released")
            }

            // 마지막으로 classifier 해제
            classifier.close()
            Log.d(TAG, "Classifier closed")

            super.onDestroy()
            Log.d(TAG, "Service destroyed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}", e)
        }
    }
}