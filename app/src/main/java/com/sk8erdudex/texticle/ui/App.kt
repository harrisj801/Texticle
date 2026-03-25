package com.sk8erdudex.texticle.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.sk8erdudex.texticle.commands.CommandRegistry
import com.sk8erdudex.texticle.editor.EditorComposable
import com.sk8erdudex.texticle.editor.EditorState
import com.sk8erdudex.texticle.plugins.PluginHost
import com.sk8erdudex.texticle.ui.model.EditorDoc
import com.sk8erdudex.texticle.ui.theme.TexticleTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.net.toUri
import androidx.core.content.edit
import com.sk8erdudex.texticle.editor.SoraConfig
import com.sk8erdudex.texticle.editor.SoraEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current

    // ---------- SharedPreferences helpers (ordered lists via newline-delimited strings)
    val prefs = remember { ctx.getSharedPreferences("texticle_tabs", Context.MODE_PRIVATE) }

    fun readUriList(key: String): List<Uri> =
        prefs.getString(key, "")
            ?.lines()
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { runCatching { it.toUri() }.getOrNull() }
            ?: emptyList()

    fun writeUriList(key: String, list: List<Uri>) {
        prefs.edit { putString(key, list.joinToString("\n") { it.toString() }) }
    }

    fun getRecents(): List<Uri> = readUriList("recents_list")
    fun addRecent(uri: Uri) {
        val cur = getRecents().toMutableList()
        cur.remove(uri)           // ← remove the Uri object
        cur.add(uri)              // most recent at end
        val trimmed = if (cur.size > 20) cur.takeLast(20) else cur
        writeUriList("recents_list", trimmed)
    }


    fun getOpenTabs(): List<Uri> = readUriList("open_list")
    fun saveOpenTabs(uris: List<Uri>) = writeUriList("open_list", uris)

    // ---------- Tabs state
    val docs = remember { mutableStateListOf<EditorDoc>() }
    // Helpers for current doc
    // Keep index in-bounds even if docs is temporarily empty
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    // ---- right after you declare: val docs = remember { mutableStateListOf<EditorDoc>() } and currentIndex ----
    val tabItems = docs.toList()
    val tabCount = tabItems.size
    val selectedIndex = currentIndex.coerceIn(0, (tabCount - 1).coerceAtLeast(0))

    val docForUi = tabItems.getOrNull(selectedIndex) ?: EditorDoc(null, "Untitled", EditorState(), "")
    val currentText = docForUi.state.text.text
    val dirtyImmediate = currentText != docForUi.baseline
    var lastTabCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(docs.size) {
        val count = docs.size
        currentIndex = if (count == 0) {
            0
        } else {
            if (count > lastTabCount) {
                // a tab was added → jump to the new tab safely
                count - 1
            } else {
                // tabs removed or same → clamp
                currentIndex.coerceIn(0, count - 1)
            }
        }
        lastTabCount = count
    }



    var recents by remember { mutableStateOf(getRecents()) }

    fun persistTabs() = saveOpenTabs(docs.mapNotNull { it.uri })
    fun refreshRecents() { recents = getRecents() }

    // Load tabs from prefs (ordered) on launch
    LaunchedEffect(Unit) {
        val uris = getOpenTabs()
        if (uris.isEmpty()) {
            docs.add(EditorDoc(null, "Untitled", EditorState(), ""))
        } else {
            uris.forEach { uri ->
                // Do NOT call displayNameOf yet; first check the grant
                val hasRead = hasPersistedRead(ctx, uri)
                val title = if (hasRead) {
                    // safe to resolve a nice title now
                    displayNameOf(ctx, uri)
                } else {
                    // no permission: use a cheap fallback (no provider access)
                    (uri.lastPathSegment ?: "Document") + " (permission required – reopen)"
                }

                if (!hasRead) {

                    docs.add(EditorDoc(uri = null, title = title, state = EditorState(TextFieldValue("")), baseline = ""))
                    return@forEach
                }

                val text = runCatching {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                }.getOrElse { "" }

                docs.add(EditorDoc(uri = uri, title = title, state = EditorState(TextFieldValue(text)), baseline = text))
            }
            currentIndex = docs.lastIndex
        }
    }



    // ---------- Commands / plugins (optional; safe to keep)
    val commands = remember { CommandRegistry() }
    val pluginHost = remember {
        PluginHost(
            ctx,
            commands,
            getText = {
                val i = currentIndex.coerceIn(0, (docs.lastIndex).coerceAtLeast(0))
                docs.getOrNull(i)?.state?.text?.text ?: ""
            },
            setText = { newText ->
                val i = currentIndex.coerceIn(0, (docs.lastIndex).coerceAtLeast(0))
                if (i in docs.indices) {
                    docs[i] = docs[i].copy(state = docs[i].state.copy(text = TextFieldValue(newText)))
                }
            }
        )
    }
    LaunchedEffect(Unit) { pluginHost.ensureSamplePluginInstalled(); pluginHost.loadAll() }



    // ---------- Launchers


    fun saveInPlace(): Boolean {
        val u = docForUi.uri ?: return false
        val ok = save(ctx, u, currentText)
        if (ok) {
            val i = selectedIndex
            if (i in docs.indices) {
                docs[i] = docs[i].copy(baseline = currentText)
            }
            addRecent(u); refreshRecents(); persistTabs()
        }
        return ok
    }


    var pendingSaveAsIndex by rememberSaveable { mutableIntStateOf(-1) }

    val saveAsDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val idx = pendingSaveAsIndex
        pendingSaveAsIndex = -1
        if (uri != null && idx in docs.indices) {
            // Persist the grant (READ+WRITE if possible)
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.onFailure {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            if (save(ctx, uri, docs[idx].state.text.text)) {
                val title = displayNameOf(ctx, uri)
                docs[idx] = docs[idx].copy(uri = uri, title = title, baseline = docs[idx].state.text.text)
                currentIndex = idx
                addRecent(uri); refreshRecents(); persistTabs()
            }
        }
    }


    fun closeTab(index: Int) {
        if (index !in docs.indices) return
        docs.removeAt(index)
        if (docs.isEmpty()) {
            docs.add(EditorDoc(uri = null, title = "Untitled", state = EditorState(), baseline = ""))
            currentIndex = 0
        } else {
            currentIndex = currentIndex.coerceAtMost(docs.lastIndex)
        }
        persistTabs()
    }
    fun openOrSelectTab(title: String, uri: Uri?, text: String, ignoreCase: Boolean = false) {
        val byUri = uri?.let { u -> docs.indexOfFirst { it.uri == u } } ?: -1
        val targetIndex =
            if (byUri != -1) byUri
            else docs.indexOfFirst { it.uri == null && it.title.equals(title, ignoreCase = ignoreCase) }

        if (targetIndex != -1) {
            currentIndex = targetIndex
        } else {
            docs.add(
                EditorDoc(
                    uri = uri,
                    title = title,
                    state = EditorState(TextFieldValue(text)),
                    baseline = text
                )
            )
        }
        persistTabs()
    }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            // Persist the grant so we can use it after process death/restart
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    u,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.onFailure {
                // Some providers only allow READ
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(
                        u, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            val title = displayNameOf(ctx, u)
            val text = runCatching {
                ctx.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() } ?: ""
            }.getOrElse { "" }

            docs.add(EditorDoc(u, title, EditorState(TextFieldValue(text)), text))
            addRecent(u); refreshRecents(); persistTabs()
        }
    }

    fun getNewTabName() : String{
        for(i in 1..99)
        {
            if (docs.any { it.title == "Untitled-$i" }) continue else return "Untitled-$i"
        }
        return "Untitled-1"
    }


    var showRecents by remember { mutableStateOf(false) }

    // ---------- UI
    TexticleTheme {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(if (dirtyImmediate) "${docForUi.title} *" else docForUi.title) },
                        actions = {
                            TextButton(onClick = { docs.add(EditorDoc(null, getNewTabName(), EditorState(), "")) }) { Text("New") }
                            TextButton(onClick = { openDoc.launch(arrayOf("*/*")) }) { Text("Open") }
                            TextButton(onClick = {
                                if (docForUi.uri == null) {
                                    pendingSaveAsIndex = currentIndex
                                    saveAsDoc.launch(docForUi.title.ifBlank { "untitled.txt" })
                                } else {
                                    saveInPlace()
                                }
                            }) { Text("Save") }
                            TextButton(onClick = {
                                pendingSaveAsIndex = currentIndex
                                saveAsDoc.launch(docForUi.title.ifBlank { "untitled.txt" })
                            }) { Text("Save As") }
                            TextButton(onClick = { showRecents = true }) { Text("Recent") }
                        }
                    )
                    // Tabs row under the app bar (long-press opens menu; long-press+drag reorders)
                    run {
                        // Local states for menu + drag
                        var menuIndex by remember { mutableStateOf<Int?>(null) }
                        var draggingId by remember { mutableStateOf<String?>(null) } // identity for dragged tab
                        var dragAccum by remember { mutableFloatStateOf(0f) }
                        val thresholdPx = with(LocalDensity.current) { 64.dp.toPx() }

                        // Helpers
                        fun tabId(i: Int, d: EditorDoc) = d.uri?.toString() ?: "untitled#$i"
                        fun moveTab(from: Int, to: Int) {
                            if (from == to) return
                            if (from !in docs.indices || to !in docs.indices) return
                            val item = docs.removeAt(from)
                            docs.add(to, item)
                            currentIndex = to
                        }

                        val rowItems = docs.toList()
                        val rowCount = rowItems.size
                        val rowIndex = currentIndex.coerceIn(0, (rowCount - 1).coerceAtLeast(0))

                        if (rowCount > 0 && rowIndex in 0 until rowCount) {
                            ScrollableTabRow(selectedTabIndex = rowIndex, edgePadding = 8.dp) {
                                rowItems.forEachIndexed { indexSnapshot, dSnapshot ->
                                    // Resolve this tab’s current live index each frame by identity
                                    val id = tabId(indexSnapshot, dSnapshot)
                                    val liveIndex = docs.indexOfFirst { tabId(0, it) == id }.takeIf { it >= 0 } ?: indexSnapshot
                                    val label = if (dSnapshot.state.text.text != dSnapshot.baseline) "${dSnapshot.title} *" else dSnapshot.title

                                    Tab(
                                        selected = liveIndex == currentIndex,
                                        onClick = { currentIndex = liveIndex }, // short tap selects
                                        text = {
                                            val isDragging = draggingId == id
                                            val scale by animateFloatAsState(if (isDragging) 1.06f else 1f, label = "tabScale")
                                            val elevPx = with(LocalDensity.current) { (if (isDragging) 8.dp else 0.dp).toPx() }
                                            val closeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() } // drag up to close

                                            var showMenu by remember(id) { mutableStateOf(false) }
                                            var didMove by remember(id) { mutableStateOf(false) }
                                            var dragAccumY by remember(id) { mutableFloatStateOf(0f) } // vertical accumulation

                                            Box(
                                                modifier = Modifier
                                                    .graphicsLayer {
                                                        scaleX = scale; scaleY = scale
                                                        shadowElevation = elevPx
                                                        translationY = if (isDragging) -dragAccumY.coerceAtLeast(0f) * 0.1f else 0f
                                                    }
                                                    .pointerInput(id) {
                                                        detectDragGestures(
                                                            onDragStart = {
                                                                draggingId = id
                                                                dragAccum = 0f
                                                                dragAccumY = 0f
                                                                didMove = false
                                                            },
                                                            onDragEnd = {
                                                                // If user long-pressed but didn’t drag → open menu
                                                                if (!didMove) {
                                                                    menuIndex = liveIndex
                                                                    showMenu = true
                                                                }
                                                                draggingId = null
                                                                dragAccum = 0f
                                                                dragAccumY = 0f
                                                            },
                                                            onDragCancel = {
                                                                draggingId = null
                                                                dragAccum = 0f
                                                                dragAccumY = 0f
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                if (dragAmount.x != 0f || dragAmount.y != 0f) didMove = true

                                                                // vertical: close if dragged upwards far enough
                                                                dragAccumY += -dragAmount.y  // up is positive
                                                                if (dragAccumY >= closeThresholdPx) {
                                                                    // Close this tab immediately
                                                                    val idx = liveIndex
                                                                    if (idx in docs.indices) closeTab(idx)
                                                                    draggingId = null
                                                                    dragAccum = 0f
                                                                    dragAccumY = 0f
                                                                    return@detectDragGestures
                                                                }

                                                                // horizontal: reorder (only if not near closing)
                                                                dragAccum += dragAmount.x
                                                                val currentPos = docs.indexOfFirst { (it.uri?.toString() ?: it.title) == (dSnapshot.uri?.toString() ?: dSnapshot.title) }
                                                                if (currentPos == -1) return@detectDragGestures

                                                                if (dragAccum > thresholdPx && currentPos < docs.lastIndex) {
                                                                    moveTab(currentPos, currentPos + 1)
                                                                    dragAccum = 0f
                                                                } else if (dragAccum < -thresholdPx && currentPos > 0) {
                                                                    moveTab(currentPos, currentPos - 1)
                                                                    dragAccum = 0f
                                                                }
                                                            }
                                                        )
                                                    }
                                            ) {
                                                // Label
                                                Text(
                                                    label,
                                                    maxLines = 1,
                                                    modifier = Modifier.alpha(
                                                        if (isDragging && dragAccumY > 0f) (1f - (dragAccumY / closeThresholdPx).coerceIn(0f, 0.4f)) else 1f
                                                    )
                                                )

                                                // Underline indicator (primary) when dragging horizontally,
                                                // shifting to "closing" hint as we drag up
                                                if (isDragging) {
                                                    val color = if (dragAccumY > 0f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                    Box(
                                                        Modifier
                                                            .align(Alignment.BottomCenter)
                                                            .fillMaxWidth()
                                                            .height(2.dp)
                                                            .background(color)
                                                    )
                                                }

                                                // Context menu
                                                DropdownMenu(
                                                    expanded = showMenu && menuIndex == liveIndex,
                                                    onDismissRequest = { showMenu = false; menuIndex = null }
                                                ) {
                                                    DropdownMenuItem(text = { Text("Close") }, onClick = {
                                                        showMenu = false; closeTab(liveIndex); menuIndex = null
                                                    })
                                                    DropdownMenuItem(text = { Text("Close others") }, onClick = {
                                                        showMenu = false
                                                        val keep = docs.getOrNull(liveIndex)
                                                        if (keep != null) {
                                                            docs.clear(); docs.add(keep); currentIndex = 0; persistTabs()
                                                        }
                                                        menuIndex = null
                                                    })
                                                    DropdownMenuItem(text = { Text("Close to the right") }, onClick = {
                                                        showMenu = false
                                                        if (liveIndex in docs.indices) {
                                                            while (docs.lastIndex > liveIndex) docs.removeAt(docs.lastIndex)
                                                            currentIndex = liveIndex; persistTabs()
                                                        }
                                                        menuIndex = null
                                                    })
                                                    HorizontalDivider(
                                                        Modifier,
                                                        DividerDefaults.Thickness,
                                                        DividerDefaults.color
                                                    )
                                                    DropdownMenuItem(text = { Text("Move left") }, onClick = {
                                                        showMenu = false; val i = liveIndex; if (i > 0) moveTab(i, i - 1); menuIndex = null
                                                    })
                                                    DropdownMenuItem(text = { Text("Move right") }, onClick = {
                                                        showMenu = false; val i = liveIndex; if (i < docs.lastIndex) moveTab(i, i + 1); menuIndex = null
                                                    })
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                // Choose editor based on extension/MIME
                val lower = docForUi.title.lowercase()
                val kind = when {
                    lower.endsWith(".json") -> "json"
                    lower.endsWith(".xml") || lower.endsWith(".plist") -> "xml"
                    else -> null
                }

                //if (kind != null) {
                    SoraEditor(
                        text = docForUi.state.text.text,
                        onTextChange = { new ->
                            val i = currentIndex.coerceIn(0, (docs.lastIndex).coerceAtLeast(0))
                            if (i in docs.indices) {
                                docs[i] = docs[i].copy(
                                    state = docs[i].state.copy(
                                        text = androidx.compose.ui.text.input.TextFieldValue(new)
                                    )
                                )
                            }
                        },
                        config = SoraConfig(languageId = kind ?: ""),
                        modifier = Modifier.fillMaxSize()
                    )
//                //} else {
//                    // your existing Compose editor
//                    EditorComposable(
//                        state = docForUi.state,
//                        onChange = { v ->
//                            val i = currentIndex.coerceIn(0, (docs.lastIndex).coerceAtLeast(0))
//                            if (i in docs.indices) docs[i] = docs[i].copy(state = docs[i].state.copy(text = v))
//                        },
//                        modifier = Modifier.fillMaxSize()
//                    )
//                }
            }

            // Recent files dialog (from ordered MRU list)
            if (showRecents) {
                AlertDialog(
                    onDismissRequest = { showRecents = false },
                    title = { Text("Recent files") },
                    text = {
                        Column {
                            val items = recents
                            if (items.isEmpty()) Text("No recent files yet")
                            items.takeLast(10).forEach { u ->
                                val title = displayNameOf(ctx, u)
                                val safeLabel = runCatching { displayNameOf(ctx, u) }.getOrElse {
                                    u.lastPathSegment ?: "Document"
                                }

                                TextButton(onClick = {
                                    if (!hasPersistedRead(ctx, u)) {
                                        // No access anymore — prompt user to pick again via Open
                                        showRecents = false
                                        openDoc.launch(arrayOf("*/*"))
                                        return@TextButton
                                    }

                                    val text = runCatching {
                                        ctx.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() } ?: ""
                                    }.getOrElse { "" }

                                    docs.add(EditorDoc(uri = u, title = safeLabel, state = EditorState(TextFieldValue(text)), baseline = text))
                                    showRecents = false
                                    persistTabs()
                                }) {
                                    Text(safeLabel)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showRecents = false }) { Text("Close") } }
                )
            }
        }
    }
}

// ---------- helpers

private fun save(ctx: Context, uri: Uri, text: String): Boolean =
    runCatching {
        ctx.contentResolver.openOutputStream(uri, "rwt")
            ?.bufferedWriter()?.use { it.write(text) }
            ?: error("openOutputStream returned null")
    }.isSuccess

private fun displayNameOf(ctx: Context, uri: Uri): String {
    // Try quick path with DocumentFile; swallow SecurityException
    try {
        DocumentFile.fromSingleUri(ctx, uri)?.name?.let { return it }
    } catch (_: SecurityException) {
        // no persisted grant; fall through to fallbacks
    } catch (_: Throwable) {
        // provider may reject; fall through
    }

    // Try querying OpenableColumns if we can
    return try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
            null
        } ?: (uri.lastPathSegment ?: "Untitled")
    } catch (_: SecurityException) {
        // No access: don’t crash, return a reasonable fallback
        uri.lastPathSegment ?: "Untitled"
    } catch (_: Throwable) {
        uri.lastPathSegment ?: "Untitled"
    }
}

private fun hasPersistedRead(ctx: Context, uri: Uri): Boolean =
    ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

private fun hasPersistedWrite(ctx: Context, uri: Uri): Boolean =
    ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

