package com.sergey.reader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sergey.reader.AppContainer
import com.sergey.reader.data.db.BookEntity
import com.sergey.reader.ui.document.DocumentViewer
import com.sergey.reader.ui.screens.ReaderScreen
import kotlinx.coroutines.CancellationException

/** Choose the reader BEFORE creating a text ReaderViewModel or loading paragraph blocks. */
@Composable
fun ReadingDestination(container: AppContainer,bookId: Long,initialBlock: Int?,onBack: ()->Unit) {
    var book by remember(bookId){mutableStateOf<BookEntity?>(null)}
    var error by remember(bookId){mutableStateOf<String?>(null)}
    LaunchedEffect(bookId) {
        try { book=container.books.getBook(bookId);if(book==null) error="Книга не найдена" }
        catch(cancelled: CancellationException){throw cancelled}
        catch(failure: Exception){error=failure.message ?: "Не удалось открыть книгу"}
    }
    val current=book
    when {
        error!=null -> Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text(error.orEmpty());TextButton(onClick=onBack){Text("В библиотеку")}}
        current==null -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
        current.format.uppercase() in setOf("PDF","DJVU","DJV") -> key(bookId) { DocumentViewer(container,current,initialBlock,onBack) }
        else -> {
            val vm: ReaderViewModel=viewModel(key="reader_$bookId",factory=AppViewModelFactory(container,AppViewModelFactory.Kind.READER,bookId))
            ReaderScreen(vm,initialBlock,onBack)
        }
    }
}
