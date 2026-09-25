package dev.kimi2api

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.kimi2api.engine.KimiEngine
import dev.kimi2api.http.GatewayServer
import dev.kimi2api.http.RequestHandler
import android.os.Handler
import android.os.Looper
import dev.kimi2api.kimi.KimiClient
import dev.kimi2api.store.AccountStore
import dev.kimi2api.store.SettingsStore
import dev.kimi2api.util.Logger
import dev.kimi2api.util.NetUtil

class GatewayService : Service() {

    private lateinit var settings: SettingsStore
    private lateinit var accountStore: AccountStore
    private lateinit var kimi: KimiClient
    private var server: GatewayServer? = null
    private var floating: FloatingWindow? = null

    companion object {
        const val ACTION_START = "dev.kimi2api.START"
        const val ACTION_STOP = "dev.kimi2api.STOP"
        const val ACTION_RESTART = "dev.kimi2api.RESTART"
        private const val CHANNEL_ID = "kimi_gateway"
        private const val NOTIFY_ID = 1001

        @Volatile
        var running = false
            private set

        @Volatile
        var currentPort = 9980
            private set
    }

    private lateinit var engine: KimiEngine

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        accountStore = AccountStore.get(this)
        engine = KimiEngine.get(this)
        kimi = KimiClient(engine)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopGateway()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                stopGateway()
                startGateway()
            }
            else -> startGateway()
        }
        return START_STICKY
    }

    private val handler = Handler(Looper.getMainLooper())
    private val notifyRunnable = Runnable {
        if (running) {
            try {
                getSystemService(android.app.NotificationManager::class.java)
                    .notify(NOTIFY_ID, buildNotification(currentPort))
            } catch (_: Exception) {}
        }
    }

    private fun startGateway() {
        if (running) return
        val port = settings.port
        currentPort = port
        startForeground(NOTIFY_ID, buildNotification(port))
        try {
            val rh = RequestHandler(settings, kimi)
            server = GatewayServer(port, rh).also { it.start() }
            running = true
            currentPort = GatewayServer.runningPort
            Logger.log("服务启动，端口 ${GatewayServer.runningPort}")
            // 预热网页引擎（后台加载 kimi.com，消除首次请求冷启动）
            engine.ensureWebView {
                Logger.log("Kimi 网页引擎就绪")
            }
            syncFloating()
            // 每 30s 刷新通知，更新请求计数
            handler.removeCallbacks(notifyRunnable)
            handler.postDelayed(notifyRunnable, 30_000)
        } catch (e: Exception) {
            Logger.log("服务启动失败：" + e.message)
            running = false
        }
    }

    private fun stopGateway() {
        handler.removeCallbacks(notifyRunnable)
        server?.stop()
        server = null
        running = false
        floating?.hide()
        floating = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        Logger.log("服务已停止")
    }

    private fun syncFloating() {
        if (settings.showFloat) {
            if (floating == null) floating = FloatingWindow(this)
            floating?.show(NetUtil.getLocalIp() + ":" + currentPort)
        } else {
            floating?.hide()
            floating = null
        }
    }

    private fun buildNotification(port: Int): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), flags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kimi API 运行中")
            .setContentText(
                "地址 " + NetUtil.getLocalIp() + ":" + port +
                    "  已处理 " + GatewayServer.requestCount() + " 次请求"
            )
            .setSmallIcon(R.drawable.ic_stat_gateway)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(notifyRunnable)
        stopGateway()
        super.onDestroy()
    }

    /** 供 WebBridge 调用：立即刷新悬浮窗与通知（切换开关后生效） */
    fun refresh() {
        syncFloating()
        handler.removeCallbacks(notifyRunnable)
        handler.post(notifyRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
