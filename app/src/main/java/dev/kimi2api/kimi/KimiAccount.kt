package dev.kimi2api.kimi

import org.json.JSONObject

/**
 * 一个 Kimi 账号。
 * refreshToken 有效期约 3 个月；accessToken 有效期约 15 分钟，自动刷新。
 */
data class KimiAccount(
    var refreshToken: String,
    var remark: String = "",
    var accessToken: String = "",
    var expireAt: Long = 0L,
    var lastError: String = "",
    var using: Boolean = false
) {
    fun tokenValid(): Boolean =
        accessToken.isNotEmpty() && System.currentTimeMillis() < expireAt - 30_000L

    fun statusText(): String = when {
        using -> "使用中"
        lastError.isNotEmpty() -> "异常：$lastError"
        tokenValid() -> "可用"
        else -> "待刷新"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("refresh_token", refreshToken)
        put("remark", remark)
    }

    companion object {
        fun fromJson(o: JSONObject): KimiAccount = KimiAccount(
            refreshToken = o.optString("refresh_token"),
            remark = o.optString("remark", "")
        )
    }
}
