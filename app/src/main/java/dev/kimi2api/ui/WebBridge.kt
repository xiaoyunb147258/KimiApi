package dev.kimi2api.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import dev.kimi2api.GatewayService
import dev.kimi2api.engine.KimiEngine
import dev.kimi2api.http.GatewayServer
import dev.kimi2api.kimi.KimiClient
import dev.kimi2api.store.AccountStore
import dev.kimi2api.store.SettingsStore
import dev.kimi2api.util.Logger
import dev.kimi2api.util.NetUtil
import org.json.JSONArray
import org.json.JSONObject

/**
 * 暴露给 HTML 前端的原生能力桥。
 * 前端通过 window.KimiNative.xxx() 调用。
 */
class WebBridge(
    private val context: Context,
    private val webView: WebView
) {
    private val settings = SettingsStore(context)
    private val accountStore = AccountStore.get(context)
    private val kimi = KimiClient(KimiEngine.get(context))

    private val logListener: (Logger.Entry) -> Unit = { entry ->
        val js = "window.onNativeLog(${JSONObject.quote(entry.time)}, " +
            "${JSONObject.quote(entry.text)})"
        onUi {
            try { webView.evaluateJavascript(js, null) } catch (_: Exception) {}
        }
    }

    init {
        Logger.addListener(logListener)
    }

    /** WebView 销毁时调用，移除日志监听，避免回调已销毁的 WebView */
    fun destroy() {
        Logger.removeListener(logListener)
    }

    private fun onUi(block: () -> Unit) {
        webView.post(block)
    }

    private fun ok(data: String = "null"): String =
        """{"ok":true,"data":$data}"""

    private fun err(msg: String): String =
        """{"ok":false,"error":${JSONObject.quote(msg)}}"""

    // ------------------------------------------------------------ 状态

    @JavascriptInterface
    fun getStatus(): String {
        return try {
            val obj = JSONObject()
                .put("running", GatewayService.running)
                .put("port", GatewayService.currentPort)
                .put("configPort", settings.port)
                .put("requestCount", GatewayServer.requestCount())
                .put("ip", NetUtil.getLocalIp())
                .put("accountCount", accountStore.size())
                .put("apiKey", settings.apiKey)
                .put("model", settings.modelName)
                .put("autoStart", settings.autoStart)
                .put("showFloat", settings.showFloat)
                .put("useSearch", settings.useSearch)
                .put("canOverlay", canOverlay())
                .put("batteryIgnored", isIgnoringBattery())
            ok(obj.toString())
        } catch (e: Exception) {
            err(e.message ?: "error")
        }
    }

    // ------------------------------------------------------------ 服务

    @JavascriptInterface
    fun startService(): String {
        startServiceAction(GatewayService.ACTION_START)
        return ok()
    }

    @JavascriptInterface
    fun stopService(): String {
        startServiceAction(GatewayService.ACTION_STOP)
        return ok()
    }

    @JavascriptInterface
    fun restartService(): String {
        startServiceAction(GatewayService.ACTION_RESTART)
        return ok()
    }

    private fun startServiceAction(action: String) {
        val intent = Intent(context, GatewayService::class.java).apply { this.action = action }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    // ------------------------------------------------------------ 账号

    @JavascriptInterface
    fun getAccounts(): String {
        return try {
            val arr = JSONArray()
            accountStore.getAll().forEach { a ->
                arr.put(
                    JSONObject()
                        .put("remark", a.remark)
                        .put("refreshToken", mask(a.refreshToken))
                        .put("status", a.statusText())
                )
            }
            ok(arr.toString())
        } catch (e: Exception) {
            err(e.message ?: "error")
        }
    }

    @JavascriptInterface
    fun addAccount(refreshToken: String, remark: String): String {
        return try {
            if (refreshToken.isBlank()) return err("refresh_token 不能为空")
            accountStore.add(refreshToken, remark)
            ok()
        } catch (e: Exception) {
            err(e.message ?: "error")
        }
    }

    @JavascriptInterface
    fun deleteAccount(index: Int): String {
        return try {
            val list = accountStore.getAll()
            if (index < 0 || index >= list.size) return err("索引越界")
            accountStore.remove(list[index])
            ok()
        } catch (e: Exception) {
            err(e.message ?: "error")
        }
    }

    private fun mask(token: String): String =
        if (token.length <= 12) token else token.take(6) + "..." + token.takeLast(4)

    @JavascriptInterface
    fun testAccount(index: Int): String {
        return try {
            val okTest = kimi.testAccount()
            if (okTest) ok() else err("引擎未就绪，请先登录 Kimi")
        } catch (e: Exception) {
            err(e.message ?: "error")
        }
    }

    /** 唤起内置登录页；由宿主 Activity 处理跳转与结果回传 */
    @JavascriptInterface
    fun openLogin(): String {
        return try {
            if (context is LoginLauncher) {
                onUi { (context as LoginLauncher).launchKimiLogin() }
                ok()
            } else {
                err("当前环境不支持内置登录")
            }
        } catch (e: Exception) {
            err(e.message ?: "error")
        }
    }

    @JavascriptInterface
    fun copyToClipboard(text: String): String {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("kimi", text))
            ok()
        } catch (e: Exception) {
            err(e.message ?: "error")
        }
    }

    // ------------------------------------------------------------ 设置

    @JavascriptInterface
    fun saveSettings(port: Int, apiKey: String, model: String): String {
        return try {
            if (port in 1..65535) settings.port = port
            settings.apiKey = apiKey
            settings.modelName = model
            ok()
        } catch (e: Exception) {
            err(e.message ?: "error")
        }
    }

    @JavascriptInterface
    fun setSwitch(key: String, value: Boolean): String {
        return try {
            when (key) {
                "autoStart" -> settings.autoStart = value
                "showFloat" -> settings.showFloat = value
                "useSearch" -> settings.useSearch = value
            }
            ok()
        } catch (e: Exception) {
            err(e.message ?: "error")
        }
    }

    // ------------------------------------------------------------ 日志

    @JavascriptInterface
    fun getLogs(): String {
        return try {
            val arr = JSONArray()
            Logger.all().forEach { e ->
                arr.put(JSONObject().put("time", e.time).put("text", e.text))
            }
            ok(arr.toString())
        } catch (e: Exception) {
            err(e.message ?: "error")
        }
    }

    @JavascriptInterface
    fun clearLogs(): String {
        Logger.clear()
        return ok()
    }

    // ------------------------------------------------------------ 权限

    private fun canOverlay(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun isIgnoringBattery(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @JavascriptInterface
    fun requestOverlay(): String {
        return try {
            if (!canOverlay()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.packageName)
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            }
            ok()
        } catch (e: Exception) {
            err(e.message ?: "error")
        }
    }

    @JavascriptInterface
    fun requestBattery(): String {
        return try {
            if (!isIgnoringBattery()) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    ok()
                } else {
                    context.startActivity(intent)
                }
            }
            ok()
        } catch (e: Exception) {
            err(e.message ?: "error")
        }
    }
}

/** 宿主 Activity 实现此接口以支持「内置登录」跳转 */
interface LoginLauncher {
    fun launchKimiLogin()
}
