package com.example.audioclassifier.classifier

import android.content.Context
import android.util.Log
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import java.nio.FloatBuffer

class AudioClassifier(private val context: Context) {
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private val modelPath = "ast_model_with_metadata.onnx"
    private var labels: Map<Int, String> = emptyMap()

    companion object {
        private const val TAG = "AudioClassifier"
    }

    init {
        Log.d(TAG, "Initializing AudioClassifier")
        try {
            // assets 폴더의 파일 목록 확인
            context.assets.list("")?.forEach {
                Log.d(TAG, "Found asset: $it")
            }

            // 모델 파일 존재 확인
            try {
                context.assets.open("ast_model_with_metadata.onnx")
                Log.d(TAG, "ONNX model file found")
            } catch (e: Exception) {
                Log.e(TAG, "ONNX model file not found", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in initialization", e)
        }
    }

    private fun initializeModel() {
        try {
            // ONNX Runtime 환경 초기화
            ortEnvironment = OrtEnvironment.getEnvironment()

            // 모델 파일을 assets에서 로드
            val modelBytes = context.assets.open(modelPath).readBytes()

            // ONNX 세션 생성
            ortSession = ortEnvironment?.createSession(modelBytes)

            // 라벨 맵 초기화 (메타데이터에서 추출)
            // 실제 구현에서는 라벨 정보를 적절히 초기화해야 함
        } catch (e: Exception) {
            Log.e("AudioClassifier", "Error initializing model", e)
        }
    }

    fun classify(audioData: FloatArray): String {
        try {
            // 오디오 데이터 전처리
            val inputTensor = OnnxTensor.createTensor(
                ortEnvironment,
                FloatBuffer.wrap(audioData),
                longArrayOf(1, 1, audioData.size.toLong())
            )

            // 추론 실행
            val inputs = mapOf("input" to inputTensor)
            val output = ortSession?.run(inputs)

            // 결과 처리
            val outputArray = (output?.get(0)?.value as Array<FloatArray>)[0]
            val maxIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: 0

            return labels[maxIndex] ?: "Unknown"
        } catch (e: Exception) {
            Log.e("AudioClassifier", "Error during classification", e)
            return "Error"
        }
    }

    fun close() {
        ortSession?.close()
        ortEnvironment?.close()
    }
}