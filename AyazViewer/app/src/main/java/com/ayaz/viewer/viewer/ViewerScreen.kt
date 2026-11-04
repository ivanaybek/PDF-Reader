package com.ayaz.viewer.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayaz.viewer.data.RecentFile
import com.ayaz.viewer.share.ShareSheet
import com.ayaz.viewer.ui.components.EmptyState
import com.ayaz.viewer.ui.components.SpacerH
import com.ayaz.viewer.viewer.pdf.PdfViewerScreen
import com.ayaz.viewer.viewer.xlsx.XlsxViewerScreen

@Composable
fun ViewerScreen(vm: ViewerViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var shareFile by remember { mutableStateOf<RecentFile?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = state) {
            is ViewerState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    SpacerH(14)
                    Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is ViewerState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Info,
                    title = "Dosya açılamadı",
                    message = s.message,
                    action = { Button(onClick = onClose) { Text("Kapat") } },
                )
            }
            is ViewerState.Pdf -> PdfViewerScreen(
                file = s.file,
                controller = s.controller,
                initialPage = remember(s.file.id) { vm.initialPage(s.file) },
                onBack = onClose,
                onShare = { shareFile = s.file },
                onPageChanged = { vm.savePdfPage(s.file, it) },
            )
            is ViewerState.Xlsx -> XlsxViewerScreen(
                file = s.file,
                controller = s.controller,
                initialSheet = remember(s.file.id) { s.file.lastSheet },
                lightSheets = settings.lightSheets,
                onToggleLightSheets = vm::toggleLightSheets,
                onBack = onClose,
                onShare = { shareFile = s.file },
                onSheetChanged = { vm.saveSheet(s.file, it) },
            )
        }
    }
    shareFile?.let { ShareSheet(file = it, onDismiss = { shareFile = null }) }
}
