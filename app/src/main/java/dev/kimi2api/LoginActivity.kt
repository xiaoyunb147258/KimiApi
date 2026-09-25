package dev.kimi2api

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 内置 Kimi 登录页。用户在 WebView 登录 kimi.com 后点右上角「保存」，
 * 本页从 Cookie / LocalStorage 提取 refresh_token 回传给主界面。
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    companion object {
        const val EXTRA_TOKEN = "refresh_token"
        const val LOGIN_URL = "https://www.kimi.com"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.WHITE)

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = WebViewClient()

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        lp.topMargin = dp(56)
        root.addView(webView, lp)

        val bar = FrameLayout(this)
        bar.setBackgroundColor(Color.parseColor("#D32F2F"))

        val title = TextView(this).apply {
            text = "登录 Kimi"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
        }
        val tlp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        tlp.leftMargin = dp(16)
        bar.addView(title, tlp)

        val saveBtn = Button(this).apply {
            text = "保存"
            setTextColor(Color.parseColor("#D32F2F"))
            setBackgroundColor(Color.parseColor("#FFEB3B"))
            setOnClickListener { extractToken() }
        }
        val blp = FrameLayout.LayoutParams(dp(80), dp(40))
        blp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        blp.rightMargin = dp(12)
        bar.addView(saveBtn, blp)

        root.addView(bar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ))

        setContentView(root)
        webView.loadUrl(LOGIN_URL)
    }

    private fun extractToken() {
        val all = CookieManager.getInstance().getCookie(LOGIN_URL) ?: ""
        var token = extractFromCookie(all)
        if (token.isNullOrEmpty()) {
            webView.evaluateJavascript(
                "(function(){try{return localStorage.getItem('refresh_token')||'';}catch(e){return '';}})()"
            ) { value ->
                val v = value?.trim('"') ?: ""
                if (v.isNotEmpty()) {
                    finishWithToken(v)
                } else {
                    Toast.makeText(this, "未找到 refresh_token，请确认已登录成功", Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        finishWithToken(token!!)
    }

    private fun extractFromCookie(cookie: String): String? {
        cookie.split(";").forEach { part ->
            val kv = part.trim().split("=")
            if (kv.size >= 2 && kv[0].trim() == "refresh_token") {
                return kv[1].trim()
            }
        }
        return null
    }

    private fun finishWithToken(token: String) {
        val data = Intent().putExtra(EXTRA_TOKEN, token)
        setResult(RESULT_OK, data)
        Toast.makeText(this, "已获取 refresh_token", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
