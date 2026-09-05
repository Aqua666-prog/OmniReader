package com.sergey.reader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore by preferencesDataStore(name = "reader_settings")

enum class ReaderThemePreset { DAY, SEPIA, TWILIGHT, NIGHT, AMOLED }
enum class LibraryViewMode { LIST, GRID }
enum class ContextMenuMode { SIMPLE, EXTENDED }
enum class AppAppearance { SYSTEM, LIGHT, DARK }
enum class LibrarySort { RECENT, TITLE, AUTHOR, ADDED, PROGRESS }
enum class ReaderMode { VERTICAL, PAGED }

data class ReaderSettings(
    val fontSizeSp: Float = 22f,
    val lineHeight: Float = 1.38f,
    val horizontalPaddingDp: Float = 28f,
    val theme: ReaderThemePreset = ReaderThemePreset.SEPIA,
    val justify: Boolean = true,
    val showControlsOnTap: Boolean = true,
    val libraryViewMode: LibraryViewMode = LibraryViewMode.GRID,
    val librarySort: LibrarySort = LibrarySort.RECENT,
    val appAppearance: AppAppearance = AppAppearance.SYSTEM,
    val keepScreenOn: Boolean = true,
    val brightness: Float = -1f,
    val translatorUrlTemplate: String = DEFAULT_TRANSLATOR_TEMPLATE,
    val dictionaryUrlTemplate: String = DEFAULT_DICTIONARY_TEMPLATE,
    val webSearchUrlTemplate: String = DEFAULT_WEB_SEARCH_TEMPLATE,
    val contextMenuMode: ContextMenuMode = ContextMenuMode.EXTENDED,
    val ttsEnabled: Boolean = true,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val fontPath: String? = null,
    val readerMode: ReaderMode = ReaderMode.VERTICAL
) {
    companion object {
        const val DEFAULT_TRANSLATOR_TEMPLATE = "https://translate.google.com/?sl=auto&tl=ru&text={text}&op=translate"
        const val DEFAULT_DICTIONARY_TEMPLATE = "https://www.google.com/search?q=define%3A{text}"
        const val DEFAULT_WEB_SEARCH_TEMPLATE = "https://www.google.com/search?q={text}"
    }
}

class ReaderSettingsRepository(private val context: Context) {
    private object Keys {
        val librarySort = stringPreferencesKey("library_sort")
        val appAppearance = stringPreferencesKey("app_appearance")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val brightness = floatPreferencesKey("reader_brightness")
        val fontSize = floatPreferencesKey("font_size")
        val lineHeight = floatPreferencesKey("line_height")
        val padding = floatPreferencesKey("padding")
        val theme = stringPreferencesKey("theme")
        val justify = booleanPreferencesKey("justify")
        val controls = booleanPreferencesKey("controls_on_tap")
        val libraryView = stringPreferencesKey("library_view")
        val translatorUrl = stringPreferencesKey("translator_url_template")
        val dictionaryUrl = stringPreferencesKey("dictionary_url_template")
        val webSearchUrl = stringPreferencesKey("web_search_url_template")
        val contextMenuMode = stringPreferencesKey("context_menu_mode")
        val ttsEnabled = booleanPreferencesKey("tts_enabled")
        val ttsRate = floatPreferencesKey("tts_rate")
        val ttsPitch = floatPreferencesKey("tts_pitch")
        val fontPath = stringPreferencesKey("font_path")
        val readerMode = stringPreferencesKey("reader_mode")
    }

    val settings: Flow<ReaderSettings> = context.readerDataStore.data.map { p ->
        ReaderSettings(
            fontSizeSp = p[Keys.fontSize] ?: 22f,
            lineHeight = p[Keys.lineHeight] ?: 1.38f,
            horizontalPaddingDp = p[Keys.padding] ?: 28f,
            theme = p[Keys.theme]?.let { runCatching { ReaderThemePreset.valueOf(it) }.getOrNull() } ?: ReaderThemePreset.SEPIA,
            justify = p[Keys.justify] ?: true,
            showControlsOnTap = p[Keys.controls] ?: true,
            libraryViewMode = p[Keys.libraryView]?.let { runCatching { LibraryViewMode.valueOf(it) }.getOrNull() } ?: LibraryViewMode.GRID,
            librarySort = p[Keys.librarySort]?.let { runCatching { LibrarySort.valueOf(it) }.getOrNull() } ?: LibrarySort.RECENT,
            appAppearance = p[Keys.appAppearance]?.let { runCatching { AppAppearance.valueOf(it) }.getOrNull() } ?: AppAppearance.SYSTEM,
            keepScreenOn = p[Keys.keepScreenOn] ?: true,
            brightness = p[Keys.brightness]?.takeIf { it.isFinite() }?.let { if (it < 0) -1f else it.coerceIn(0.02f, 1f) } ?: -1f,
            translatorUrlTemplate = p[Keys.translatorUrl] ?: ReaderSettings.DEFAULT_TRANSLATOR_TEMPLATE,
            dictionaryUrlTemplate = p[Keys.dictionaryUrl] ?: ReaderSettings.DEFAULT_DICTIONARY_TEMPLATE,
            webSearchUrlTemplate = p[Keys.webSearchUrl] ?: ReaderSettings.DEFAULT_WEB_SEARCH_TEMPLATE,
            contextMenuMode = p[Keys.contextMenuMode]?.let { runCatching { ContextMenuMode.valueOf(it) }.getOrNull() } ?: ContextMenuMode.EXTENDED,
            ttsEnabled = p[Keys.ttsEnabled] ?: true,
            ttsRate = p[Keys.ttsRate] ?: 1.0f,
            ttsPitch = p[Keys.ttsPitch] ?: 1.0f,
            fontPath = p[Keys.fontPath]?.takeIf { it.isNotBlank() },
            readerMode = p[Keys.readerMode]?.let { runCatching { ReaderMode.valueOf(it) }.getOrNull() } ?: ReaderMode.VERTICAL
        )
    }

