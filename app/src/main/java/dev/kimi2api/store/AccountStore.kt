package dev.kimi2api.store

import android.content.Context
import dev.kimi2api.kimi.KimiAccount
import org.json.JSONArray

/**
 * 账号存储：进程内单例，UI 与前台服务共用同一实例，避免双实例写丢失。
 */
class AccountStore private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kimi_accounts", Context.MODE_PRIVATE)

    private val accounts = mutableListOf<KimiAccount>()
    private var poll = 0

    init {
        load()
    }

    @Synchronized
    fun load() {
        accounts.clear()
        poll = prefs.getInt("poll_index", 0)
        val raw = prefs.getString("list", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                accounts.add(KimiAccount.fromJson(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun save() {
        val arr = JSONArray()
        val snapshot = accounts.toList()
        for (a in snapshot) arr.put(a.toJson())
        // commit() 同步落盘，保证紧随其后的 load() 能读到最新数据
        prefs.edit()
            .putString("list", arr.toString())
            .putInt("poll_index", poll)
            .commit()
    }

    @Synchronized
    fun getAll(): List<KimiAccount> = accounts.toList()

    @Synchronized
    fun add(refreshToken: String, remark: String = ""): KimiAccount {
        val a = KimiAccount(refreshToken.trim(), remark.trim())
        accounts.add(a)
        save()
        return a
    }

    @Synchronized
    fun remove(acc: KimiAccount) {
        accounts.remove(acc)
        save()
    }

    @Synchronized
    fun update(index: Int, refreshToken: String, remark: String) {
        if (index < 0 || index >= accounts.size) return
        accounts[index].refreshToken = refreshToken.trim()
        accounts[index].remark = remark.trim()
        accounts[index].accessToken = ""
        accounts[index].expireAt = 0L
        accounts[index].lastError = ""
        save()
    }

    @Synchronized
    fun size(): Int = accounts.size

    @Synchronized
    fun pollIndex(): Int = if (accounts.isEmpty()) 0 else poll % accounts.size

    @Synchronized
    fun setPollIndex(i: Int) {
        poll = i
        prefs.edit().putInt("poll_index", i).apply()
    }

    companion object {
        @Volatile
        private var instance: AccountStore? = null

        fun get(context: Context): AccountStore {
            return instance ?: synchronized(this) {
                instance ?: AccountStore(context).also { instance = it }
            }
        }
    }
}
