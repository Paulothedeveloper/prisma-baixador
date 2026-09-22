package com.paulo.prismagrab

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Alternativa SEM LOGIN: um resolvedor server-side (cobalt) resolve o link público e devolve a URL
 * direta da mídia — daí a gente baixa sem tocar em conta nenhuma (zero risco de banir Instagram).
 *
 * PORÉM cobalt precisa de uma INSTÂNCIA: a oficial (api.cobalt.tools) exige chave e as comunitárias
 * caem. Então a URL da instância é CONFIGURÁVEL ([Prefs.cobaltUrl], colada pelo usuário em Contas).
 * Sem instância configurada, esta alternativa fica desligada e o app cai no login/cookies.
 *
 * API cobalt v10: POST {url} (Accept+Content-Type json) -> {status, url|picker[]}.
 */
object Cobalt {
    // Instâncias públicas que resolvem SEM chave (fallback automático, o usuário não configura nada).
    // Volúveis por natureza (podem cair/pedir chave) — por isso é LISTA + só é usada quando o
    // anônimo falha, e se todas falharem o app cai no login. Provado no-key em set/2026: otomir23.
    private val DEFAULTS = listOf("https://co.otomir23.me")

    /** Tenta a instância do usuário (se colada) e depois as padrão, até uma resolver. */
    fun resolveAny(ctx: Context, mediaUrl: String): String? {
        val bases = Prefs.cobaltUrl(ctx)?.let { listOf(it) } ?: DEFAULTS
        for (b in bases) resolve(b, mediaUrl)?.let { return it }
        return null
    }

    /** Resolve a url pública numa URL direta de mídia via UMA instância cobalt. null se falhou. */
    fun resolve(base0: String, mediaUrl: String): String? {
        val base = base0.trimEnd('/')
        return try {
            val conn = (URL("$base/").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(JSONObject().put("url", mediaUrl).toString().toByteArray()) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: return null
            val json = JSONObject(body)
            when (json.optString("status")) {
                "redirect", "tunnel", "stream" -> json.optString("url").ifBlank { null }
                "picker" -> json.optJSONArray("picker")?.optJSONObject(0)?.optString("url")?.ifBlank { null }
                else -> null // "error" / desconhecido
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Preferências simples do app (URL da instância cobalt do modo sem-login). */
object Prefs {
    private const val P = "prefs"
    private const val COBALT = "cobalt_url"
    fun cobaltUrl(ctx: Context): String? =
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).getString(COBALT, "")?.takeIf { it.startsWith("http") }
    fun setCobaltUrl(ctx: Context, url: String) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putString(COBALT, url.trim()).apply()
    }
}
