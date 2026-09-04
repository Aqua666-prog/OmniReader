package com.sergey.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sergey.reader.AppContainer
import com.sergey.reader.ui.screens.DetailsScreen
import com.sergey.reader.ui.screens.LibraryScreen
import com.sergey.reader.ui.screens.ReaderScreen
import com.sergey.reader.ui.screens.ResearchScreen
import com.sergey.reader.ui.screens.SettingsScreen
import com.sergey.reader.ui.theme.ReaderAppTheme

private enum class Route { LIBRARY, READER, DETAILS, RESEARCH, SETTINGS }

@Composable
fun ReaderApp(container: AppContainer) {
    ReaderAppTheme {
        var route by rememberSaveable { mutableStateOf(Route.LIBRARY.name) }
        var bookId by rememberSaveable { mutableLongStateOf(0L) }
        var readerStartBlock by rememberSaveable { mutableIntStateOf(-1) }

        val libraryVm: LibraryViewModel = viewModel(
            key = "library",
            factory = AppViewModelFactory(container, AppViewModelFactory.Kind.LIBRARY)
        )

        val current = Route.valueOf(route)
        BackHandler(enabled = current != Route.LIBRARY) { route = Route.LIBRARY.name }

        when (current) {
            Route.LIBRARY -> LibraryScreen(
                vm = libraryVm,
                onOpenBook = { id ->
                    bookId = id
                    readerStartBlock = -1
                    route = Route.READER.name
                },
                onDetails = { id ->
                    bookId = id
                    route = Route.DETAILS.name
                },
                onResearch = { route = Route.RESEARCH.name },
                onSettings = { route = Route.SETTINGS.name }
            )

            Route.READER -> {
                val vm: ReaderViewModel = viewModel(
                    key = "reader_$bookId",
                    factory = AppViewModelFactory(container, AppViewModelFactory.Kind.READER, bookId)
                )
                ReaderScreen(
                    vm = vm,
                    initialBlock = readerStartBlock.takeIf { it >= 0 },
                    onBack = { route = Route.LIBRARY.name }
                )
            }

            Route.DETAILS -> {
                val vm: DetailsViewModel = viewModel(
                    key = "details_$bookId",
                    factory = AppViewModelFactory(container, AppViewModelFactory.Kind.DETAILS, bookId)
                )
                DetailsScreen(
                    vm = vm,
                    onBack = { route = Route.LIBRARY.name },
                    onRead = { id ->
                        bookId = id
                        readerStartBlock = -1
                        route = Route.READER.name
                    }
                )
            }

            Route.RESEARCH -> {
                val vm: ResearchViewModel = viewModel(
                    key = "research",
                    factory = AppViewModelFactory(container, AppViewModelFactory.Kind.RESEARCH)
                )
                ResearchScreen(
                    vm = vm,
                    onBack = { route = Route.LIBRARY.name },
                    onOpenBook = { id, block ->
                        bookId = id
                        readerStartBlock = block
                        route = Route.READER.name
                    }
                )
            }

            Route.SETTINGS -> SettingsScreen(libraryVm, onBack = { route = Route.LIBRARY.name })
        }
    }
}
