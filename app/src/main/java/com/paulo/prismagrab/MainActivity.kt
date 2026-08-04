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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.paulo.prismagrab.ui.*
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

    var url by remember { mutableStateOf("") }
    var audioOnly by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { sharedUrl.value }.collectLatest { u ->
            if (!u.isNullOrBlank()) { sharedUrl.value = null; start(ctx, u, audioOnly) }
        }
    }

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
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = audioOnly, onClick = { audioOnly = !audioOnly },
                    label = { Text(if (audioOnly) "Só áudio (MP3)" else "Vídeo (MP4)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrismAccent.copy(alpha = 0.16f),
                        selectedLabelColor = PrismAccent,
                        labelColor = PrismMuted, containerColor = PrismSurface,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = audioOnly,
                        borderColor = PrismBorder, selectedBorderColor = PrismAccent.copy(alpha = 0.5f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { if (url.isNotBlank()) { start(ctx, url.trim(), audioOnly); url = "" } },
                    enabled = url.isNotBlank(),
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

            if (!engineReady) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (engineError == null)
                        CircularProgressIndicator(Modifier.size(13.dp), color = PrismAccent, strokeWidth = 1.6.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        engineError ?: "Preparando o motor na 1ª vez…",
                        color = if (engineError != null) PrismError else PrismMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            if (items.isEmpty()) EmptyState()
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items.reversed(), key = { it.id }) { item ->
                    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                        DlCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.EmptyState() {
    Column(
        Modifier.fillMaxWidth().weight(1f).padding(bottom = 40.dp),
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
private fun DlCard(item: DlItem) {
    Surface(
        color = PrismSurface,
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, PrismBorder, RoundedCornerShape(15.dp)),
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
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = PrismAccent, trackColor = PrismSurface2,
                    )
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
                    CircularProgressIndicator(Modifier.size(12.dp), color = PrismAccent, strokeWidth = 1.6.dp)
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

private fun start(ctx: Context, url: String, audioOnly: Boolean) =
    DownloadService.enqueue(ctx, firstUrl(url), audioOnly)

private fun clipboard(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    return cm?.primaryClip?.getItemAt(0)?.text?.toString()?.let { firstUrl(it) }?.takeIf { it.startsWith("http") }
}
