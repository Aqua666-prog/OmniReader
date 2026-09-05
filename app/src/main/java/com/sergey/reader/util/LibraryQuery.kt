package com.sergey.reader.util

import com.sergey.reader.data.db.BookEntity
import com.sergey.reader.data.settings.LibrarySort
import com.sergey.reader.model.LibrarySection
import java.util.Locale

/** Pure library query logic, shared by both list and grid. No arbitrary book limit. */
object LibraryQuery {
    private fun normalized(text: String) = text.lowercase(Locale.ROOT).replace('ё', 'е')
    private fun authors(text: String) = text.split(',', ';').map(String::trim).filter(String::isNotEmpty)

    fun select(books: List<BookEntity>, section: LibrarySection, group: Pair<LibrarySection, String>?, query: String, sort: LibrarySort): List<BookEntity> {
        val tokens = normalized(query).split(Regex("\\s+")).filter(String::isNotEmpty)
        val filtered = books.filter { book ->
            val inSection = when (section) {
                LibrarySection.CURRENT -> !book.finished
                LibrarySection.FAVORITES -> book.favorite
                LibrarySection.WANT_TO_READ -> book.wantToRead
                LibrarySection.FINISHED -> book.finished
                else -> true
            }
            val inGroup = group?.let { (kind, key) ->
                when (kind) {
                    LibrarySection.AUTHORS -> key in authors(book.authors)
                    LibrarySection.SERIES -> book.series == key
                    LibrarySection.COLLECTIONS -> book.collection == key
                    LibrarySection.FORMATS -> book.format == key
                    LibrarySection.FOLDERS -> (book.folderLabel ?: "Без папки") == key
                    else -> true
                }
            } ?: true
            val haystack = normalized(listOfNotNull(book.title, book.authors, book.series, book.collection, book.displayName, book.format).joinToString(" "))
            inSection && inGroup && tokens.all { it in haystack }
        }
        val comparator = when (sort) {
            LibrarySort.RECENT -> compareByDescending<BookEntity> { it.lastOpenedAt ?: 0L }.thenByDescending { it.addedAt }
            LibrarySort.ADDED -> compareByDescending<BookEntity> { it.addedAt }
            LibrarySort.TITLE -> compareBy<BookEntity> { normalized(it.title) }
            LibrarySort.AUTHOR -> compareBy<BookEntity> { normalized(it.authors) }.thenBy { normalized(it.title) }
            LibrarySort.PROGRESS -> compareByDescending<BookEntity> { it.progress }
        }.thenBy { it.id }
        return filtered.sortedWith(comparator)
    }
}
