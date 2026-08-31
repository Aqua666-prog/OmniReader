package app.omnireader.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.omnireader.android.ui.folders.FoldersScreen
import app.omnireader.android.ui.library.LibraryScreen
import app.omnireader.android.ui.reader.ReaderScreen

@Composable
fun OmniReaderApp(modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "library", modifier = modifier) {
        composable("library") {
            LibraryScreen(
                onManageFolders = { nav.navigate("folders") },
                onOpen = { id -> nav.navigate("reader/$id") },
            )
        }
        composable("folders") { FoldersScreen(onBack = { nav.popBackStack() }) }
        composable("reader/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ReaderScreen(itemId = entry.arguments?.getLong("id") ?: return@composable, onBack = { nav.popBackStack() })
        }
    }
}
