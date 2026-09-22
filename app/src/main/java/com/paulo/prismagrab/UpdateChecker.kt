package com.paulo.prismagrab

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualização do PRÓPRIO app (o APK é sideload do GitHub — não tem loja pra auto-atualizar).
 * No boot checa a última release e, se houver versão mais nova, a UI mostra um banner que leva
 * pro download. Instalar é o usuário quem faz (Android pede confirmação de fonte desconhecida).
 */
object UpdateChecker {
    private const val LATEST =
        "https://api.github.com/repos/Paulothedeveloper/prisma-baixador/releases/latest"

    data class Update(val version: String, val pageUrl: String)

    fun check(currentVersion: String): Update? {
        return try {
            val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").removePrefix("v").trim()
            val page = json.optString("html_url").ifBlank {
                "https://github.com/Paulothedeveloper/prisma-baixador/releases/latest"
            }
            if (tag.isNotEmpty() && isNewer(tag, currentVersion)) Update(tag, page) else null
        } catch (_: Exception) {
            null
        }
    }

    /** semver simples (a.b.c): remoto > local? */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".")
        val l = local.split(".")
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrNull(i)?.toIntOrNull() ?: 0
            val b = l.getOrNull(i)?.toIntOrNull() ?: 0
            if (a != b) return a > b
        }
        return false
    }
}
