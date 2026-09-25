package dev.kimi2api.http

import java.io.OutputStream

/**
 * 封装 HTTP 响应写出：普通 JSON 响应 + chunked 流式响应。
 */
class ResponseWriter(private val out: OutputStream) {

    fun writeJson(status: Int, json: String, extraHeaders: Map<String, String> = emptyMap()) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $status ${statusText(status)}\r\n")
        sb.append("Content-Type: application/json; charset=utf-8\r\n")
        sb.append("Content-Length: ${bytes.size}\r\n")
        sb.append("Connection: close\r\n")
        sb.append("Access-Control-Allow-Origin: *\r\n")
        for ((k, v) in extraHeaders) sb.append("$k: $v\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    /** 开始 chunked 流式响应（SSE） */
    fun beginChunked(contentType: String = "text/event-stream; charset=utf-8") {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 200 OK\r\n")
        sb.append("Content-Type: $contentType\r\n")
        sb.append("Cache-Control: no-cache\r\n")
        sb.append("Transfer-Encoding: chunked\r\n")
        sb.append("Connection: close\r\n")
        sb.append("Access-Control-Allow-Origin: *\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** 写一个 chunk */
    fun writeChunk(data: String) {
        val bytes = data.toByteArray(Charsets.UTF_8)
        out.write(Integer.toHexString(bytes.size).toByteArray(Charsets.US_ASCII))
        out.write("\r\n".toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.write("\r\n".toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    /** 结束 chunked 响应 */
    fun endChunked() {
        out.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        500 -> "Internal Server Error"
        else -> "OK"
    }
}
