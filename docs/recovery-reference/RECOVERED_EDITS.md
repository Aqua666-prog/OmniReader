# Извлечённые фрагменты последующих правок

Ниже — фрагменты кода из ранее выполненных команд. Они не применены к базе 0.7.0. Полные файлы в recovered-sources могут соответствовать более ранней стадии, чем эти фрагменты.

## Модель межстраничного выделения

```kotlin
data class DocumentSelection(val page: DocumentTextPage, val start: Int, val end: Int, val following: List<DocumentSelection> = emptyList()) {
    val parts get() = listOf(if(following.isEmpty()) this else copy(following=emptyList()))+following
    val text get() = parts.joinToString("\n") { it.page.text.substring(it.start.coerceIn(0,it.page.text.length),it.end.coerceIn(it.start,it.page.text.length)) }
    val bounds by lazy { page.bounds(start, end) }
}
```

DocumentTextPage получил дополнительный параметр `val recognized: Boolean = false`. Для DocumentSelectionRange требовался import kotlinx.coroutines.ensureActive.

## Ускорение поиска bounds

```kotlin
    fun bounds(start: Int,end: Int): List<DRect> {
        if(end<=start)return emptyList()
        var low=0;var high=glyphs.size
        while(low<high){val mid=(low+high) ushr 1;if(glyphs[mid].end<=start)low=mid+1 else high=mid}
        val out=mutableListOf<DRect>()
        while(low<glyphs.size && glyphs[low].start<end){out+=glyphs[low].bounds;low++}
        return out
    }
```

## DocumentSearch.hit

```kotlin
    fun hit(page: DocumentTextPage,index: Int,length: Int): DocumentSearchHit {
        var a=(index-45).coerceAtLeast(0);var b=(index+length+75).coerceAtMost(page.text.length)
        if(a>0 && page.text[a].isLowSurrogate())a--
        if(b<page.text.length && b>0 && page.text[b-1].isHighSurrogate())b++
        return DocumentSearchHit(page.pageIndex,index,index+length,page.text.substring(a,b),page.bounds(index,index+length))
    }
```

## Unicode PDF tests — добавлено в glyphCoordinatesMatchSharpRegionRender

```kotlin
            assertTrue(text.text.contains("Русский"))
            assertTrue(text.text.any{it in '\u0590'..'\u05ff'})
```

## Защита от race condition при выделении

```kotlin
    private var selectionGeneration=0
    private fun cancelSelectionWork(){selectionGeneration++;selectionJob?.cancel();onBusy(false)}
```

Перед запуском selectionJob сохранялся `val token=++selectionGeneration`. Во всех finally старый безусловный onBusy(false) заменялся на `if(token==selectionGeneration)onBusy(false)`. Аналогично в DocumentViewer разделены selectionBusy/searchBusy, добавлен searchGeneration, finally поиска проверял свой token.

## Последующая правка OCR — один переиспользуемый движок

В companion object TesseractOcrEngine добавлялись:

```kotlin
        private var shared:TessBaseAPI?=null
        private var activeContext:kotlin.coroutines.CoroutineContext?=null
        private val cleanupScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        private var cleanup:Job?=null
        fun trimMemory(){cleanupScope.launch{gate.withLock{cleanup?.cancel();shared?.recycle();shared=null}}}
```

Вместо создания api на каждой странице:

```kotlin
            cleanup?.cancel()
            activeContext=currentCoroutineContext()
            val api=shared ?: TessBaseAPI { if(activeContext?.isActive==false) shared?.stop() }.also { candidate ->
                shared=candidate
                try {
                    check(candidate.init(base.absolutePath,languages.joinToString("+"),TessBaseAPI.OEM_LSTM_ONLY)){"Не удалось загрузить модели OCR"}
                } catch(error:Throwable){shared=null;activeContext=null;candidate.recycle();throw error}
            }
```

Итоговый finally:

```kotlin
                if(activeContext?.isActive==false){api.recycle();shared=null}
                else api.clear() // Free page/image state; reuse models during a document search.
                activeContext=null
                cleanup=cleanupScope.launch {delay(30_000);gate.withLock{shared?.recycle();shared=null}}
```

## Последующая правка навигатора поиска

Добавлялся `onUnreadable: (Int)->Unit = {}` после read. Перед циклом — failures=0. Чтение страницы:

```kotlin
            val page=try{read(pageIndex)}
            catch(cancelled:CancellationException){throw cancelled}
            catch(error:SecurityException){throw error}
            catch(error:Exception){failures++;onUnreadable(pageIndex);if(failures>=10)throw error;continue}
```

В двух тестах trailing lambda после добавления параметра заменялась на именованную: `adjacent(1,"x",current,1,read={p})` и `adjacent(1,"x",current,-1,read={p})`.

## OCR cache — обработка ошибки записи

В DocumentSession запись кэша заменялась на:

```kotlin
withContext(Dispatchers.IO){try{store.write(result)}catch(_:java.io.IOException){ /* Cache is optional; recognition remains usable. */ }}
```

OCR bitmap выделялся через nullable owned с recycle в finally, чтобы отмена withContext не теряла bitmap. OOM/LinkageError превращались в пользовательские IllegalStateException. Это интеграционные правки DocumentBackend, полный итоговый файл здесь не восстановлен.
