package com.sergey.reader.util

object SeriesInference {
    private val patterns = listOf(
        Regex("(?iu)^(.*?)[\\s._-]*(?:том|т\\.?|vol(?:ume)?)[\\s._-]*(\\d+(?:[.,]\\d+)?)\\s*$"),
        Regex("(?iu)^(.*?)[\\s._-]+(?:book|книга)[\\s._-]*(\\d+(?:[.,]\\d+)?)\\s*$"),
        Regex("^(.*?)[\\s._-]+#?(\\d{1,3})\\s*$")
    )

    data class Result(val series: String?, val index: Double?)

    fun infer(title: String): Result {
        val clean = title.trim().replace(Regex("\\s+"), " ")
        for (pattern in patterns) {
            val match = pattern.matchEntire(clean) ?: continue
            val series = match.groupValues[1].trim(' ', '-', '_', '.', '№', '#')
            val index = match.groupValues[2].replace(',', '.').toDoubleOrNull()
            if (series.length >= 2 && index != null) return Result(series, index)
        }
        return Result(null, null)
    }
}
