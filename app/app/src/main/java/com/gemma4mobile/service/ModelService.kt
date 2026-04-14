package com.gemma4mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.gemma4mobile.MainActivity
import com.gemma4mobile.inference.GemmaInferenceEngine
import com.gemma4mobile.inference.InferenceState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Foreground Service — Lite 모델을 메모리에 상주시켜 즉시 응답 가능하게 한다.
 *
 * 사용 흐름:
 * 1. 앱 시작 시 또는 설정에서 "상시 대기" ON → startService
 * 2. 모델이 메모리에 로드된 상태 유지
 * 3. 측면 버튼 호출 시 → 이미 로드된 engine 사용 → 즉시 응답
 * 4. 설정에서 OFF → stopService
 */
@AndroidEntryPoint
class ModelService : Service() {

    @Inject lateinit var engine: GemmaInferenceEngine

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val binder = ModelBinder()

    inner class ModelBinder : Binder() {
        val service: ModelService get() = this@ModelService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelPath = intent?.getStringExtra(EXTRA_MODEL_PATH)

        startForeground(NOTIFICATION_ID, buildNotification("모델 로딩 중..."))

        if (modelPath != null && engine.state != InferenceState.READY) {
            scope.launch {
                try {
                    engine.loadModel(modelPath, applicationContext)
                    updateNotification("Gemma 4 대기 중")
                    Log.d(TAG, "Model loaded and ready in foreground service")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load model in service: ${e.message}")
                    updateNotification("모델 로드 실패")
                    stopSelf()
                }
            }
        } else if (engine.state == InferenceState.READY) {
            updateNotification("Gemma 4 대기 중")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        // 서비스 종료 시 모델 언로드하지 않음 — engine은 싱글톤이므로 앱에서 계속 사용 가능
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gemma4 AI 서비스",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "AI 모델이 백그라운드에서 대기 중입니다"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Gemma 4")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val TAG = "ModelService"
        private const val CHANNEL_ID = "gemma4_model_service"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_MODEL_PATH = "model_path"

        fun start(context: Context, modelPath: String) {
            val intent = Intent(context, ModelService::class.java).apply {
                putExtra(EXTRA_MODEL_PATH, modelPath)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ModelService::class.java))
        }
    }
}
