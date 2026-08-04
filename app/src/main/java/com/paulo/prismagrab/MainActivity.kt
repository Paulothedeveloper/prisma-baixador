package com.paulo.prismagrab

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.paulo.prismagrab.ui.*
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val sharedUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
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

    // link compartilhado de outro app já dispara o download
    LaunchedEffect(Unit) {
        snapshotFlow { sharedUrl.value }.collectLatest { u ->
            if (!u.isNullOrBlank()) { sharedUrl.value = null; start(ctx, u, audioOnly) }
        }
    }

    Scaffold(containerColor = PrismBg) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text("PRISMA", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium, color = PrismText)
            Text("Baixe vídeos de qualquer rede social", color = PrismMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text("Cole o link do vídeo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    IconButton(onClick = { clipboard(ctx)?.let { url = it } }) {
                        Icon(Icons.Filled.ContentPaste, "Colar", tint = PrismMuted)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrismAccent, unfocusedBorderColor = PrismSurface2,
                    focusedContainerColor = PrismSurface, unfocusedContainerColor = PrismSurface,
                ),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = audioOnly, onClick = { audioOnly = !audioOnly },
                    label = { Text(if (audioOnly) "Só áudio (MP3)" else "Vídeo (MP4)") },
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { if (url.isNotBlank()) { start(ctx, url.trim(), audioOnly); url = "" } },
                    enabled = url.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrismAccent),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Baixar")
                }
            }

            if (!engineReady) {
                Spacer(Modifier.height(12.dp))
                Text(
                    engineError ?: "Preparando o motor na 1ª vez…",
                    color = if (engineError != null) PrismError else PrismMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(20.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items.reversed(), key = { it.id }) { DlCard(it) }
            }
        }
    }
}

@Composable
private fun DlCard(item: DlItem) {
    Surface(color = PrismSurface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                item.title.ifBlank { item.url },
                color = PrismText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            when (item.status) {
                DlStatus.BAIXANDO -> {
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = PrismAccent, trackColor = PrismSurface2,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${item.progress}%", color = PrismMuted, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { DownloadRepository.requestCancel(item.id) }) { Text("Cancelar", color = PrismError) }
                    }
                }
                DlStatus.PRONTO -> StatusLine(item.message.ifBlank { "Salvo na galeria." }, PrismAccent2)
                DlStatus.ERRO -> StatusLine(item.message, PrismError)
                DlStatus.CANCELADO -> StatusLine("Cancelado.", PrismMuted)
                else -> StatusLine(item.message.ifBlank { "Na fila…" }, PrismMuted)
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color) =
    Text(text, color = color, style = MaterialTheme.typography.bodySmall)

private fun start(ctx: Context, url: String, audioOnly: Boolean) =
    DownloadService.enqueue(ctx, firstUrl(url), audioOnly)

private fun clipboard(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    return cm?.primaryClip?.getItemAt(0)?.text?.toString()?.let { firstUrl(it) }?.takeIf { it.startsWith("http") }
}
