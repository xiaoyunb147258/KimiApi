package dev.kimi2api.kimi

import dev.kimi2api.engine.KimiEngine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Kimi 调用入口：阻塞式 chat()，内部走网页引擎。
 */
class KimiClient(private val engine: KimiEngine) {

    class NoAccountException : Exception("引擎未就绪，请先在 App 内登录 Kimi")

    fun chat(
        prompt: String,
        useSearch: Boolean = false,
        onDelta: ((String) -> Unit)? = null
    ): String {
        val latch = CountDownLatch(1)
        val error = AtomicReference<String?>()
        val full = StringBuilder()
        engine.send(prompt, object : KimiEngine.Listener {
            override fun onDelta(requestId: String, delta: String) {
                full.append(delta)
                onDelta?.invoke(delta)
            }
            override fun onDone(requestId: String, fullText: String, err: String?) {
                if (fullText.isNotEmpty()) {
                    full.setLength(0)
                    full.append(fullText)
                }
                error.set(err)
                latch.countDown()
            }
        })
        if (!latch.await(180, TimeUnit.SECONDS)) {
            throw Exception("等待回复超时")
        }
        error.get()?.let { throw Exception(it) }
        val r = full.toString()
        if (r.isBlank()) throw Exception("未收到回复")
        return r
    }

    fun testAccount(): Boolean = engine.isReady()
}
