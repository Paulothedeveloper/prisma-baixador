package com.paulo.prismagrab

import android.content.Context
import android.webkit.CookieManager
import java.io.File

/**
 * Login/cookies pras redes que BLOQUEIAM download anônimo (Instagram, TikTok, Facebook) — mesmo
 * conteúdo público exige sessão. O desktop resolve lendo o cookie do navegador logado; no celular
 * o usuário loga uma vez numa WebView ([LoginActivity]) e a gente exporta os cookies num
 * cookies.txt (formato Netscape) que o yt-dlp lê via `--cookies`.
 *
 * O arquivo fica em filesDir (privado do app, fora de git e de outros apps) — NUNCA no repo.
 */
object Cookies {
    data class Site(
        val key: String,           // instagram / tiktok / facebook
        val label: String,         // "Instagram"
        val loginUrl: String,
        val cookieUrl: String,     // origem pra ler os cookies do CookieManager
        val domain: String,        // ".instagram.com" (1ª coluna da linha Netscape)
        val successCookie: String, // cookie que só existe logado (marca "conectado")
    )

    val SITES = listOf(
        Site("instagram", "Instagram", "https://www.instagram.com/accounts/login/", "https://www.instagram.com", ".instagram.com", "sessionid"),
        Site("tiktok", "TikTok", "https://www.tiktok.com/login", "https://www.tiktok.com", ".tiktok.com", "sessionid"),
        Site("facebook", "Facebook", "https://www.facebook.com/login", "https://www.facebook.com", ".facebook.com", "c_user"),
    )

    fun byKey(key: String): Site? = SITES.firstOrNull { it.key == key }

    /** Site cujo login serve pra ESTA url (ou null se a url não é de site que exige login). */
    fun forUrl(url: String): Site? {
        val u = url.lowercase()
        return when {
            u.contains("instagram.com") -> byKey("instagram")
            u.contains("tiktok.com") -> byKey("tiktok")
            u.contains("facebook.com") || u.contains("fb.watch") || u.contains("fb.com") -> byKey("facebook")
            else -> null
        }
    }

    private fun file(ctx: Context, site: Site) = File(ctx.filesDir, "cookies_${site.key}.txt")

    fun isLoggedIn(ctx: Context, site: Site): Boolean {
        val f = file(ctx, site)
        return f.exists() && f.readText().contains(site.successCookie)
    }

    /** Lê os cookies do WebView (CookieManager) deste site e grava em cookies.txt (Netscape). */
    fun saveFromWebView(ctx: Context, site: Site): Boolean {
        val raw = CookieManager.getInstance().getCookie(site.cookieUrl) ?: return false
        if (!raw.contains(site.successCookie)) return false
        val expiry = 4102444800L // ~2100: cookies do WebView não trazem expiry por-cookie
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        raw.split(";").forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) return@forEach
            val name = pair.substring(0, eq).trim()
            val value = pair.substring(eq + 1).trim()
            if (name.isNotEmpty()) {
                // domínio \t includeSub \t path \t secure \t expiry \t nome \t valor
                sb.append("${site.domain}\tTRUE\t/\tTRUE\t$expiry\t$name\t$value\n")
            }
        }
        file(ctx, site).writeText(sb.toString())
        return true
    }

    /** Caminho do cookies.txt pra usar no yt-dlp desta url, ou null se não logado / não aplica. */
    fun fileForUrl(ctx: Context, url: String): String? {
        val site = forUrl(url) ?: return null
        val f = file(ctx, site)
        return if (f.exists() && f.readText().contains(site.successCookie)) f.absolutePath else null
    }

    fun logout(ctx: Context, site: Site) {
        file(ctx, site).delete()
    }
}
