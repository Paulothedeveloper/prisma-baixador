package com.paulo.prismagrab

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast

/**
 * Login numa rede (Instagram/TikTok/Facebook) via WebView. Quando a sessão aparece nos cookies
 * (successCookie), exporta pra cookies.txt ([Cookies.saveFromWebView]) e fecha com RESULT_OK.
 * A partir daí o [Downloader] passa `--cookies` pro yt-dlp e o download público volta a funcionar.
 */
class LoginActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val site = Cookies.byKey(intent.getStringExtra("site") ?: "instagram") ?: run { finish(); return }

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0E0F13"))
            cm.setAcceptThirdPartyCookies(this, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val cookie = cm.getCookie(site.cookieUrl) ?: ""
                    if (cookie.contains(site.successCookie)) {
                        cm.flush()
                        if (Cookies.saveFromWebView(this@LoginActivity, site)) {
                            Toast.makeText(this@LoginActivity, "Conectado ao ${site.label}.", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                }
            }
        }
        setContentView(web, FrameLayout.LayoutParams(-1, -1))
        web.loadUrl(site.loginUrl)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
