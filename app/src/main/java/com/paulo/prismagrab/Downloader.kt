package com.paulo.prismagrab

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * Motor. Envolve yt-dlp (youtubedl-android) e resolve o pulo do gato do Android 10+:
 * o yt-dlp precisa de um CAMINHO real pra escrever, mas o app não pode gravar direto na pasta
 * pública Download. Então baixa num diretório PRÓPRIO do app (sem permissão) e depois COPIA pro
 * MediaStore (Download/PRISMA), que aparece na galeria/Downloads do usuário.
 */
object Downloader {

    data class Info(val title: String, val thumbnail: String?, val durationSec: Int)

    /** Como baixar: alvo resolvido + cookies (se houver). Duas alternativas p/ IG/TikTok/FB. */
    data class Plan(val target: String, val cookies: String?)

    /**
     * Decide o caminho ANTES de baixar:
     *  1) sem-login: se há instância cobalt configurada, resolve a URL pública nela (zero risco de conta);
     *  2) login: senão usa os cookies da sessão (se o usuário logou em Contas);
     *  3) senão tenta anônimo (IG/TikTok/FB vão falhar → banner de bloqueio).
     */
    fun plan(ctx: Context, url: String): Plan {
        if (Cookies.forUrl(url) != null) {
            Cobalt.resolve(ctx, url)?.let { return Plan(it, null) } // sem-login primeiro
        }
        return Plan(url, Cookies.fileForUrl(ctx, url))
    }

    fun getInfo(plan: Plan): Info {
        val req = YoutubeDLRequest(plan.target)
        plan.cookies?.let { req.addOption("--cookies", it) }
        val i = YoutubeDL.getInstance().getInfo(req)
        val t = i.title?.takeIf { it.isNotBlank() } ?: "video"
        return Info(t, i.thumbnail, i.duration)
    }

    /**
     * Baixa e salva na galeria. `onProgress(0..100)` reporta o andamento.
     * Retorna o nome do arquivo salvo. Lança em erro (a UI traduz pra PT).
     */
    fun download(
        ctx: Context,
        plan: Plan,
        processId: String,
        audioOnly: Boolean,
        maxHeight: Int,   // 0 = melhor disponível; >0 = teto de altura (720/1080/…)
        upscale: Boolean, // vídeo: força a resolução escolhida via ffmpeg scale (re-encode lento)
        onProgress: (Int, String) -> Unit,
    ): String {
        // pasta temporária DO APP (sempre gravável, sem permissão)
        val tmp = File(ctx.cacheDir, "dl_$processId").apply { deleteRecursively(); mkdirs() }
        try {
            val req = YoutubeDLRequest(plan.target)
            req.addOption("-o", "${tmp.absolutePath}/%(title).150s.%(ext)s")
            req.addOption("--no-playlist")
            req.addOption("--no-mtime")
            // cookies da sessão (login) quando o alvo é o link original de IG/TikTok/FB
            plan.cookies?.let { req.addOption("--cookies", it) }
            if (audioOnly) {
                req.addOption("-x")
                req.addOption("--audio-format", "mp3")
                // maxHeight aqui vira "qualidade de áudio": 0 = melhor, senão VBR menor
                req.addOption("--audio-quality", if (maxHeight == 0) "0" else "5")
            } else {
                // seleção de formato: melhor v+a já unidos em mp4 (ffmpeg embutido faz o merge)
                val fmt = if (maxHeight > 0 && !upscale)
                    "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]/bv*+ba/b"
                else
                    "bv*+ba/b"
                req.addOption("-f", fmt)
                req.addOption("--merge-output-format", "mp4")
                // UPSCALE nativo (opção do usuário): re-encoda escalando pra altura escolhida com
                // lanczos. Interpolação (não IA) e LENTA no celular — a UI avisa.
                if (upscale && maxHeight > 0) {
                    req.addOption("--recode-video", "mp4")
                    req.addOption("--postprocessor-args", "VideoConvertor:-vf scale=-2:$maxHeight:flags=lanczos")
                }
            }

            YoutubeDL.getInstance().execute(req, processId) { progress, _, _ ->
                if (progress >= 0f) onProgress(progress.toInt().coerceIn(0, 100), "")
            }

            val produced = tmp.listFiles()?.maxByOrNull { it.lastModified() }
                ?: throw IllegalStateException("Nada foi baixado.")
            return exportToGallery(ctx, produced, audioOnly)
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** Copia o arquivo baixado pro MediaStore (Download/PRISMA) e devolve o nome final. */
    private fun exportToGallery(ctx: Context, src: File, audioOnly: Boolean): String {
        val name = src.name
        val mime = if (audioOnly) "audio/mpeg" else "video/mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PRISMA")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Não consegui salvar na galeria.")
            resolver.openOutputStream(uri).use { out ->
                src.inputStream().use { it.copyTo(out!!) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            // Android 9-: grava direto na pasta pública Download/PRISMA
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PRISMA").apply { mkdirs() }
            src.copyTo(File(dir, name), overwrite = true)
        }
        return name
    }

    fun cancel(processId: String) {
        try { YoutubeDL.getInstance().destroyProcessById(processId) } catch (_: Exception) {}
    }
}
