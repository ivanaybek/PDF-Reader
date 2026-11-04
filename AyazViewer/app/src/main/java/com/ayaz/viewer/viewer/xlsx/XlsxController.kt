package com.ayaz.viewer.viewer.xlsx

import android.util.Xml
import com.ayaz.viewer.core.ErrorMessages
import com.ayaz.viewer.data.UserFacingException
import com.ayaz.viewer.xlsx.CellFormatter
import com.ayaz.viewer.xlsx.FormatLocale
import com.ayaz.viewer.xlsx.ParseCancelledException
import com.ayaz.viewer.xlsx.SheetData
import com.ayaz.viewer.xlsx.SheetSearch
import com.ayaz.viewer.xlsx.WorkbookData
import com.ayaz.viewer.xlsx.XlsxException
import com.ayaz.viewer.xlsx.XlsxReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

sealed interface SheetLoad {
    val index: Int

    data class Loading(override val index: Int) : SheetLoad
    data class Ready(override val index: Int, val sheet: SheetData, val formatter: CellFormatter) : SheetLoad
    data class Message(override val index: Int, val text: String) : SheetLoad
}

/**
 * Bir XLSX çalışma kitabını açık tutar; sayfaları ihtiyaç anında (seçildiğinde) ayrıştırır.
 * Bellek için en fazla iki ayrıştırılmış sayfa önbellekte tutulur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class XlsxController private constructor(
    private val reader: XlsxReader,
    val workbook: WorkbookData,
    private val file: File,
    private val temporary: Boolean,
    val locale: FormatLocale,
) {
    private val parseDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = object : LinkedHashMap<Int, SheetData>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, SheetData>?): Boolean = size > 2
    }
    private var loadJob: Job? = null

    @Volatile
    private var closed = false

    private val _state = MutableStateFlow<SheetLoad>(SheetLoad.Loading(0))
    val state: StateFlow<SheetLoad> = _state.asStateFlow()

    val sheetCount: Int get() = workbook.sheets.size

    fun select(index: Int) {
        if (index !in workbook.sheets.indices || closed) return
        val current = _state.value
        if (current.index == index && current !is SheetLoad.Loading && loadJob?.isActive != true) return
        loadJob?.cancel()
        val entry = workbook.sheets[index]
        if (!entry.isWorksheet) {
            _state.value = SheetLoad.Message(index, "\"${entry.name}\" bir grafik veya özel sayfa. Bu sayfa türü görüntülenemiyor.")
            return
        }
        synchronized(cache) { cache[index] }?.let {
            _state.value = SheetLoad.Ready(index, it, CellFormatter(workbook, locale))
            return
        }
        _state.value = SheetLoad.Loading(index)
        loadJob = scope.launch {
            val result: SheetLoad = withContext(parseDispatcher) {
                try {
                    val job = coroutineContext[Job]
                    val sheet = reader.readSheet(workbook, index) { job?.isActive != true || closed }
                    synchronized(cache) { cache[index] = sheet }
                    SheetLoad.Ready(index, sheet, CellFormatter(workbook, locale))
                } catch (e: ParseCancelledException) {
                    SheetLoad.Loading(index)
                } catch (e: XlsxException) {
                    SheetLoad.Message(index, ErrorMessages.xlsx(e.reason))
                } catch (e: OutOfMemoryError) {
                    synchronized(cache) { cache.clear() }
                    SheetLoad.Message(index, "Bu sayfa çok büyük olduğu için bellek yetersiz kaldı.")
                } catch (e: Exception) {
                    SheetLoad.Message(index, ErrorMessages.xlsx(XlsxException.Reason.CORRUPT))
                }
            }
            if (isActive) _state.value = result
        }
    }

    /** Hücre araması arka planda yapılır; sonuç (satır shl 32) or sütun dizisidir. */
    suspend fun search(sheet: SheetData, query: String): LongArray = withContext(Dispatchers.Default) {
        val formatter = CellFormatter(workbook, locale)
        val ctx = coroutineContext
        try {
            SheetSearch.find(sheet, formatter, query) { !ctx.isActive }
        } catch (_: ParseCancelledException) {
            ctx.ensureActive()
            LongArray(0)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        loadJob?.cancel()
        scope.cancel()
        CoroutineScope(parseDispatcher).launch {
            reader.close()
            if (temporary) file.delete()
        }
    }

    companion object {
        @Throws(UserFacingException::class)
        fun open(file: File, temporary: Boolean, locale: FormatLocale): XlsxController {
            var reader: XlsxReader? = null
            try {
                reader = XlsxReader(file, { Xml.newPullParser() })
                val wb = reader.readWorkbook()
                if (wb.sheets.isEmpty()) throw XlsxException(XlsxException.Reason.CORRUPT)
                return XlsxController(reader, wb, file, temporary, locale)
            } catch (e: XlsxException) {
                reader?.close()
                if (temporary) file.delete()
                throw UserFacingException(ErrorMessages.xlsx(e.reason), e)
            } catch (e: OutOfMemoryError) {
                reader?.close()
                if (temporary) file.delete()
                throw UserFacingException(ErrorMessages.OUT_OF_MEMORY, e)
            } catch (e: Exception) {
                reader?.close()
                if (temporary) file.delete()
                throw UserFacingException(ErrorMessages.xlsx(XlsxException.Reason.CORRUPT), e)
            }
        }
    }
}
