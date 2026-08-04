# CLAUDE.md — PRISMA Baixador (APK Android)

Leia junto de `PRODUCT.md`. Irmão mobile do PRISMA desktop (`PC - PRISMA`).

## O que é
APK Android que baixa vídeo de redes sociais (motor `youtubedl-android` = yt-dlp+ffmpeg no aparelho).
Público primeiro; login/cookies para privado = fase 2. Distribuição: **APK no GitHub Release**
(Play Store barra downloader de social). Pacote `com.paulo.prismagrab`.

## Stack & build
Kotlin 2.0.21 · Compose Material3 · minSdk 26 / target 35 · AGP 8.7.3 · Gradle 8.14 · JDK 17 (JBR).
`./gradlew.bat assembleDebug` (teste) / `assembleRelease` (splits ABI + universal).

## Convenções (herdadas do estúdio)
- **Zero emoji na UI.** Identidade PRISMA dark (prisma/vidro, acento violeta-espectro).
- **Erro nunca vaza dump técnico** → `friendly()` no Service traduz o yt-dlp pra PT.
- Download **serial** (fila), salva em `Download/PRISMA` via MediaStore (scoped storage).
- **NÃO publicar/release sem OK explícito do Paulo.** Acumular e buildar 1x.

## Aprendizados / cuidados
- youtubedl-android via JitPack (`com.github.yausername.youtubedl-android:*:0.18.1`).
- `jniLibs.useLegacyPackaging = true` — .so do Python/ffmpeg não podem ser comprimidos.
- Motor desempacota na 1ª execução (App.kt em background) → UI espera `engineReady`.
- Scoped storage (Android 10+): baixa em `cacheDir` e copia pro MediaStore Download/PRISMA.
- Compartilhar de outro app: intent-filter ACTION_SEND text/plain → `firstUrl()` extrai o link.
