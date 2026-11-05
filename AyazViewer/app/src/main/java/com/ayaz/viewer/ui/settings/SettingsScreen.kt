package com.ayaz.viewer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayaz.viewer.BuildConfig
import com.ayaz.viewer.core.Formatters
import com.ayaz.viewer.data.NumberLocale
import com.ayaz.viewer.data.ThemeMode
import com.ayaz.viewer.ui.components.AyazCard
import com.ayaz.viewer.ui.components.IconBadge
import com.ayaz.viewer.ui.components.SpacerH
import com.ayaz.viewer.ui.components.SpacerW
import com.ayaz.viewer.ui.icons.AyazIcons
import com.ayaz.viewer.ui.main.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val usage by vm.storageUsage.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshStorage() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Ayarlar", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }

        item {
            SettingsGroup(AyazIcons.DarkMode, "Tema") {
                listOf(ThemeMode.DARK to "Koyu (varsayılan)", ThemeMode.LIGHT to "Açık", ThemeMode.SYSTEM to "Sistem ayarını izle")
                    .forEach { (mode, label) ->
                        RadioRow(label, selected = settings.themeMode == mode) { vm.updateSettings { it.copy(themeMode = mode) } }
                    }
            }
        }

        item {
            SettingsGroup(AyazIcons.GridOn, "Excel görüntüleme") {
                SwitchRow(
                    "Tabloları her zaman açık renkte göster",
                    "Koyu temada da hücreler beyaz zemin üzerinde gösterilir.",
                    settings.lightSheets,
                ) { v -> vm.updateSettings { it.copy(lightSheets = v) } }
                SpacerH(4)
                Text("Sayı ve tarih biçimi", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                RadioRow("Türkçe (1.234,56 • 05.03.2024)", settings.numberLocale == NumberLocale.TR) {
                    vm.updateSettings { it.copy(numberLocale = NumberLocale.TR) }
                }
                RadioRow("İngilizce (1,234.56)", settings.numberLocale == NumberLocale.EN) {
                    vm.updateSettings { it.copy(numberLocale = NumberLocale.EN) }
                }
            }
        }

        item {
            SettingsGroup(AyazIcons.Pages, "PDF") {
                SwitchRow(
                    "Son görüntülenen sayfayı hatırla",
                    "Belge yeniden açıldığında kaldığınız sayfadan devam eder.",
                    settings.rememberLastPage,
                ) { v -> vm.updateSettings { it.copy(rememberLastPage = v) } }
            }
        }

        item {
            SettingsGroup(AyazIcons.Storage, "Depolama") {
                Text(
                    "Kalıcı erişim verilmeyen dosyaların (ör. WhatsApp'tan açılanlar) uygulama içi güvenli kopyaları: " +
                        if (usage >= 0) Formatters.size(usage) else "hesaplanıyor…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SpacerH(10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::cleanUnusedCopies) { Text("Kullanılmayanları temizle") }
                    OutlinedButton(onClick = vm::clearRecents) { Text("Listeyi temizle") }
                }
            }
        }

        item {
            SettingsGroup(AyazIcons.Shield, "Gizlilik") {
                Text(
                    "Ayaz Viewer tamamen çevrimdışı çalışır. İnternet izni yoktur; reklam, analitik veya kullanıcı " +
                        "takibi içermez. Dosyalarınız hiçbir sunucuya gönderilmez ve değiştirilmez. Paylaşım yalnızca " +
                        "sizin seçtiğiniz WhatsApp veya Telegram uygulamasına yapılır.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsGroup(Icons.Filled.Info, "Hakkında") {
                Text("Ayaz Viewer ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Kişisel kullanım için PDF ve XLSX görüntüleyici.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    AyazCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, MaterialTheme.colorScheme.primary)
                SpacerW(12)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            SpacerH(10)
            content()
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        SpacerW(8)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpacerW(8)
        Switch(checked = checked, onCheckedChange = null)
    }
}
