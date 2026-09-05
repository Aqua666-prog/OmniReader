package com.sergey.reader.document

/** Safe actionable messages. Native exception details are not exposed as unreadable UI text. */
object DocumentErrors {
    fun message(error: Throwable): String = when {
        error is SecurityException -> "Нет доступа к файлу. Выберите документ заново в библиотеке."
        error is java.io.FileNotFoundException -> "Файл перемещён, удалён или недоступен. Выберите его заново."
        error is OutOfMemoryError -> "Недостаточно памяти. Закройте другие приложения и повторите попытку."
        error is LinkageError -> "Не удалось загрузить нативный движок документа. Проверьте установленную сборку приложения."
        error.javaClass.simpleName.contains("password", true) ||
            error.message.orEmpty().contains("password", true) ||
            error.message.orEmpty().contains("encrypted", true) ->
            "PDF защищён паролем. Откройте незашифрованную копию документа."
        error.message.orEmpty().contains("нет страниц", true) -> "В документе нет страниц."
        error is java.io.IOException -> "Не удалось прочитать документ. Файл может быть повреждён или доступ к нему потерян."
        else -> error.message?.takeIf { message -> message.any { it in 'А'..'я' || it == 'Ё' || it == 'ё' } }
            ?: "Не удалось обработать документ. Возможно, файл повреждён или использует неподдерживаемые возможности."
    }
}
