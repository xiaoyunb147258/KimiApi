package dev.kimi2api

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dev.kimi2api.ui.LoginLauncher
import dev.kimi2api.ui.WebBridge
import org.json.JSONObject

class MainActivity : AppCompatActivity(), LoginLauncher {

    private lateinit var webView: WebView
    private var bridge: WebBridge? = null

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val loginLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val token = result.data?.getStringExtra(LoginActivity.EXTRA_TOKEN).orEmpty()
                if (token.isNotEmpty()) {
                    val js = "window.onLoginToken(${JSONObject.quote(token)})"
                    webView.post { webView.evaluateJavascript(js, null) }
                }
            }
        }

    override fun launchKimiLogin() {
        loginLauncher.launch(Intent(this, LoginActivity::class.java))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        webView.webViewClient = WebViewClient()

        val b = WebBridge(this, webView)
        bridge = b
        webView.addJavascriptInterface(b, "KimiNative")
        webView.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        requestNotificationPermission()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        bridge?.destroy()
        bridge = null
        webView.destroy()
        super.onDestroy()
    }
}
