package dev.kimi2api.http

import dev.kimi2api.kimi.KimiClient
import dev.kimi2api.store.SettingsStore
import dev.kimi2api.util.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.util.UUID

/**
 * OpenAI 兼容路由：
 *   GET  /v1/models
 *   POST /v1/chat/completions （stream / non-stream）
 */
class RequestHandler(
    private val settings: SettingsStore,
    private val kimi: KimiClient
) {

    fun handle(req: Request, out: OutputStream) {
        val writer = ResponseWriter(out)
        val path = req.path.substringBefore("?")

        when {
            req.method == "OPTIONS" -> {
                writer.writeJson(200, "{}")
                return
            }
            path == "/v1/models" && req.method == "GET" -> {
                if (!auth(req, writer)) return
                writer.writeJson(200, modelsJson())
                return
            }
            path == "/v1/chat/completions" && req.method == "POST" -> {
                if (!auth(req, writer)) return
                handleChat(req, writer)
                return
            }
            path == "/health" -> {
                writer.writeJson(200, "{\"status\":\"ok\"}")
                return
            }
            else -> {
                writer.writeJson(404, errorJson("Not found: $path", "invalid_request_error"))
                return
            }
        }
    }

    private fun auth(req: Request, writer: ResponseWriter): Boolean {
        val key = settings.apiKey
        if (key.isEmpty()) return true
        val token = req.bearerToken()
        if (token != key) {
            writer.writeJson(401, errorJson("Invalid API key", "invalid_api_key"))
            return false
        }
        return true
    }

    private fun handleChat(req: Request, writer: ResponseWriter) {
        val body = try {
            JSONObject(req.body)
        } catch (e: Exception) {
            writer.writeJson(400, errorJson("Invalid JSON body", "invalid_request_error"))
            return
        }

        val prompt = buildPrompt(body)
        if (prompt.isEmpty()) {
            writer.writeJson(400, errorJson("No user message", "invalid_request_error"))
            return
        }
        val stream = body.optBoolean("stream", false)
        val model = body.optString("model", settings.modelName)
        // kimi-search 模型自动开启联网搜索，其余走设置里的默认开关
        val useSearch = if (model == "kimi-search") true else settings.useSearch
        val id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(24)
        val created = System.currentTimeMillis() / 1000

        if (!stream) {
            try {
                val reply = kimi.chat(prompt, useSearch)
                writer.writeJson(200, completionJson(id, created, model, reply))
            } catch (e: Exception) {
                Logger.log("请求失败：" + e.message)
                writer.writeJson(500, errorJson(e.message ?: "upstream error", "api_error"))
            }
            return
        }

        // 流式 SSE
        writer.beginChunked()
        try {
            writer.writeChunk(sseChunk(id, created, model, mapOf("role" to "assistant")))
            val reply = kimi.chat(prompt, useSearch) { delta ->
                if (delta.isNotEmpty()) {
                    writer.writeChunk(sseChunk(id, created, model, mapOf("content" to delta)))
                }
            }
            if (reply.isEmpty()) {
                writer.writeChunk(sseChunk(id, created, model, mapOf("content" to "")))
            }
            writer.writeChunk(sseChunk(id, created, model, emptyMap(), "stop"))
            writer.writeChunk("data: [DONE]\n\n")
        } catch (e: Exception) {
            Logger.log("流式请求失败：" + e.message)
            writer.writeChunk(sseError(e.message ?: "upstream error"))
        }
        writer.endChunked()
    }

    private fun buildPrompt(body: JSONObject): String {
        val messages = body.optJSONArray("messages") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until messages.length()) {
            val m = messages.optJSONObject(i) ?: continue
            val role = m.optString("role", "user")
            val content = m.optString("content")
            if (content.isEmpty()) continue
            when (role) {
                "system" -> sb.append("[system] ").append(content).append("\n")
                "assistant" -> sb.append("[assistant] ").append(content).append("\n")
                else -> sb.append(content).append("\n")
            }
        }
        return sb.toString().trim()
    }

    private fun sseChunk(
        id: String,
        created: Long,
        model: String,
        delta: Map<String, String>,
        finish: String? = null
    ): String {
        val deltaObj = JSONObject()
        for ((k, v) in delta) deltaObj.put(k, v)
        val choice = JSONObject()
            .put("index", 0)
            .put("delta", deltaObj)
            .put("finish_reason", finish ?: JSONObject.NULL)
        val obj = JSONObject()
            .put("id", id)
            .put("object", "chat.completion.chunk")
            .put("created", created)
            .put("model", model)
            .put("choices", JSONArray().put(choice))
        return "data: $obj\n\n"
    }

    private fun sseError(msg: String): String {
        val obj = JSONObject()
            .put("error", JSONObject().put("message", msg).put("type", "api_error"))
        return "data: $obj\n\n"
    }

    private fun completionJson(id: String, created: Long, model: String, content: String): String {
        val message = JSONObject().put("role", "assistant").put("content", content)
        val choice = JSONObject()
            .put("index", 0)
            .put("message", message)
            .put("finish_reason", "stop")
        val usage = JSONObject()
            .put("prompt_tokens", 0)
            .put("completion_tokens", 0)
            .put("total_tokens", 0)
        return JSONObject()
            .put("id", id)
            .put("object", "chat.completion")
            .put("created", created)
            .put("model", model)
            .put("choices", JSONArray().put(choice))
            .put("usage", usage)
            .toString()
    }

    private fun modelsJson(): String {
        val now = System.currentTimeMillis() / 1000
        val list = JSONArray()
        list.put(
            JSONObject()
                .put("id", settings.modelName)
                .put("object", "model")
                .put("created", now)
                .put("owned_by", "kimi")
        )
        list.put(
            JSONObject()
                .put("id", "kimi-search")
                .put("object", "model")
                .put("created", now)
                .put("owned_by", "kimi")
        )
        return JSONObject().put("object", "list").put("data", list).toString()
    }

    private fun errorJson(msg: String, type: String): String =
        JSONObject().put("error", JSONObject().put("message", msg).put("type", type)).toString()
}
