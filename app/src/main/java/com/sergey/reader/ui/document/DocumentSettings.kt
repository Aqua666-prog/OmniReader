package com.sergey.reader.ui.document

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sergey.reader.document.*

@Composable
fun DocumentSettings(options: DocumentOptions,onChange: (DocumentOptions)->Unit) {
    Text("Режим просмотра",style=MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        DocumentMode.entries.forEach { mode -> FilterChip(selected=options.mode==mode,onClick={onChange(options.copy(mode=mode))},label={Text(modeLabel(mode))}) }
    }
    Spacer(Modifier.height(12.dp))
    Text("Масштаб при открытии",style=MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        DocumentOpeningScale.entries.forEach { mode -> FilterChip(selected=options.openingScale==mode,onClick={onChange(options.copy(openingScale=mode))},label={Text(when(mode){DocumentOpeningScale.WIDTH->"По ширине";DocumentOpeningScale.PAGE->"Вся страница";DocumentOpeningScale.LAST->"Последний"})}) }
    }
    Text("Максимальное увеличение",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=12.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(10.0,15.0).forEach { zoom -> FilterChip(selected=options.maxZoom==zoom,onClick={onChange(options.copy(maxZoom=zoom))},label={Text("${zoom.toInt()}×")}) }
    }
    DocumentToggle("Двойной тап для увеличения",options.doubleTapZoom){onChange(options.copy(doubleTapZoom=it))}
    DocumentToggle("Скрывать панели тапом",options.tapControls){onChange(options.copy(tapControls=it))}
    DocumentToggle("Сохранять масштаб",options.saveZoom){onChange(options.copy(saveZoom=it))}
    DocumentToggle("Высококачественный zoom",options.highQuality){onChange(options.copy(highQuality=it))}
    Text("Чёткость сканов ограничена разрешением оригинала. Для страниц без текстового слоя OCR запускается автоматически и работает офлайн.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun DocumentToggle(title: String,value: Boolean,onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(title,Modifier.weight(1f)); Switch(value,onChange) }
}
fun modeLabel(mode: DocumentMode) = when(mode) {DocumentMode.CONTINUOUS->"Вертикальная лента";DocumentMode.PAGE->"По страницам";DocumentMode.SPREAD->"Разворот"}
