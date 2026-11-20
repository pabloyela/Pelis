package com.tua.pelis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PelisTheme {
                Surface(color = Fondo, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
                    LibraryApp()
                }
            }
        }
    }
}

private fun videoPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, videoPermission()) == PackageManager.PERMISSION_GRANTED

@Composable
fun LibraryApp() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it || hasPermission(context)
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(videoPermission()) }

    if (granted) LibraryScreen() else PermissionScreen { launcher.launch(videoPermission()) }
}

@Composable
private fun PermissionScreen(onAsk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.VideoLibrary, null, tint = Acento, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            "Pelis necesita permiso para ver los videos del teléfono.",
            fontSize = 20.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAsk, contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)) {
            Text("Dar permiso", fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Si ya lo negaste, actívalo en Ajustes › Apps › Pelis › Permisos.",
            fontSize = 14.sp, color = TextoSuave, textAlign = TextAlign.Center
        )
    }
}

private const val TODOS = Long.MIN_VALUE

@Composable
private fun LibraryScreen() {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<Video>?>(null) }
    var progress by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var continueIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var folderId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Recargar al volver del reproductor (para actualizar el progreso)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            val list = VideoRepo.load(context)
            val prog = list.associate { it.uri.toString() to ProgressStore.fraction(context, it.uri.toString()) }
            val cont = ProgressStore.continueIds(context)
            withContext(Dispatchers.Main) {
                videos = list
                progress = prog
                continueIds = cont
            }
        }
    }

    BackHandler(enabled = folderId != null) { folderId = null }

    val all = videos
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val currentFolder = folderId
        if (all == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (currentFolder == null) {
            HomeList(all, progress, continueIds) { folderId = it }
        } else {
            val list = if (currentFolder == TODOS) all else all.filter { it.folderId == currentFolder }
            val title = if (currentFolder == TODOS) "Todos los videos" else list.firstOrNull()?.folderName ?: ""
            FolderList(title, list, progress, onBack = { folderId = null })
        }
    }
}

private fun play(context: Context, list: List<Video>, index: Int) {
    val i = Intent(context, PlayerActivity::class.java).apply {
        putStringArrayListExtra(PlayerActivity.EXTRA_URIS, ArrayList(list.map { it.uri.toString() }))
        putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, ArrayList(list.map { it.name }))
        putExtra(PlayerActivity.EXTRA_INDEX, index)
    }
    context.startActivity(i)
}

@Composable
private fun HomeList(
    all: List<Video>,
    progress: Map<String, Float>,
    continueIds: List<String>,
    onFolder: (Long) -> Unit
) {
    val context = LocalContext.current
    val byUri = remember(all) { all.associateBy { it.uri.toString() } }
    val continuing = remember(all, continueIds) { continueIds.mapNotNull { byUri[it] }.take(15) }
    val folders = remember(all) { VideoRepo.folders(all) }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Pelis", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)
            )
        }
        if (all.isEmpty()) {
            item {
                Text(
                    "No encontré videos en el teléfono.",
                    fontSize = 18.sp, color = TextoSuave, modifier = Modifier.padding(24.dp)
                )
            }
        }
        if (continuing.isNotEmpty()) {
            item { SectionTitle("Continuar viendo") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(continuing, key = { it.id }) { v ->
                        Column(
                            Modifier.width(210.dp).clip(RoundedCornerShape(14.dp))
                                .clickable { play(context, continuing, continuing.indexOf(v)) }
                        ) {
                            ThumbWithProgress(v, progress[v.uri.toString()] ?: 0f, Modifier.width(210.dp).height(118.dp))
                            Text(
                                v.name, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        if (all.isNotEmpty()) {
            item { SectionTitle("Carpetas") }
            item {
                FolderRow("Todos los videos", all.size, all.first(), Icons.Rounded.VideoLibrary) { onFolder(TODOS) }
            }
            items(folders, key = { it.id }) { f ->
                FolderRow(f.name, f.videos.size, f.videos.first(), Icons.Rounded.Folder) { onFolder(f.id) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Acento,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 10.dp)
    )
}

@Composable
private fun FolderRow(
    name: String,
    count: Int,
    sample: Video,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            VideoThumb(sample.uri, Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(10.dp)))
            Box(
                Modifier.align(Alignment.BottomStart).padding(4.dp).size(26.dp)
                    .clip(CircleShape).background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Acento, modifier = Modifier.size(16.dp)) }
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (count == 1) "1 video" else "$count videos", fontSize = 14.sp, color = TextoSuave)
        }
    }
}

@Composable
private fun FolderList(title: String, list: List<Video>, progress: Map<String, Float>, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Regresar", modifier = Modifier.size(30.dp)) }
            Text(
                title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp)
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list.size, key = { list[it].id }) { index ->
                val v = list[index]
                Row(
                    Modifier.fillMaxWidth().clickable { play(context, list, index) }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ThumbWithProgress(v, progress[v.uri.toString()] ?: 0f, Modifier.width(144.dp).height(81.dp))
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(v.name, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val extra = listOf(resolutionLabel(v.width, v.height), formatSize(v.size))
                            .filter { it.isNotBlank() }.joinToString(" · ")
                        Text(extra, fontSize = 13.sp, color = TextoSuave, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbWithProgress(v: Video, fraction: Float, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp))) {
        VideoThumb(v.uri, Modifier.fillMaxSize())
        if (v.duration > 0) {
            Text(
                formatTime(v.duration), fontSize = 12.sp, color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        if (fraction > 0f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x88FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}
