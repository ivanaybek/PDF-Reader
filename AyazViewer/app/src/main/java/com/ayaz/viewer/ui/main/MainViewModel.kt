package com.ayaz.viewer.ui.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayaz.viewer.AyazApp
import com.ayaz.viewer.core.ErrorMessages
import com.ayaz.viewer.core.Formatters
import com.ayaz.viewer.data.AppSettings
import com.ayaz.viewer.data.FileOpener
import com.ayaz.viewer.data.GrantedFolder
import com.ayaz.viewer.data.RecentFile
import com.ayaz.viewer.data.UserFacingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MainEvent {
    data class OpenViewer(val id: String) : MainEvent
    data class Message(val text: String) : MainEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AyazApp
    private val opener = FileOpener(app.documents, app.recents)

    val recents: StateFlow<List<RecentFile>> = app.recents.items
    val settings: StateFlow<AppSettings> = app.settings.settings
    val folders: StateFlow<List<GrantedFolder>> = app.folders.folders

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _storageUsage = MutableStateFlow(-1L)
    val storageUsage: StateFlow<Long> = _storageUsage.asStateFlow()

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun openUri(uri: Uri) = runOpen { opener.open(uri) }

    fun openRecent(entry: RecentFile) = runOpen { opener.reopen(entry) }

    private fun runOpen(block: suspend () -> RecentFile) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val entry = block()
                _events.send(MainEvent.OpenViewer(entry.id))
            } catch (e: UserFacingException) {
                _events.send(MainEvent.Message(e.message ?: ErrorMessages.NO_ACCESS))
            } catch (e: Exception) {
                _events.send(MainEvent.Message(ErrorMessages.generic(e)))
            } finally {
                _busy.value = false
            }
        }
    }

    fun remove(entry: RecentFile) {
        viewModelScope.launch {
            app.recents.remove(entry.id)
            _events.send(MainEvent.Message("\"${entry.name}\" listeden kaldırıldı. Dosyanın kendisi silinmedi."))
            refreshStorage()
        }
    }

    fun clearRecents() {
        viewModelScope.launch {
            app.recents.clear()
            _events.send(MainEvent.Message("Son açılanlar listesi temizlendi."))
            refreshStorage()
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = app.settings.update(transform)

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { app.folders.add(uri) }
            _events.send(
                MainEvent.Message(if (added != null) "\"${added.name}\" klasörü eklendi." else "Klasöre erişim izni alınamadı."),
            )
        }
    }

    fun removeFolder(folder: GrantedFolder) {
        app.folders.remove(folder)
        viewModelScope.launch { _events.send(MainEvent.Message("\"${folder.name}\" klasörünün erişimi kaldırıldı.")) }
    }

    fun refreshStorage() {
        viewModelScope.launch(Dispatchers.IO) { _storageUsage.value = app.documents.importedUsageBytes() }
    }

    fun cleanUnusedCopies() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) {
                app.recents.awaitLoaded()
                val ids = app.recents.items.value.filter { it.isPrivateCopy }.map { it.id }.toSet()
                app.documents.removeOrphanCopies(ids)
            }
            refreshStorage()
            _events.send(MainEvent.Message("Temizlendi: ${Formatters.size(freed)}"))
        }
    }

    fun postMessage(text: String) {
        viewModelScope.launch { _events.send(MainEvent.Message(text)) }
    }
}
