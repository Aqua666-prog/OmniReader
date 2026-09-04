package com.sergey.reader.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sergey.reader.data.settings.ContextMenuMode
import com.sergey.reader.data.settings.ReaderMode
import com.sergey.reader.data.settings.ReaderSettings
import com.sergey.reader.data.settings.ReaderThemePreset
import com.sergey.reader.ui.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: LibraryViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val customFonts by vm.customFonts.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var translator by remember(settings.translatorUrlTemplate) { mutableStateOf(settings.translatorUrlTemplate) }
    var dictionary by remember(settings.dictionaryUrlTemplate) { mutableStateOf(settings.dictionaryUrlTemplate) }
    var webSearch by remember(settings.webSearchUrlTemplate) { mutableStateOf(settings.webSearchUrlTemplate) }

    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFont)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::exportBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::stageRestore)
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Text("Чтение", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))

            Text("Режим по умолчанию", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.readerMode == ReaderMode.VERTICAL,
                    onClick = { vm.setReaderMode(ReaderMode.VERTICAL) },
                    label = { Text("Прокрутка") }
                )
                FilterChip(
                    selected = settings.readerMode == ReaderMode.PAGED,
                    onClick = { vm.setReaderMode(ReaderMode.PAGED) },
                    label = { Text("Страницы") }
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Шрифт по умолчанию", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = settings.fontPath == null,
                    onClick = { vm.setFontPath(null) },
                    label = { Text("Serif") }
                )
                customFonts.forEach { font ->
                    FilterChip(
                        selected = settings.fontPath == font.path,
                        onClick = { vm.setFontPath(font.path) },
                        label = { Text(font.name) }
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        fontLauncher.launch(
                            arrayOf(
                                "font/ttf",
                                "font/otf",
                                "application/x-font-ttf",
                                "application/x-font-opentype",
                                "application/octet-stream"
                            )
                        )
                    }
                ) { Text("Добавить .ttf/.otf") }
                val selectedPath = settings.fontPath
                if (selectedPath != null) {
                    TextButton(onClick = { vm.deleteFont(selectedPath) }) { Text("Удалить выбранный") }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Размер шрифта по умолчанию: ${settings.fontSizeSp.toInt()}")
            Slider(settings.fontSizeSp, vm::setFontSize, valueRange = 14f..42f)
            Text("Межстрочный интервал: ${"%.2f".format(settings.lineHeight)}")
            Slider(settings.lineHeight, vm::setLineHeight, valueRange = 1f..2f)
            Text("Поля страницы: ${settings.horizontalPaddingDp.toInt()} dp")
            Slider(settings.horizontalPaddingDp, vm::setPadding, valueRange = 8f..52f)

            Spacer(Modifier.height(12.dp))
            Text("Цветовая схема", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReaderThemePreset.entries.forEach { theme ->
                    FilterChip(
                        selected = settings.theme == theme,
                        onClick = { vm.setTheme(theme) },
                        label = { Text(themeLabelForSettings(theme)) }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Выравнивание по ширине")
                    Text(
                        "Для художественного текста",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(settings.justify, vm::setJustify)
            }

            Spacer(Modifier.height(28.dp))
            Text("Озвучка (TTS)", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("Скорость: ${"%.2f".format(settings.ttsRate)}×")
            Slider(value = settings.ttsRate, onValueChange = vm::setTtsRate, valueRange = 0.1f..4.0f)
            Text("Высота голоса: ${"%.2f".format(settings.ttsPitch)}×")
            Slider(value = settings.ttsPitch, onValueChange = vm::setTtsPitch, valueRange = 0.5f..2.0f)
            Text(
                "Фоновая озвучка работает при выключенном экране. Поддерживаются системные и Bluetooth media-кнопки: play/pause, следующий и предыдущий фрагмент, stop.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))
            Text("Инструменты текста", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("Контекстное меню", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.contextMenuMode == ContextMenuMode.SIMPLE,
                    onClick = { vm.setContextMenuMode(ContextMenuMode.SIMPLE) },
                    label = { Text("Простое") }
                )
                FilterChip(
                    selected = settings.contextMenuMode == ContextMenuMode.EXTENDED,
                    onClick = { vm.setContextMenuMode(ContextMenuMode.EXTENDED) },
                    label = { Text("Расширенное") }
                )
            }
            Text(
                if (settings.contextMenuMode == ContextMenuMode.SIMPLE)
                    "Копировать, Цитата, Ещё"
                else
                    "Копировать, Цитата, Выделить, Заметка, Словарь, Перевод, Ещё",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "В URL-шаблоне {text} заменяется выделенным и безопасно кодируется. Можно подставить другой поисковик, словарь или переводчик.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            UrlTemplateEditor(
                title = "Переводчик",
                value = translator,
                onValueChange = { translator = it },
                onSave = { vm.setTranslatorTemplate(translator) },
                onReset = {
                    translator = ReaderSettings.DEFAULT_TRANSLATOR_TEMPLATE
                    vm.setTranslatorTemplate(translator)
                }
            )

            Spacer(Modifier.height(14.dp))
            UrlTemplateEditor(
                title = "Словарь",
                value = dictionary,
                onValueChange = { dictionary = it },
                onSave = { vm.setDictionaryTemplate(dictionary) },
                onReset = {
                    dictionary = ReaderSettings.DEFAULT_DICTIONARY_TEMPLATE
                    vm.setDictionaryTemplate(dictionary)
                }
            )

            Spacer(Modifier.height(14.dp))
            UrlTemplateEditor(
                title = "Веб-поиск",
                value = webSearch,
                onValueChange = { webSearch = it },
                onSave = { vm.setWebSearchTemplate(webSearch) },
                onReset = {
                    webSearch = ReaderSettings.DEFAULT_WEB_SEARCH_TEMPLATE
                    vm.setWebSearchTemplate(webSearch)
                }
            )

            Spacer(Modifier.height(28.dp))
            Text("Резервные копии", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "Копия содержит локальную базу Reader, прогресс, цитаты, заметки, словарь, профили книг, свои шрифты, обложки и файлы внутренней библиотеки.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { backupLauncher.launch("Reader-backup-0.4.0.readerbackup") }) {
                    Text("Создать копию")
                }
                Button(onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                    Text("Восстановить")
                }
            }
            Text(
                "Восстановление применяется при следующем полном запуске приложения: это защищает живую базу Room от подмены во время работы.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(28.dp))
            Text("Импорт", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("Поддержка текущей версии: EPUB, FB2, TXT и PDF. EPUB отображает встроенные иллюстрации, строки таблиц и распознанные сноски. На Android 15+ PDF дополнительно индексирует системный текстовый слой для поиска, TTS и цитирования через режим «Текст страницы».")

            Spacer(Modifier.height(28.dp))
            Text("Reader 0.4.0", style = MaterialTheme.typography.labelLarge)
            Text(
                "В этой версии: типографически точная пагинация Compose с сохранением смещения, PDF text layer (Android 15+), подсветка текущего фрагмента TTS, таймер сна, TXT/HTML-экспорт и улучшенный разбор EPUB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UrlTemplateEditor(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        supportingText = { Text("Нужен маркер {text}") }
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onReset) { Text("Сбросить") }
        TextButton(onClick = onSave) { Text("Сохранить") }
    }
}

private fun themeLabelForSettings(theme: ReaderThemePreset): String = when (theme) {
    ReaderThemePreset.DAY -> "День"
    ReaderThemePreset.SEPIA -> "Сепия"
    ReaderThemePreset.TWILIGHT -> "Сумерки"
    ReaderThemePreset.NIGHT -> "Ночь"
    ReaderThemePreset.AMOLED -> "AMOLED"
}
