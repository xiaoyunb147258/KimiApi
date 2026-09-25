package dev.kimi2api.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Kimi 网页引擎（DOM 抓取版）：
 * 常驻 WebView 加载 kimi.com，靠页面上自己的代码发消息；
 * 发送 = 模拟输入+回车；收回复 = 轮询 DOM 读最后一条 assistant 消息文本。
 */
class KimiEngine private constructor(private val context: Context) {

    interface Listener {
        fun onDelta(requestId: String, delta: String)
        fun onDone(requestId: String, fullText: String, error: String?)
    }

    class PendingReq(
        val requestId: String,
        val listener: Listener,
        var lastText: String = "",
        var waited: Int = 0,
        var finished: Boolean = false
    )

    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var attached = false
    private val seq = AtomicLong(0)
    private val pending = HashMap<String, PendingReq>()
    private var ready = false

    private val bridge = object {
        @JavascriptInterface
        fun onPoll(rid: String, text: String, done: Boolean) {
            val req: PendingReq
            synchronized(pending) {
                req = pending[rid] ?: return
                if (req.finished) return
                val prev = req.lastText
                if (text.length > prev.length && text.startsWith(prev)) {
                    val delta = text.substring(prev.length)
                    req.lastText = text
                    handler.post { req.listener.onDelta(rid, delta) }
                } else if (text != prev) {
                    // 文本被改写，按全量替换处理
                    req.lastText = text
                }
                if (done) {
                    req.finished = true
                    pending.remove(rid)
                    val full = req.lastText
                    handler.post { req.listener.onDone(rid, full, null) }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun ensureWebView(onReady: () -> Unit) {
        if (webView != null && ready) {
            onReady()
            return
        }
        handler.post {
            val wv = WebView(context)
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            wv.addJavascriptInterface(bridge, "Native")
            var notified = false
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!notified && url != null && url.contains("kimi.com")) {
                        notified = true
                        ready = true
                        onReady()
                    }
                }
            }
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val lp = WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )
                wm.addView(wv, lp)
                attached = true
            } catch (_: Exception) {
                attached = false
            }
            wv.loadUrl(PAGE_URL)
            webView = wv
        }
    }

    fun isReady(): Boolean = ready

    /** 发送消息：记录发送前的 assistant 条数，发送后轮询 DOM */
    fun send(prompt: String, listener: Listener): String {
        val rid = "r" + seq.incrementAndGet()
        synchronized(pending) {
            pending[rid] = PendingReq(rid, listener)
        }
        handler.post {
            val q = JSONObject.quote(prompt)
            val js = "(function(){var b=document.querySelector('[contenteditable=\"true\"]');" +
                "if(!b){Native.onPoll('" + rid + "','',true);return;}" +
                "window.__KM_BASELINE__=document.querySelectorAll('.chat-content-item-assistant').length;" +
                "b.focus();document.execCommand('selectAll',false,null);" +
                "document.execCommand('delete',false,null);" +
                "document.execCommand('insertText',false," + q + ");" +
                "setTimeout(function(){b.dispatchEvent(new KeyboardEvent('keydown'," +
                "{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));},400);" +
                "window.__KM_POLL__('" + rid + "');})()"
            webView?.evaluateJavascript(POLL_FN, null)
            webView?.evaluateJavascript(js, null)
        }
        return rid
    }

    /** 页面内轮询读 DOM 的辅助函数 */
    private val POLL_FN = "(function(){if(window.__KM_POLL__)return;" +
        "window.__KM_POLL__=function(rid){" +
        "var tries=0;var stable=0;var last='';" +
        "var timer=setInterval(function(){" +
        "  tries++;" +
        "  var items=document.querySelectorAll('.chat-content-item-assistant');" +
        "  var baseline=window.__KM_BASELINE__||0;" +
        "  if(items.length<=baseline){ if(tries>150){clearInterval(timer);Native.onPoll(rid,last,true);} return; }" +
        "  var el=items[items.length-1];" +
        "  var box=el.querySelector('.segment-content-box')||el;" +
        "  var txt=(box.innerText||'').trim();" +
        "  var hasAction=!!el.querySelector('.segment-user-action-row');" +
        "  if(txt===last){stable++;}else{stable=0;last=txt;Native.onPoll(rid,txt,false);}" +
        "  var thinking=(el.innerText||'').indexOf('思考中')>=0||(el.innerText||'').indexOf('正在思考')>=0;" +
        "  if((hasAction||(stable>=3&&!thinking&&txt.length>0))&&txt.length>0){" +
        "    clearInterval(timer);Native.onPoll(rid,txt,true);return;}" +
        "  if(tries>300){clearInterval(timer);Native.onPoll(rid,last,true);}" +
        "},600);};})()"

    fun shutdown() {
        handler.post {
            if (attached) {
                try {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    webView?.let { wm.removeView(it) }
                } catch (_: Exception) {
                }
                attached = false
            }
            webView?.destroy()
            webView = null
            ready = false
        }
    }

    companion object {
        const val PAGE_URL = "https://www.kimi.com/chat/new"

        @Volatile
        private var instance: KimiEngine? = null

        fun get(context: Context): KimiEngine {
            return instance ?: synchronized(this) {
                instance ?: KimiEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
