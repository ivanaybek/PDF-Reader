package com.ayaz.viewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.data.RecentFile
import com.ayaz.viewer.data.RecentFilesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uygulama kapatılıp yeniden açıldığında son dosyaların korunması (yeni depo örneği = yeniden başlatma). */
@RunWith(AndroidJUnit4::class)
class RecentFilesPersistenceTest {

    /** Takılan bir test, adıyla birlikte başarısız olsun (sonsuza dek beklemesin). */
    @get:org.junit.Rule(order = 0)
    val timeout: org.junit.rules.Timeout = org.junit.rules.Timeout.seconds(120)

    @Test
    fun recentsSurviveRestart() = runBlocking {
        val app = TestFiles.app
        val name = "recents_test.json"
        File(app.filesDir, name).delete()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = RecentFilesRepository(app, app.documents, scope, name)
        first.upsert(RecentFile("a", "content://x/a", "Rapor ğüş.pdf", FileKind.PDF, 1000, 1000))
        first.upsert(RecentFile("b", "content://x/b", "Tablo.xlsx", FileKind.XLSX, 2000, 3000))
        first.upsert(RecentFile("c", "content://x/c", "Eski.pdf", FileKind.PDF, 3000, 2000))
        first.updatePosition("a", page = 17)

        val second = RecentFilesRepository(app, app.documents, scope, name)
        second.awaitLoaded()
        val items = second.items.value
        assertEquals(listOf("b", "c", "a"), items.map { it.id })
        assertEquals("Rapor ğüş.pdf", items.last().name)
        assertEquals(17, items.last().lastPage)

        // Aynı dosya tekrar açılınca öne gelir, sayfa bilgisi korunur.
        second.upsert(RecentFile("a", "content://x/a", "Rapor ğüş.pdf", FileKind.PDF, 1000, 9000))
        assertEquals("a", second.items.value.first().id)
        assertEquals(17, second.items.value.first().lastPage)
    }

    @Test
    fun removingDeletesOnlyPrivateCopy() = runBlocking {
        TestFiles.clearRecents()
        val entry = TestFiles.seedRecent("single_page.pdf", "kopya.pdf", FileKind.PDF)
        val copy = File(android.net.Uri.parse(entry.uri).path!!)
        assertTrue(copy.exists())
        TestFiles.app.recents.remove(entry.id)
        assertFalse("uygulama kopyası silinmeli", copy.exists())
        assertTrue(TestFiles.app.recents.items.value.none { it.id == entry.id })
    }

    @Test
    fun corruptListFileDoesNotCrash() = runBlocking {
        val app = TestFiles.app
        val name = "recents_corrupt.json"
        File(app.filesDir, name).writeText("{bozuk json")
        val repo = RecentFilesRepository(app, app.documents, CoroutineScope(Dispatchers.IO), name)
        repo.awaitLoaded()
        assertTrue(repo.items.value.isEmpty())
    }
}
