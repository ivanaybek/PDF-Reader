package com.ayaz.viewer.viewer.xlsx

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayaz.viewer.data.RecentFile
import com.ayaz.viewer.ui.components.EmptyState
import com.ayaz.viewer.ui.components.SpacerH
import com.ayaz.viewer.ui.components.SpacerW
import com.ayaz.viewer.ui.icons.AyazIcons
import com.ayaz.viewer.ui.theme.LocalAyazColors
import com.ayaz.viewer.xlsx.CellRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XlsxViewerScreen(
    file: RecentFile,
    controller: XlsxController,
    initialSheet: Int,
    lightSheets: Boolean,
    onToggleLightSheets: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSheetChanged: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val load by controller.state.collectAsStateWithLifecycle()
    var sheetIndex by rememberSaveable { mutableIntStateOf(initialSheet.coerceIn(0, (controller.sheetCount - 1).coerceAtLeast(0))) }
    var menu by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf(LongArray(0)) }
    var hitSet by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var hitIndex by remember { mutableIntStateOf(-1) }
    var searching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(sheetIndex) {
        controller.select(sheetIndex)
        onSheetChanged(sheetIndex)
        hits = LongArray(0); hitSet = emptySet(); hitIndex = -1
    }

    val ready = load as? SheetLoad.Ready
    val gridModel = remember(ready) { ready?.let { GridModel(it.sheet, controller.workbook, it.formatter) } }
    val gridState = remember(ready?.index, ready?.sheet) { GridState() }
    val palette = if (LocalAyazColors.current.isDark && !lightSheets) GridPalette.DARK else GridPalette.LIGHT

    fun selectCell(packed: Long) {
        gridState.selected = packed
    }

    fun runSearch() {
        val r = ready ?: return
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = scope.launch {
            searching = true
            val found = controller.search(r.sheet, q)
            searching = false
            hits = found
            hitSet = found.toHashSet()
            if (found.isEmpty()) {
                hitIndex = -1
                snackbar.showSnackbar("\"$q\" bu sayfada bulunamadı.")
            } else {
                hitIndex = 0
                val p = found[0]
                gridState.reveal(CellRef.rowOf(p), CellRef.colOf(p))
                selectCell(p)
            }
        }
    }

    fun gotoHit(delta: Int) {
        if (hits.isEmpty()) return
        hitIndex = (hitIndex + delta + hits.size) % hits.size
        val p = hits[hitIndex]
        gridState.reveal(CellRef.rowOf(p), CellRef.colOf(p))
        selectCell(p)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (searchOpen) {
                SheetSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = ::runSearch,
                    onClose = {
                        searchOpen = false; searchJob?.cancel(); hits = LongArray(0); hitSet = emptySet(); hitIndex = -1
                    },
                    status = when {
                        searching -> "…"
                        hits.isNotEmpty() -> "${hitIndex + 1}/${hits.size}"
                        else -> ""
                    },
                    onPrev = { gotoHit(-1) },
                    onNext = { gotoHit(1) },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${controller.sheetCount} sayfa • ${controller.workbook.sheets.getOrNull(sheetIndex)?.name ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) { Icon(Icons.Filled.Search, "Hücrelerde ara") }
                        IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Paylaş") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Diğer") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Açık renkli tablo") },
                                    leadingIcon = { Icon(AyazIcons.GridOn, null) },
                                    trailingIcon = { if (lightSheets) Icon(Icons.Filled.Check, null) },
                                    onClick = { menu = false; onToggleLightSheets() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Başa dön (A1)") },
                                    onClick = { menu = false; gridState.scrollX = 0f; gridState.scrollY = 0f },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
        },
        bottomBar = {
            SheetTabs(controller, sheetIndex) { sheetIndex = it }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val r = ready
            if (r != null && gridModel != null && r.index == sheetIndex) {
                val selected = gridState.selected
                CellInfoBar(
                    reference = if (selected >= 0) CellRef.name(CellRef.rowOf(selected), CellRef.colOf(selected)) else null,
                    value = if (selected >= 0) r.formatter.text(r.sheet, CellRef.rowOf(selected), CellRef.colOf(selected)) else "",
                    onCopy = { text ->
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("Hücre", text))
                        if (Build.VERSION.SDK_INT < 33) scope.launch { snackbar.showSnackbar("Kopyalandı") }
                    },
                )
                AnimatedVisibility(r.sheet.truncated) {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        SpacerW(8)
                        Text(
                            "Sayfa çok büyük: bellek güvenliği için ilk ${r.sheet.rowCount} satır gösteriliyor.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (r.sheet.isEmpty) {
                    EmptyState(AyazIcons.GridOn, "Bu sayfa boş", "\"${r.sheet.name}\" sayfasında gösterilecek veri yok.")
                } else {
                    SheetGrid(
                        model = gridModel,
                        state = gridState,
                        palette = palette,
                        hits = hitSet,
                        currentHit = hits.getOrNull(hitIndex) ?: -1L,
                        onCellTap = { selectCell(if (gridState.selected == it) -1L else it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                val l = load
                if (l is SheetLoad.Message && l.index == sheetIndex) {
                    EmptyState(AyazIcons.GridOn, "Sayfa açılamadı", l.text)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            SpacerH(12)
                            Text("Sayfa yükleniyor…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CellInfoBar(reference: String?, value: String, onCopy: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 6.dp)
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                reference ?: "—",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics { contentDescription = "Seçili hücre ${reference ?: "yok"}" },
            )
        }
        SpacerW(10)
        Text(
            if (reference == null) "Değerini görmek için bir hücreye dokunun" else value.ifEmpty { "(boş)" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = if (reference == null || value.isEmpty()) FontStyle.Italic else FontStyle.Normal,
            color = if (reference == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (reference != null && value.isNotEmpty()) {
            IconButton(onClick = { onCopy(value) }, modifier = Modifier.size(36.dp)) {
                Icon(AyazIcons.ContentCopy, "Hücre değerini kopyala", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SheetTabs(controller: XlsxController, selected: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { if (selected in 0 until controller.sheetCount) listState.animateScrollToItem(selected) }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(controller.workbook.sheets) { i, sheet ->
            val isSel = i == selected
            Column(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    sheet.name + if (sheet.hidden) " (gizli)" else "",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    fontStyle = if (sheet.hidden) FontStyle.Italic else FontStyle.Normal,
                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (isSel) {
                    SpacerH(3)
                    Box(Modifier.width(18.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

@Composable
private fun SheetSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    status: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Aramayı kapat") }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focus),
            placeholder = { Text("Hücrelerde ara") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
        )
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp))
        IconButton(onClick = onPrev) { Icon(Icons.Filled.KeyboardArrowUp, "Önceki sonuç") }
        IconButton(onClick = onNext) { Icon(Icons.Filled.KeyboardArrowDown, "Sonraki sonuç") }
    }
}
