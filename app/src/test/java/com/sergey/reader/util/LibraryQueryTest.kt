package com.sergey.reader.util

import com.sergey.reader.data.db.BookEntity
import com.sergey.reader.data.settings.LibrarySort
import com.sergey.reader.model.LibrarySection
import org.junit.Assert.*
import org.junit.Test

class LibraryQueryTest {
    private fun book(id: Long, title: String = "Книга $id") = BookEntity(id = id, uri = "file:$id", displayName = "$title.epub", title = title, format = "EPUB", addedAt = id)
    private fun select(books: List<BookEntity>, query: String = "", section: LibrarySection = LibrarySection.ALL, sort: LibrarySort = LibrarySort.RECENT, group: Pair<LibrarySection, String>? = null) =
        LibraryQuery.select(books, section, group, query, sort)

    @Test fun currentShelfDoesNotTruncateUnopenedBooks() {
        assertEquals(40, select((1L..40L).map { book(it) }, section = LibrarySection.CURRENT).size)
    }
    @Test fun currentShelfExcludesFinishedBooks() {
        assertEquals(listOf(2L), select(listOf(book(1).copy(finished = true), book(2)), section = LibrarySection.CURRENT).map { it.id })
    }
    @Test fun searchesAllTokensAcrossTitleAndAuthorIgnoringYo() {
        val books = listOf(book(1, "Мёртвые души").copy(authors = "Николай Гоголь"), book(2, "Души"))
        assertEquals(listOf(1L), select(books, "  ГОГОЛЬ   мертвые ").map { it.id })
    }
    @Test fun searchDoesNotEscapeFavoriteFilter() {
        assertTrue(select(listOf(book(1, "История")), "История", LibrarySection.FAVORITES).isEmpty())
    }
    @Test fun recentReadingTakesPrecedenceOverImportDate() {
        assertEquals(listOf(1L, 2L), select(listOf(book(2).copy(addedAt = 100), book(1).copy(lastOpenedAt = 10))).map { it.id })
    }
    @Test fun groupedAuthorsUseFullNames() {
        val books = listOf(book(1).copy(authors = "Лев Толстой; Алексей Толстой"), book(2).copy(authors = "Толстой"))
        assertEquals(listOf(1L), select(books, group = LibrarySection.AUTHORS to "Лев Толстой").map { it.id })
    }
    @Test fun sortingIsDeterministicForMatchingTitles() {
        assertEquals(listOf(1L, 2L), select(listOf(book(2, "Название"), book(1, "Название")), sort = LibrarySort.TITLE).map { it.id })
    }
    @Test fun progressSortsDescending() {
        assertEquals(listOf(2L, 1L), select(listOf(book(1).copy(progress = .2f), book(2).copy(progress = .8f)), sort = LibrarySort.PROGRESS).map { it.id })
    }
    @Test fun unknownFolderUsesVisibleGroupName() {
        assertEquals(1, select(listOf(book(1)), group = LibrarySection.FOLDERS to "Без папки").size)
    }
}
