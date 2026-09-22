package com.paulo.prismagrab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Faz o download em foreground (sobrevive ao app em background) e SERIAL: uma URL por vez, na
 * ordem em que entraram na fila. Cada intent com EXTRA_URL joga na fila; o único consumidor
 * processa em sequência e para o serviço quando a fila esvazia.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<DlItem>(Channel.UNLIMITED)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotif("PRISMA", "Preparando…", null))
        scope.launch { consume() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)?.trim()
        val audioOnly = intent?.getBooleanExtra(EXTRA_AUDIO, false) ?: false
        val maxHeight = intent?.getIntExtra(EXTRA_MAXH, 0) ?: 0
        val upscale = intent?.getBooleanExtra(EXTRA_UPSCALE, false) ?: false
        if (!url.isNullOrEmpty()) {
            val item = DlItem(
                id = "dl_${startId}_${url.hashCode()}", url = url,
                audioOnly = audioOnly, maxHeight = maxHeight, upscale = upscale,
            )
            DownloadRepository.add(item)
            queue.trySend(item)
        }
        return START_NOT_STICKY
    }

    private suspend fun consume() {
        if (started) return
        started = true
        for (item in queue) {
            processOne(item)
            // fila vazia e nada mais pendente → encerra o serviço
            if (queue.isEmpty && DownloadRepository.items.value.none {
                    it.status == DlStatus.FILA || it.status == DlStatus.BAIXANDO || it.status == DlStatus.BUSCANDO
                }) break
        }
        stopSelf()
    }

    private suspend fun processOne(item: DlItem) {
        val id = item.id
        if (DownloadRepository.consumeCancel(id)) {
            DownloadRepository.patch(id) { it.copy(status = DlStatus.CANCELADO, message = "Cancelado.") }
            return
        }
        try {
            // espera o motor terminar de desempacotar (1ª execução)
            if (!App.engineReady.value) {
                DownloadRepository.patch(id) { it.copy(status = DlStatus.BUSCANDO, message = "Preparando motor…") }
                App.engineError.value?.let { throw IllegalStateException(it) }
                App.engineReady.first { it }
            }

            DownloadRepository.patch(id) { it.copy(status = DlStatus.BUSCANDO, message = "Lendo o link…") }
            // título é só cosmético: se falhar (ex.: IG anônimo), segue — o download tem a cascata real
            val info = try { Downloader.getInfo(this, item.url) } catch (_: Exception) { Downloader.Info("video", null, 0) }
            DownloadRepository.patch(id) { it.copy(title = info.title, status = DlStatus.BAIXANDO) }
            notify("Baixando", info.title, 0)

            val saved = Downloader.download(this, item.url, id, item.audioOnly, item.maxHeight, item.upscale) { p, _ ->
                if (DownloadRepository.consumeCancel(id)) { Downloader.cancel(id); return@download }
                DownloadRepository.patch(id) { it.copy(progress = p) }
                notify("Baixando", info.title, p)
            }

            if (DownloadRepository.cancelRequested.value.contains(id)) {
                DownloadRepository.patch(id) { it.copy(status = DlStatus.CANCELADO, message = "Cancelado.") }
            } else {
                DownloadRepository.patch(id) { it.copy(status = DlStatus.PRONTO, progress = 100, message = "Salvo na galeria: $saved") }
                notify("Concluído", saved, 100)
            }
        } catch (e: Exception) {
            android.util.Log.e("PRISMA_DL", "falha download: ${e.message}", e)
            if (DownloadRepository.cancelRequested.value.contains(id)) {
                DownloadRepository.patch(id) { it.copy(status = DlStatus.CANCELADO, message = "Cancelado.") }
            } else {
                DownloadRepository.patch(id) { it.copy(status = DlStatus.ERRO, message = friendly(e)) }
                maybeBlockNotice(item.url, e)
            }
        }
    }

    /** Se IG/TikTok/FB bloqueou e o usuário não está logado nessa rede, mostra o banner de aviso. */
    private fun maybeBlockNotice(url: String, e: Exception) {
        val raw = (e.message ?: "").lowercase()
        val blocked = listOf("login", "private", "cookies", "sign in", "rate-limit", "429", "restricted")
            .any { raw.contains(it) }
        val site = Cookies.forUrl(url) ?: return
        if (blocked && !Cookies.isLoggedIn(this, site)) {
            App.blockNotice.value =
                "O ${site.label} pediu login pra esse vídeo. Toque em Contas e entre na sua conta (de preferência uma conta secundária) — aí é só baixar de novo."
        }
    }

    /** Erro cru do yt-dlp → mensagem curta em PT (nunca vazar dump inglês pro usuário). */
    private fun friendly(e: Exception): String {
        val raw = (e.message ?: "").lowercase()
        return when {
            raw.contains("unsupported url") || raw.contains("no video") -> "Esse link não tem vídeo pra baixar."
            raw.contains("private") || raw.contains("login") || raw.contains("cookies") ||
                raw.contains("sign in") -> "Precisa de login. Toque em Contas (canto superior) e entre no Instagram/TikTok/Facebook, depois baixe de novo."
            raw.contains("unavailable") || raw.contains("removed") -> "Vídeo indisponível ou removido."
            raw.contains("http error 404") -> "Link não encontrado (404)."
            raw.contains("timed out") || raw.contains("timeout") ||
                raw.contains("network") || raw.contains("resolve host") -> "Sem conexão. Confira a internet."
            e is YoutubeDLException -> "Não consegui baixar esse link."
            else -> "Falhou o download. Tente de novo."
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CH_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotif(title: String, text: String, progress: Int?): Notification {
        val b = NotificationCompat.Builder(this, CH_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(progress != null && progress < 100)
        if (progress != null) b.setProgress(100, progress, progress == 0)
        return b.build()
    }

    private fun notify(title: String, text: String, progress: Int) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotif(title, text, progress))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CH_ID = "downloads"
        private const val NOTIF_ID = 42
        const val EXTRA_URL = "url"
        const val EXTRA_AUDIO = "audio"
        const val EXTRA_MAXH = "maxh"
        const val EXTRA_UPSCALE = "upscale"

        fun enqueue(ctx: Context, url: String, audioOnly: Boolean, maxHeight: Int = 0, upscale: Boolean = false) {
            val i = Intent(ctx, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_AUDIO, audioOnly)
                putExtra(EXTRA_MAXH, maxHeight)
                putExtra(EXTRA_UPSCALE, upscale)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }
}
