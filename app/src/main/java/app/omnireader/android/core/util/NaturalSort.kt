package app.omnireader.android.core.util

object NaturalSort {
    val comparator: Comparator<String> = Comparator { a, b -> compare(a, b) }

    fun compare(a: String, b: String): Int {
        var ia = 0
        var ib = 0
        while (ia < a.length && ib < b.length) {
            val ca = a[ia]
            val cb = b[ib]
            if (ca.isDigit() && cb.isDigit()) {
                val sa = ia
                val sb = ib
                while (ia < a.length && a[ia].isDigit()) ia++
                while (ib < b.length && b[ib].isDigit()) ib++
                val na = a.substring(sa, ia).trimStart('0').ifEmpty { "0" }
                val nb = b.substring(sb, ib).trimStart('0').ifEmpty { "0" }
                if (na.length != nb.length) return na.length.compareTo(nb.length)
                val numericCompare = na.compareTo(nb)
                if (numericCompare != 0) return numericCompare
                val rawLenCompare = (ia - sa).compareTo(ib - sb)
                if (rawLenCompare != 0) return rawLenCompare
            } else {
                val diff = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (diff != 0) return diff
                ia++
                ib++
            }
        }
        return (a.length - ia).compareTo(b.length - ib)
    }
}