    suspend fun setLibrarySort(value: LibrarySort) { context.readerDataStore.edit { it[Keys.librarySort] = value.name } }
    suspend fun setAppAppearance(value: AppAppearance) { context.readerDataStore.edit { it[Keys.appAppearance] = value.name } }
    suspend fun setKeepScreenOn(value: Boolean) { context.readerDataStore.edit { it[Keys.keepScreenOn] = value } }
    suspend fun setBrightness(value: Float) { context.readerDataStore.edit { it[Keys.brightness] = if (!value.isFinite() || value < 0) -1f else value.coerceIn(0.02f, 1f) } }
    suspend fun setFontSize(value: Float) { context.readerDataStore.edit { it[Keys.fontSize] = value.coerceIn(14f, 42f) } }
    suspend fun setLineHeight(value: Float) { context.readerDataStore.edit { it[Keys.lineHeight] = value.coerceIn(1.0f, 2.0f) } }
    suspend fun setPadding(value: Float) { context.readerDataStore.edit { it[Keys.padding] = value.coerceIn(8f, 52f) } }
    suspend fun setTheme(value: ReaderThemePreset) { context.readerDataStore.edit { it[Keys.theme] = value.name } }
    suspend fun setJustify(value: Boolean) { context.readerDataStore.edit { it[Keys.justify] = value } }
    suspend fun setShowControlsOnTap(value: Boolean) { context.readerDataStore.edit { it[Keys.controls] = value } }
    suspend fun setLibraryView(value: LibraryViewMode) { context.readerDataStore.edit { it[Keys.libraryView] = value.name } }
    suspend fun setFontPath(value: String?) {
        context.readerDataStore.edit {
            if (value.isNullOrBlank()) it.remove(Keys.fontPath) else it[Keys.fontPath] = value
        }
    }
    suspend fun setReaderMode(value: ReaderMode) { context.readerDataStore.edit { it[Keys.readerMode] = value.name } }

    suspend fun setTranslatorUrlTemplate(value: String) {
        context.readerDataStore.edit { it[Keys.translatorUrl] = sanitizeTemplate(value, ReaderSettings.DEFAULT_TRANSLATOR_TEMPLATE) }
    }

    suspend fun setDictionaryUrlTemplate(value: String) {
        context.readerDataStore.edit { it[Keys.dictionaryUrl] = sanitizeTemplate(value, ReaderSettings.DEFAULT_DICTIONARY_TEMPLATE) }
    }

    suspend fun setWebSearchUrlTemplate(value: String) {
        context.readerDataStore.edit { it[Keys.webSearchUrl] = sanitizeTemplate(value, ReaderSettings.DEFAULT_WEB_SEARCH_TEMPLATE) }
    }

    suspend fun setContextMenuMode(value: ContextMenuMode) {
        context.readerDataStore.edit { it[Keys.contextMenuMode] = value.name }
    }

    suspend fun setTtsEnabled(value: Boolean) {
        context.readerDataStore.edit { it[Keys.ttsEnabled] = value }
    }

    suspend fun setTtsRate(value: Float) {
        context.readerDataStore.edit { it[Keys.ttsRate] = value.coerceIn(0.1f, 4.0f) }
    }

    suspend fun setTtsPitch(value: Float) {
        context.readerDataStore.edit { it[Keys.ttsPitch] = value.coerceIn(0.5f, 2.0f) }
    }

    private fun sanitizeTemplate(value: String, fallback: String): String {
        val clean = value.trim()
        return if (clean.startsWith("http://") || clean.startsWith("https://")) {
            if (clean.contains("{text}")) clean else "$clean{text}"
        } else fallback
    }
}
