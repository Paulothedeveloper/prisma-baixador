package com.paulo.prismagrab

import android.app.Application
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
        // background pra NÃO travar a abertura; a UI espera `engineReady` ficar true antes de baixar.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(this@App)
                FFmpeg.getInstance().init(this@App)
                engineReady.value = true
                // O wrapper (0.15.0) traz um yt-dlp antigo; puxa a versão nova em runtime pra
                // acompanhar as redes sociais (que mudam sempre). Falha aqui não é fatal.
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(this@App)
                } catch (_: Exception) { }
            } catch (e: Exception) {
                engineError.value = e.message ?: "Falha ao preparar o motor de download."
            }
        }
    }

    companion object {
        // prontidão do motor (a UI observa)
        val engineReady = MutableStateFlow(false)
        val engineError = MutableStateFlow<String?>(null)
    }
}
