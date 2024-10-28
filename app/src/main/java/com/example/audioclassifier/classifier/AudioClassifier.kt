package com.example.audioclassifier.classifier

import android.content.Context
import android.util.Log
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import java.nio.FloatBuffer
import java.io.FileInputStream

class AudioClassifier(private val context: Context) {
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private val modelPath = "ast_model_with_metadata.onnx"
    private var labels: Map<Int, String> = emptyMap()
    private var isInitialized = false

    companion object {
        private const val TAG = "AudioClassifier"
    }

    init {
        Log.d(TAG, "Initializing AudioClassifier")
        initializeModel()
    }

    private fun initializeModel() {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            Log.d(TAG, "OrtEnvironment initialized: $ortEnvironment")

            // 일반 InputStream으로 모델 파일 읽기
            context.assets.open(modelPath).use { inputStream ->
                // 버퍼를 사용하여 모델 파일 읽기
                val modelBytes = inputStream.readBytes()
                Log.d(TAG, "Model file loaded, size: ${modelBytes.size}")

                // ONNX 세션 생성
                ortSession = ortEnvironment?.createSession(modelBytes)
                Log.d(TAG, "OrtSession created successfully")
            }

            // 라벨 초기화
            labels = mapOf(
                0 to "Background",
                1 to "Siren"
            )

            isInitialized = true
            Log.d(TAG, "Model initialization completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model", e)
            isInitialized = false
            throw RuntimeException("Failed to initialize audio classifier", e)
        }
    }

    fun classify(audioData: FloatArray): String {
        if (!isInitialized || ortEnvironment == null || ortSession == null) {
            Log.e(TAG, "Classifier not properly initialized")
            return "Error: Classifier not initialized"
        }

        try {
            val shape = longArrayOf(1, 1, audioData.size.toLong())

            val inputTensor = OnnxTensor.createTensor(
                ortEnvironment,
                FloatBuffer.wrap(audioData),
                shape
            )

            val inputs = mapOf("input" to inputTensor)
            val output = ortSession?.run(inputs)

            val outputArray = (output?.get(0)?.value as Array<FloatArray>)[0]
            val maxIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: 0

            return labels[maxIndex] ?: "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error during classification", e)
            return "Error: ${e.message}"
        }
    }

    fun close() {
        try {
            ortSession?.close()
            ortEnvironment?.close()
            isInitialized = false
            Log.d(TAG, "Resources closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resources", e)
        }
    }
}