# PRISMA Baixador

Baixador de vídeos de redes sociais para Android. Cola o link (ou compartilha de outro app) →
baixa → salva na galeria. Motor `youtubedl-android` (yt-dlp + ffmpeg no aparelho).

> Companheiro de bolso do [PRISMA desktop](../PC%20-%20PRISMA). Público primeiro; login para
> conteúdo privado vem na fase 2. Distribuição = APK no GitHub Release (não Play Store).

## Build

```bash
# JDK 17 (Android Studio JBR) + Android SDK 35
./gradlew.bat assembleDebug        # APK de teste  -> app/build/outputs/apk/debug/
./gradlew.bat assembleRelease      # APK de release (por ABI + universal)
```

APK universal serve pra sideload sem saber a arquitetura. Splits por ABI reduzem tamanho.

## Stack
Kotlin 2.0.21 · Jetpack Compose (Material3) · minSdk 26 / targetSdk 35 · AGP 8.7.3 · Gradle 8.14.

## Estrutura
- `App.kt` — inicializa o motor (yt-dlp/ffmpeg) em background na abertura.
- `Downloader.kt` — envolve o yt-dlp; baixa em pasta do app e exporta pro MediaStore (Download/PRISMA).
- `DownloadService.kt` — foreground service, fila **serial**, notificação com progresso, erros em PT.
- `DownloadRepository.kt` — estado único da fila (StateFlow); UI observa.
- `MainActivity.kt` — UI Compose (campo de link, colar, vídeo/áudio, lista com progresso/cancelar).
- `ui/Theme.kt` — identidade PRISMA dark.
