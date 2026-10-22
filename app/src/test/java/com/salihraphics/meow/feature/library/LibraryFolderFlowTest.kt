package com.salihraphics.meow.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.testing.FakePdfLibraryRepository
import com.salihraphics.meow.testing.sampleDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Folder create → move → delete, driven through the real library screen and view model. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class LibraryFolderFlowTest {
    @get:Rule val rule = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun createMoveAndDeleteAFolderWithoutLosingThePdf() {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument("a", "Fizik Deneme")) }
        val vm = LibraryViewModel(repo)
        rule.setContent {
            MeowTheme(mascotAnimationsEnabled = false) {
                LibraryRoute(vm, thumbnails = { null }, onOpen = {}, onAnswerKey = {}, onBack = {}, suggestedFolders = listOf("Fizik"))
            }
        }

        // Create a folder (typed names are covered in LibraryViewModelTest: under Robolectric a
        // text field inside a dialog window never lets Compose go idle).
        vm.createFolder("Denemeler")
        rule.waitForIdle()
        assertEquals(listOf("Denemeler"), repo.folderList.value.map { it.name })

        // Move the PDF in through its menu.
        rule.onNodeWithContentDescription("“Fizik Deneme” için seçenekler").performClick()
        rule.onNodeWithText("Klasöre taşı").performClick()
        rule.onAllNodesWithText("Denemeler").fetchSemanticsNodes().also { assertTrue(it.isNotEmpty()) }
        rule.onAllNodesWithText("Denemeler")[rule.onAllNodesWithText("Denemeler").fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()
        assertEquals("f1", repo.current.value.single().folderId)

        // Open the folder and delete it; the PDF goes back to "Klasörsüz", the file stays.
        rule.onAllNodesWithText("Denemeler")[0].performClick()
        rule.onNodeWithText("Sil").performClick()
        rule.onNodeWithText("İçindeki 1 PDF silinmez; Klasörsüz'e taşınır.").assertIsDisplayed()
        rule.onNodeWithText("Klasörü sil, PDF'leri Klasörsüz'e taşı").performClick()
        rule.waitForIdle()
        assertTrue(repo.folderList.value.isEmpty())
        assertNull(repo.current.value.single().folderId)
        assertTrue(repo.pdfFile("a").exists())
        rule.onNodeWithText("Fizik Deneme").assertIsDisplayed()
    }

    @Test
    fun cancellingAFolderDeleteChangesNothing() {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument("a", "Kimya")) }
        val vm = LibraryViewModel(repo)
        rule.setContent {
            MeowTheme(mascotAnimationsEnabled = false) {
                LibraryRoute(vm, thumbnails = { null }, onOpen = {}, onAnswerKey = {}, onBack = {})
            }
        }
        vm.createFolder("Boş")
        rule.waitForIdle()
        rule.onNodeWithText("Boş").performClick()
        rule.onNodeWithText("Bu klasör boş. Bir PDF'in menüsünden “Klasöre taşı”yı seç.").assertIsDisplayed()
        rule.onNodeWithText("Sil").performClick()
        rule.onNodeWithText("Vazgeç").performClick()
        rule.waitForIdle()
        assertEquals(listOf("Boş"), repo.folderList.value.map { it.name })
    }

    @Test
    fun subjectSuggestionsCreateFolders() {
        val repo = FakePdfLibraryRepository(tmp.root).apply { add(sampleDocument("a", "Kimya")) }
        val vm = LibraryViewModel(repo)
        rule.setContent {
            MeowTheme(mascotAnimationsEnabled = false) {
                LibraryRoute(vm, thumbnails = { null }, onOpen = {}, onAnswerKey = {}, onBack = {}, suggestedFolders = listOf("Kimya", "Fizik"))
            }
        }
        rule.onNodeWithText("Derslerimden klasör oluştur: Kimya, Fizik").performClick()
        rule.waitForIdle()
        assertEquals(setOf("Kimya", "Fizik"), repo.folderList.value.map { it.name }.toSet())
    }
}
