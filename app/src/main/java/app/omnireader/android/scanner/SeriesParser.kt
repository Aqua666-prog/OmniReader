package app.omnireader.android.scanner

import app.omnireader.android.core.model.SeriesGuess

object SeriesParser {
    private val patterns = listOf(
        Regex("""(?i)^(.*?)[\s._-]+(?:vol(?:ume)?|v|том|т)\.?[\s._-]*(\d+(?:[.,]\d+)?)\b.*$"""),
        Regex("""(?i)^(.*?)[\s._-]+(?:book|книга)[\s._-]*(\d+(?:[.,]\d+)?)\b.*$"""),
        Regex("""^(.*?)[\s._-]+(\d{1,3})(?:\s*\([^)]*\))?$"""),
        Regex("""^(.*?)[\s._-]+#(\d+(?:[.,]\d+)?)\b.*$"""),
    )

    fun parse(fileName: String, parentFolder: String? = null): SeriesGuess {
        val base = fileName.substringBeforeLast('.', fileName).trim()
        for (pattern in patterns) {
            val match = pattern.find(base) ?: continue
            val series = clean(match.groupValues[1])
            val volume = match.groupValues[2].replace(',', '.').toDoubleOrNull()
            if (series.isNotBlank() && volume != null) {
                return SeriesGuess(title = base, series = series, volume = volume)
            }
        }

        val parent = parentFolder?.trim()?.takeIf { it.isNotBlank() }
        return SeriesGuess(title = base, series = parent)
    }

    private fun clean(value: String): String = value
        .replace(Regex("[._]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '_', '.')
}
