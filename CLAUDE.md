# CLAUDE.md — PRISMA Baixador (APK Android)

Leia junto de `PRODUCT.md`. Irmão mobile do PRISMA desktop (`PC - PRISMA`).

## O que é
APK Android que baixa vídeo de redes sociais (motor `youtubedl-android` = yt-dlp+ffmpeg no aparelho).
Distribuição: **APK no GitHub Release** (Play Store barra downloader de social). Pacote `com.paulo.prismagrab`.

## Login/cookies (v1.2.0) — Instagram/TikTok/Facebook
Essas redes bloqueiam download ANÔNIMO até de vídeo público (o desktop resolve lendo o cookie do
navegador logado; no celular não há navegador pra emprestar). Solução = login in-app:
`LoginActivity` (WebView) → o usuário loga 1x → `Cookies.saveFromWebView` exporta os cookies num
`cookies_<site>.txt` (Netscape) em `filesDir` → `Downloader`/`getInfo` passam `--cookies` pro yt-dlp.
UI: botão **Contas** (header) → `AccountsDialog` (Entrar/Sair por rede). O erro de login guia pra lá.
Os `cookies_*.txt` são a SESSÃO do usuário (sensível) e vivem só em `filesDir` (privado, fora de git).

## Stack & build
Kotlin 2.0.21 · Compose Material3 · minSdk 26 / target 35 · AGP 8.7.3 · Gradle 8.14 · JDK 17 (JBR).
`./gradlew.bat assembleDebug` (teste) / `assembleRelease` (splits ABI + universal).

## Convenções (herdadas do estúdio)
- **Zero emoji na UI.** Identidade PRISMA dark (prisma/vidro, acento violeta-espectro).
- **Erro nunca vaza dump técnico** → `friendly()` no Service traduz o yt-dlp pra PT.
- Download **serial** (fila), salva em `Download/PRISMA` via MediaStore (scoped storage).
- **NÃO publicar/release sem OK explícito do Paulo.** Acumular e buildar 1x.

## Aprendizados / cuidados
- youtubedl-android = fork JunkFood02 no Maven Central (`io.github.junkfood02.youtubedl-android:{library,ffmpeg}:0.18.1`) — Python 3.11 + yt-dlp atual (o upstream yausername/JitPack trava em Python 3.8).
- **"Parou de funcionar" = yt-dlp embutido velho.** O motor SE ATUALIZA em runtime (`updateYoutubeDL`, canal STABLE = `yt-dlp/releases/latest`). Regra (App.kt): 1ª vez ou update > 3 dias → ESPERA o update antes de liberar downloads; falha com motor já instalado → segue com aviso; estado visível (`engineUpdating`/`engineWarning`). O botão de baixar só habilita com `engineReady`. NUNCA voltar a engolir o erro do update nem liberar download antes do motor pronto.
- `jniLibs.useLegacyPackaging = true` — .so do Python/ffmpeg não podem ser comprimidos.
- Motor desempacota na 1ª execução (App.kt em background) → UI espera `engineReady`.
- Scoped storage (Android 10+): baixa em `cacheDir` e copia pro MediaStore Download/PRISMA.
- Compartilhar de outro app: intent-filter ACTION_SEND text/plain → `firstUrl()` extrai o link.
