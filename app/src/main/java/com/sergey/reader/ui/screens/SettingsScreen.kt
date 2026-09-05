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
import androidx.compose.foundation.clickable
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import com.sergey.reader.data.settings.AppAppearance
import com.sergey.reader.BuildConfig
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
            Text("Сделайте чтение своим", style = MaterialTheme.typography.headlineLarge)
            Text("Оформление, голос и ваша библиотека", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            SettingsSection("Оформление приложения", initiallyExpanded = true) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppAppearance.entries.forEach { appearance ->
                        FilterChip(selected = settings.appAppearance == appearance, onClick = { vm.setAppAppearance(appearance) }, label = {
                            Text(when (appearance) { AppAppearance.SYSTEM -> "Системное"; AppAppearance.LIGHT -> "Светлое"; AppAppearance.DARK -> "Тёмное" })
                        })
                    }
                }
                Text("Цвет страницы настраивается отдельно от библиотеки.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsSection("Чтение", initiallyExpanded = true) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Не выключать экран", modifier = Modifier.weight(1f))
                    Switch(settings.keepScreenOn, vm::setKeepScreenOn)
                }

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

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Скрывать панели тапом")
                    Text("Тап по странице переключает полноэкранный режим чтения", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(settings.showControlsOnTap, vm::setShowControlsOnTap)
            }

            }
            SettingsSection("Читать вслух") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Аудиочтение")
                    Text("Можно полностью отключить TTS; активная озвучка остановится", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(settings.ttsEnabled, vm::setTtsEnabled)
            }
            Text("Скорость: ${"%.2f".format(settings.ttsRate)}×")
            Slider(value = settings.ttsRate, onValueChange = vm::setTtsRate, valueRange = 0.1f..4.0f)
            Text("Высота голоса: ${"%.2f".format(settings.ttsPitch)}×")
            Slider(value = settings.ttsPitch, onValueChange = vm::setTtsPitch, valueRange = 0.5f..2.0f)
            Text(
                "Фоновая озвучка работает при выключенном экране. Поддерживаются системные и Bluetooth media-кнопки: play/pause, следующий и предыдущий фрагмент, stop.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            }
            SettingsSection("Словарь и перевод") {
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

            }
            SettingsSection("Резервные копии") {
            Text(
                "Копия содержит библиотеку, прогресс, цитаты, заметки, словарь, профили книг, свои шрифты, обложки и файлы внутренней библиотеки.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { backupLauncher.launch("OmniReader-backup-${BuildConfig.VERSION_NAME}.readerbackup") }) {
                    Text("Создать копию")
                }
                Button(onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                    Text("Восстановить")
                }
            }
            Text(
                "После восстановления полностью закройте и снова откройте приложение. Текущая библиотека будет заменена данными копии.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            }
            SettingsSection("Поддерживаемые форматы") {
            Text("Форматы: EPUB, FB2, FB2.ZIP, FB2.GZ, TXT, PDF, HTML/HTM, Markdown, RTF, DOCX, ODT, MOBI/AZW/AZW3 (без DRM; PalmDOC), ZIP, CBZ, CBR, CB7, CHM и DjVu/DJV. DjVu отображается постранично через нативный DjVuLibre backend. EPUB поддерживает иллюстрации, сноски и переходы из встроенного оглавления.")

            }
            Spacer(Modifier.height(28.dp))
            Text("OmniReader ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge)
            Text(
                "Ваши книги, заметки и место остановки — на вашем устройстве. Для чтения импортированных книг подключение к интернету не требуется.",
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

@Composable
private fun SettingsSection(title: String, initiallyExpanded: Boolean = false, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Свернуть" else "Развернуть") { expanded = !expanded }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (expanded) Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 20.dp)) { content() }
        }
    }
}
