# PRISMA Baixador — PRODUCT.md

## O que é
App Android (APK, sideload) que baixa vídeos de redes sociais e cola direto na galeria.
Irmão de bolso do PRISMA desktop (DAM). Foco único: **colar link → baixar → salvar**.

## Para quem
Editor/criador do Paulo que já usa o PRISMA no PC e quer puxar clipes no celular sem app
cheio de anúncio/limite. Uso pessoal + venda futura junto do ecossistema PRISMA.

## O que faz (v1.0.0 — público primeiro)
- Cola o link (ou **compartilha** de outro app → cai direto no PRISMA) e baixa.
- Todas as redes que o yt-dlp suporta (YouTube, TikTok, Instagram público, X/Twitter, Facebook,
  Reddit, Kwai, Vimeo, etc. — 1000+ sites).
- Vídeo (MP4, melhor qualidade) ou **só áudio (MP3)**.
- **Fila serial**: cola vários, baixa um por um, com progresso e cancelar.
- Salva em **Download/PRISMA** (aparece na galeria).
- Erros em **português**, nunca dump técnico.

## Fora do escopo (fase 2)
- Login/cookies para conteúdo **privado/restrito** (Instagram logado, YouTube age-restricted).
- Escolha de resolução/formato avançado; playlists; legendas.
- Play Store (o Google barra downloader de social) → distribuição = **APK no GitHub Release**.

## Motor
`youtubedl-android` (yausername): yt-dlp + Python + ffmpeg + aria2c empacotados — o mesmo motor
do app Seal. 100% no aparelho, nada passa por servidor de terceiro.

## Stack
Kotlin 2.0.21 + Jetpack Compose (Material3), minSdk 26 / targetSdk 35, AGP 8.7.3, Gradle 8.14.
Pacote `com.paulo.prismagrab`. Serviço em foreground pro download sobreviver em background.
