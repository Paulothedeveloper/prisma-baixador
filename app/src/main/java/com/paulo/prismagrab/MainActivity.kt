package com.paulo.prismagrab

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.paulo.prismagrab.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val sharedUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askNotifPermission()
        handleShare(intent)
        setContent { PrismaTheme { HomeScreen(sharedUrl) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedUrl.value = firstUrl(it) }
        }
    }

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private fun askNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != 0
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** Extrai a 1ª URL de um texto compartilhado (apps mandam "legenda + link"). */
fun firstUrl(text: String): String =
    Regex("""https?://\S+""").find(text)?.value ?: text.trim()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(sharedUrl: MutableState<String?>) {
    val ctx = LocalContext.current
    val items by DownloadRepository.items.collectAsState()
    val engineReady by App.engineReady.collectAsState()
    val engineError by App.engineError.collectAsState()
    val engineUpdating by App.engineUpdating.collectAsState()
    val engineWarning by App.engineWarning.collectAsState()

    var url by remember { mutableStateOf("") }
    var audioOnly by remember { mutableStateOf(false) }
    var qIndex by remember { mutableStateOf(0) }
    var upscale by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { sharedUrl.value }.collectLatest { u ->
            if (!u.isNullOrBlank()) { sharedUrl.value = null; start(ctx, u, audioOnly) }
        }
    }

    Box(Modifier.fillMaxSize()) {
      Scaffold(containerColor = PrismBg) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)
        ) {
            // ---- Cabeçalho com a marca G6 ----
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.prisma_mark),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(11.dp))
                Text(
                    "PRISMA",
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp,
                    letterSpacing = 1.5.sp,
                    color = PrismText,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Baixe vídeos de qualquer rede social",
                color = PrismMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(22.dp))

            // ---- Campo de link ----
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text("Cole o link do vídeo", color = PrismMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    IconButton(onClick = { clipboard(ctx)?.let { url = it } }) {
                        Icon(Icons.Filled.ContentPaste, "Colar", tint = PrismMuted)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrismAccent, unfocusedBorderColor = PrismBorder,
                    focusedContainerColor = PrismSurface, unfocusedContainerColor = PrismSurface,
                    cursorColor = PrismAccent, focusedTextColor = PrismText, unfocusedTextColor = PrismText,
                ),
                shape = RoundedCornerShape(15.dp),
            )
            Spacer(Modifier.height(14.dp))
            // Tipo: Vídeo | Áudio (segmentado prism)
            TypeSegmented(audioOnly) { audioOnly = it; qIndex = 0; upscale = false }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val opts = if (audioOnly) AUDIO_Q else VIDEO_Q
                QualityPicker(opts, qIndex) { qIndex = it }
                Spacer(Modifier.weight(1f))
                DownloadButton(enabled = url.isNotBlank() && engineReady && engineError == null) {
                    val q = (if (audioOnly) AUDIO_Q else VIDEO_Q)[qIndex.coerceIn(0, (if (audioOnly) AUDIO_Q else VIDEO_Q).lastIndex)]
                    val up = upscale && !audioOnly && q.height > 0
                    start(ctx, url.trim(), audioOnly, q.height, up); url = ""
                }
            }
            // Upscale — só vídeo com resolução específica escolhida
            AnimatedVisibility(
                visible = !audioOnly && qIndex in VIDEO_Q.indices && VIDEO_Q[qIndex].height > 0,
                enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically(),
            ) { UpscaleRow(upscale) { upscale = it } }
            Spacer(Modifier.height(10.dp))
            SupportedHint()

            // Estado do motor: erro fatal > preparando/atualizando (1ª vez) > aviso não-fatal.
            val statusText = when {
                engineError != null -> engineError
                !engineReady && engineUpdating -> "Atualizando o motor de download…"
                !engineReady -> "Preparando o motor na 1ª vez…"
                engineUpdating -> "Atualizando o motor em segundo plano…"
                engineWarning != null -> engineWarning
                else -> null
            }
            if (statusText != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (engineError == null && (!engineReady || engineUpdating)) PrismRing(size = 14.dp, stroke = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        statusText,
                        color = if (engineError != null) PrismError else PrismMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (items.isEmpty()) EmptyState()
                else LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items.reversed(), key = { it.id }) { item ->
                        DlCard(item, Modifier.animateItem())
                    }
                }
            }
            Footer()
        }
      }

      // Splash de abertura: raios prismáticos girando + marca + barra espectro, enquanto o motor
      // desempacota na 1ª vez. Some (fade) quando o motor fica pronto (ou dá erro).
      var minElapsed by remember { mutableStateOf(false) }
      LaunchedEffect(Unit) { delay(1000); minElapsed = true }
      val showSplash = !minElapsed || (!engineReady && engineError == null)
      AnimatedVisibility(visible = showSplash, enter = EnterTransition.None, exit = fadeOut(tween(450))) {
        PrismSplash { s ->
          Image(painterResource(R.drawable.prisma_mark), contentDescription = null, modifier = Modifier.size(s))
        }
      }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.prisma_mark),
            contentDescription = null,
            modifier = Modifier.size(56.dp).alpha(0.22f),
        )
        Spacer(Modifier.height(14.dp))
        Text("Cole um link para começar", color = PrismMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "ou toque em Compartilhar dentro do app da rede",
            color = PrismMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DlCard(item: DlItem, modifier: Modifier = Modifier) {
    Surface(
        color = PrismSurface,
        shape = RoundedCornerShape(15.dp),
        modifier = modifier.fillMaxWidth().border(1.dp, PrismBorder, RoundedCornerShape(15.dp)),
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(
                item.title.ifBlank { item.url },
                color = PrismText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(9.dp))
            when (item.status) {
                DlStatus.BAIXANDO -> {
                    PrismProgress(progress = item.progress / 100f, modifier = Modifier.fillMaxWidth())
                    Row(
                        Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${item.progress}%", color = PrismMuted, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { DownloadRepository.requestCancel(item.id) }) {
                            Text("Cancelar", color = PrismError)
                        }
                    }
                }
                DlStatus.PRONTO -> StatusLine(item.message.ifBlank { "Salvo na galeria." }, PrismSuccess)
                DlStatus.ERRO -> StatusLine(item.message, PrismError)
                DlStatus.CANCELADO -> StatusLine("Cancelado.", PrismMuted)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    PrismRing(size = 13.dp, stroke = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    StatusLine(item.message.ifBlank { "Na fila…" }, PrismMuted)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) =
    Text(text, color = color, style = MaterialTheme.typography.bodySmall)

private fun start(ctx: Context, url: String, audioOnly: Boolean, maxHeight: Int = 0, upscale: Boolean = false) =
    DownloadService.enqueue(ctx, firstUrl(url), audioOnly, maxHeight, upscale)

private fun clipboard(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    return cm?.primaryClip?.getItemAt(0)?.text?.toString()?.let { firstUrl(it) }?.takeIf { it.startsWith("http") }
}

// ---- Qualidades (altura em px; 0 = melhor disponível) ----
private data class Quality(val label: String, val height: Int)

private val VIDEO_Q = listOf(
    Quality("Melhor", 0), Quality("2160p (4K)", 2160), Quality("1440p", 1440),
    Quality("1080p", 1080), Quality("720p", 720), Quality("480p", 480), Quality("360p", 360),
)
private val AUDIO_Q = listOf(Quality("Melhor", 0), Quality("Econômico", 1))

/** Segmentado Vídeo | Áudio com indicador que desliza (cor animada). */
@Composable
private fun TypeSegmented(audioOnly: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(PrismSurface)
            .border(1.dp, PrismBorder, RoundedCornerShape(13.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SegItem("Vídeo", selected = !audioOnly, modifier = Modifier.weight(1f)) { onChange(false) }
        SegItem("Áudio", selected = audioOnly, modifier = Modifier.weight(1f)) { onChange(true) }
    }
}

@Composable
private fun SegItem(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) PrismAccent.copy(alpha = 0.18f) else Color.Transparent, tween(220), label = "segbg")
    val fg by animateColorAsState(if (selected) PrismAccent else PrismMuted, tween(220), label = "segfg")
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(bg).clickable { onClick() }.padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Seletor de qualidade (pill + menu custom — nunca select nativo). */
@Composable
private fun QualityPicker(options: List<Quality>, index: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val idx = index.coerceIn(0, options.lastIndex)
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(PrismSurface)
                .border(1.dp, PrismBorder, RoundedCornerShape(12.dp)).clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(options[idx].label, color = PrismText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ExpandMore, null, tint = PrismMuted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(PrismSurface2)) {
            options.forEachIndexed { i, q ->
                DropdownMenuItem(
                    text = { Text(q.label, color = if (i == idx) PrismAccent else PrismText) },
                    onClick = { onPick(i); open = false },
                )
            }
        }
    }
}

@Composable
private fun DownloadButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = PrismAccent, disabledContainerColor = PrismSurface2,
            contentColor = Color.White, disabledContentColor = PrismMuted,
        ),
        shape = RoundedCornerShape(13.dp),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Filled.Download, null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text("Baixar", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun UpscaleRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bolt, null, tint = if (on) PrismAccent else PrismMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Forçar resolução (upscale)", color = PrismText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = on, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = PrismAccent,
                    uncheckedThumbColor = PrismMuted, uncheckedTrackColor = PrismSurface2,
                    uncheckedBorderColor = PrismBorder, checkedBorderColor = PrismAccent,
                ),
            )
        }
        Text(
            "Aumenta vídeos menores via ffmpeg (interpolação, não IA). Re-encoda no aparelho: mais lento.",
            color = PrismMuted, style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SupportedHint() {
    var open by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = PrismMuted, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Redes suportadas", color = PrismMuted, style = MaterialTheme.typography.bodySmall)
        }
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(
                "YouTube, Shorts, YouTube Music, TikTok, Instagram, X (Twitter), Reddit, Facebook, Threads, Bluesky, Vimeo, Kwai e +.\nSpotify e EpidemicSound exigem login/DRM — não baixam sem conta.",
                color = PrismMuted.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Rodapé: quem desenvolveu. */
@Composable
private fun Footer() {
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Desenvolvido por Paulo Codex", color = PrismMuted, style = MaterialTheme.typography.bodySmall)
        Text(
            "paulocodex.com",
            color = PrismAccent,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable { uri.openUri("https://paulocodex.com") }.padding(top = 1.dp),
        )
    }
}
