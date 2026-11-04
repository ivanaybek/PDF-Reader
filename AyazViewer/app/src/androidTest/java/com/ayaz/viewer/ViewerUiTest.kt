package com.ayaz.viewer

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.viewer.ViewerActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ViewerUiTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private fun waitDesc(desc: String, timeout: Long = 20_000) =
        rule.waitUntil(timeout) { rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty() }

    private fun waitText(text: String, timeout: Long = 20_000) =
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun pdfOpensRotatesAndShares() {
        val entry = TestFiles.seedRecent("multi_page.pdf", "Çok Sayfalı.pdf", FileKind.PDF)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use { scenario ->
            waitDesc("Sayfa 1 / 30")
            waitText("30 sayfa")
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            rule.waitForIdle()
            waitText("30 sayfa")
            waitDesc("Sayfa 1 / 30")
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Paylaş").performClick()
            waitText("WhatsApp")
            rule.onNodeWithText("Telegram").assertExists()
        }
    }

    @Test
    fun pdfGoToPageAndRememberLastPage() {
        val entry = TestFiles.seedRecent("multi_page.pdf", "Hatırla.pdf", FileKind.PDF)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use {
            waitDesc("Sayfa 1 / 30")
            rule.onNodeWithContentDescription("Diğer").performClick()
            rule.onNodeWithText("Sayfaya git").performClick()
            rule.onNode(hasSetTextAction()).performTextInput("2")
            rule.onNodeWithText("Git").performClick()
            waitDesc("Sayfa 12 / 30")
            Thread.sleep(1500) // sayfa kaydı (debounce)
        }
        val saved = runBlocking { TestFiles.app.recents.get(entry.id) }
        assertTrue("kaydedilen sayfa: ${saved?.lastPage}", saved?.lastPage == 11)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use {
            waitDesc("Sayfa 12 / 30")
        }
    }

    @Test
    fun xlsxOpensSearchesAndRotates() {
        val entry = TestFiles.seedRecent("simple.xlsx", "Satış Tablosu.xlsx", FileKind.XLSX)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use { scenario ->
            waitDesc("Tablo: Satışlar")
            rule.onNodeWithContentDescription("Hücrelerde ara").performClick()
            rule.onNode(hasSetTextAction()).performTextInput("Çay")
            rule.onNode(hasSetTextAction()).performImeAction()
            waitDesc("Seçili hücre A2")
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            rule.waitForIdle()
            waitDesc("Tablo: Satışlar")
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }

    @Test
    fun xlsxMultipleSheetTabs() {
        val entry = TestFiles.seedRecent("multi_sheet.xlsx", "Çok Sayfa.xlsx", FileKind.XLSX)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use {
            waitDesc("Tablo: Özet")
            rule.onNodeWithText("Veriler").performClick()
            waitDesc("Tablo: Veriler")
            rule.onNodeWithText("Boş Sayfa").performClick()
            waitText("Bu sayfa boş")
            rule.onNodeWithText("Gizli (gizli)").assertExists()
        }
    }

    @Test
    fun openWithFromAnotherAppAndCorruptFile() {
        val app = TestFiles.app
        val dir = File(app.cacheDir, "share/test-view").apply { mkdirs() }
        val good = TestFiles.asset("single_page.pdf", File(dir, "Dışarıdan Gelen.pdf"))
        val view = Intent(Intent.ACTION_VIEW).apply {
            setClass(app, ViewerActivity::class.java)
            setDataAndType(app.documents.contentUriFor(good), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ActivityScenario.launch<ViewerActivity>(view).use {
            waitDesc("Sayfa 1 / 1")
        }
        assertTrue(app.recents.items.value.any { it.name == "Dışarıdan Gelen.pdf" && it.isPrivateCopy })

        val bad = TestFiles.asset("corrupt.pdf", File(dir, "bozuk.pdf"))
        val badIntent = Intent(Intent.ACTION_VIEW).apply {
            setClass(app, ViewerActivity::class.java)
            setDataAndType(app.documents.contentUriFor(bad), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ActivityScenario.launch<ViewerActivity>(badIntent).use {
            waitText("Dosya açılamadı")
            rule.onNodeWithText("Kapat").assertExists()
        }

        val legacy = TestFiles.asset("legacy.xls", File(dir, "eski.xls"))
        val send = Intent(Intent.ACTION_SEND).apply {
            setClass(app, ViewerActivity::class.java)
            type = "application/vnd.ms-excel"
            putExtra(Intent.EXTRA_STREAM, app.documents.contentUriFor(legacy))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ActivityScenario.launch<ViewerActivity>(send).use {
            waitText("Dosya açılamadı")
        }
    }
}
