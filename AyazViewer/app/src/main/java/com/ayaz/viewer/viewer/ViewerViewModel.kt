package com.ayaz.viewer.viewer

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayaz.viewer.AyazApp
import com.ayaz.viewer.core.ErrorMessages
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.data.FileOpener
import com.ayaz.viewer.data.NumberLocale
import com.ayaz.viewer.data.RecentFile
import com.ayaz.viewer.data.UserFacingException
import com.ayaz.viewer.viewer.pdf.PdfController
import com.ayaz.viewer.viewer.xlsx.XlsxController
import com.ayaz.viewer.xlsx.FormatLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ViewerState {
    data class Loading(val message: String) : ViewerState
    data class Error(val message: String, val file: RecentFile? = null) : ViewerState
    data class Pdf(val file: RecentFile, val controller: PdfController) : ViewerState
    data class Xlsx(val file: RecentFile, val controller: XlsxController) : ViewerState
}

class ViewerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AyazApp
    private val opener = FileOpener(app.documents, app.recents)
    private var handled = false

    private val _state = MutableStateFlow<ViewerState>(ViewerState.Loading("Dosya açılıyor…"))
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    val settings = app.settings.settings

    /** Etkinlik ilk oluşturulduğunda bir kez çağrılır; döndürmede ViewModel korunur. */
    fun handle(intent: Intent) {
        if (handled) return
        handled = true
        viewModelScope.launch {
            try {
                val entry = resolve(intent)
                _state.value = ViewerState.Loading("Belge hazırlanıyor…")
                _state.value = openController(entry)
            } catch (e: UserFacingException) {
                _state.value = ViewerState.Error(e.message ?: ErrorMessages.NO_ACCESS)
            } catch (e: OutOfMemoryError) {
                _state.value = ViewerState.Error(ErrorMessages.OUT_OF_MEMORY)
            } catch (e: Exception) {
                _state.value = ViewerState.Error(ErrorMessages.generic(e))
            }
        }
    }

    private suspend fun resolve(intent: Intent): RecentFile {
        intent.getStringExtra(EXTRA_RECENT_ID)?.let { id ->
            val entry = app.recents.get(id) ?: throw UserFacingException("Dosya kaydı bulunamadı.")
            return entry
        }
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            else -> intent.data
        }
        if (uri == null) throw UserFacingException("Açılacak dosya bulunamadı.")
        _state.value = ViewerState.Loading("Dosya alınıyor…")
        return opener.open(uri, intent.type)
    }

    private suspend fun openController(entry: RecentFile): ViewerState = withContext(Dispatchers.IO) {
        val uri = Uri.parse(entry.uri)
        when (entry.kind) {
            FileKind.PDF -> ViewerState.Pdf(
                entry,
                PdfController.open(
                    context = app,
                    openFd = { app.documents.openFd(uri) },
                    fallbackCopy = { app.documents.materialize(uri, "pdf").first },
                    sizeHint = entry.size,
                ),
            )
            FileKind.XLSX -> {
                val (file, temporary) = try {
                    app.documents.materialize(uri, "xlsx")
                } catch (e: SecurityException) {
                    throw UserFacingException(ErrorMessages.NO_ACCESS, e)
                } catch (e: java.io.IOException) {
                    throw UserFacingException(ErrorMessages.generic(e), e)
                }
                val locale = if (app.settings.settings.value.numberLocale == NumberLocale.EN) FormatLocale.ENGLISH else FormatLocale.TURKISH
                ViewerState.Xlsx(entry, XlsxController.open(file, temporary, locale))
            }
            FileKind.UNKNOWN -> throw UserFacingException(ErrorMessages.UNSUPPORTED)
        }
    }

    fun savePdfPage(entry: RecentFile, page: Int) {
        if (!app.settings.settings.value.rememberLastPage) return
        viewModelScope.launch(NonCancellable) { app.recents.updatePosition(entry.id, page = page) }
    }

    fun saveSheet(entry: RecentFile, sheet: Int) {
        viewModelScope.launch(NonCancellable) { app.recents.updatePosition(entry.id, sheet = sheet) }
    }

    fun toggleLightSheets() = app.settings.update { it.copy(lightSheets = !it.lightSheets) }

    fun initialPage(entry: RecentFile): Int = if (app.settings.settings.value.rememberLastPage) entry.lastPage else 0

    override fun onCleared() {
        when (val s = _state.value) {
            is ViewerState.Pdf -> s.controller.close()
            is ViewerState.Xlsx -> s.controller.close()
            else -> {}
        }
        super.onCleared()
    }

    companion object {
        const val EXTRA_RECENT_ID = "com.ayaz.viewer.extra.RECENT_ID"
    }
}
