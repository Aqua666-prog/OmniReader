package com.sergey.reader.document

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Streaming next/previous navigation is independent of the bounded result list. Wraps once. */
object DocumentSearchNavigator {
    suspend fun adjacent(
        count: Int,
        query: String,
        current: DocumentSearchHit,
        direction: Int,
        read: suspend (Int) -> DocumentTextPage,
        onUnreadable: (Int) -> Unit = {},
    ): DocumentSearchHit? {
        if (count <= 0 || query.isBlank() || current.page !in 0 until count) return null
        val step = if (direction < 0) -1 else 1
        var failures = 0
        for (distance in 0..count) {
            currentCoroutineContext().ensureActive()
            val pageIndex = Math.floorMod(current.page + distance * step, count)
            val page = try {
                read(pageIndex)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                throw error
            } catch (error: Exception) {
                failures++
                onUnreadable(pageIndex)
                if (failures >= MAX_UNREADABLE_PAGES) throw error
                continue
            }

            val wrappedToCurrentPage = distance == count
            val from = when {
                distance == 0 && step > 0 -> current.end
                distance == 0 -> current.start - 1
                step > 0 -> 0
                else -> page.text.length
            }
            val index = if (step > 0) {
                page.text.indexOf(query, from.coerceAtLeast(0), ignoreCase = true)
            } else if (from < 0) {
                -1
            } else {
                page.text.lastIndexOf(query, from.coerceAtMost(page.text.lastIndex), ignoreCase = true)
            }
            if (index >= 0) {
                // The final iteration is only for wrap-around within the starting page. If it lands
                // on the same hit again, there is no other result in that direction.
                if (wrappedToCurrentPage && index == current.start) return null
                return DocumentSearch.hit(page, index, query.length)
            }
        }
        return null
    }

    private const val MAX_UNREADABLE_PAGES = 10
}
