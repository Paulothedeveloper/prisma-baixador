package com.paulo.prismagrab

import android.app.Application
import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // O motor (yt-dlp + Python + ffmpeg) desempacota na 1ª vez (alguns segundos). Roda em
        // background pra NÃO travar a abertura.
        //
        // PORQUE O APP "PARAVA DE FUNCIONAR": o yt-dlp embutido no wrapper envelhece e as redes
        // sociais mudam sempre; sem um yt-dlp atual, TODO download falha. Antes o update rodava
        // fire-and-forget com o erro engolido, e o botão de baixar liberava ANTES do motor estar
        // pronto → o usuário baixava com o motor velho. Agora:
        //  - na 1ª vez (ou se o último update tem > 3 dias) ESPERA o update terminar antes de
        //    liberar downloads (engineReady só vira true depois);
        //  - se o update falhar mas já existir um yt-dlp instalado, segue com aviso (não trava);
        //  - o estado é VISÍVEL (engineUpdating / engineWarning) pra UI mostrar o que acontece.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(this@App)
                FFmpeg.getInstance().init(this@App)

                val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val last = prefs.getLong(KEY_UPDATED_AT, 0L)
                val now = System.currentTimeMillis()
                val mustUpdateFirst = last == 0L || (now - last) > STALE_MS

                if (mustUpdateFirst) {
                    // 1ª instalação ou motor velho: atualiza ANTES de liberar downloads.
                    engineUpdating.value = true
                    val ok = tryUpdate(this@App)
                    engineUpdating.value = false
                    if (ok) prefs.edit().putLong(KEY_UPDATED_AT, now).apply()
                    else if (last == 0L) engineWarning.value =
                        "Não consegui atualizar o motor de download (uso o embutido). Cheque a internet e reabra o app."
                    engineReady.value = true
                } else {
                    // Motor recente: libera na hora e atualiza em background pra próxima vez.
                    engineReady.value = true
                    engineUpdating.value = true
                    val ok = tryUpdate(this@App)
                    engineUpdating.value = false
                    if (ok) prefs.edit().putLong(KEY_UPDATED_AT, now).apply()
                }
            } catch (e: Exception) {
                engineError.value = e.message ?: "Falha ao preparar o motor de download."
            }
        }
    }

    /** Atualiza o yt-dlp (canal estável = releases/latest do yt-dlp, sempre atual). true se ok. */
    private fun tryUpdate(ctx: Context): Boolean = try {
        YoutubeDL.getInstance().updateYoutubeDL(ctx)
        true
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val PREFS = "engine"
        private const val KEY_UPDATED_AT = "ytdlp_updated_at"
        private const val STALE_MS = 3L * 24 * 60 * 60 * 1000 // 3 dias

        // prontidão do motor (a UI observa) — só true quando dá pra baixar de verdade
        val engineReady = MutableStateFlow(false)
        // erro fatal ao preparar o motor
        val engineError = MutableStateFlow<String?>(null)
        // atualizando o yt-dlp agora (UI mostra "atualizando motor…")
        val engineUpdating = MutableStateFlow(false)
        // aviso não-fatal (ex.: update falhou mas há motor embutido)
        val engineWarning = MutableStateFlow<String?>(null)
        // banner de bloqueio (IG/TikTok/FB negou sem login) — a UI mostra + botão Contas
        val blockNotice = MutableStateFlow<String?>(null)
    }
}
