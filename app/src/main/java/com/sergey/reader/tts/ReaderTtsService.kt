package com.sergey.reader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.sergey.reader.MainActivity
import com.sergey.reader.ReaderApplication
import com.sergey.reader.model.ReaderBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ReaderTtsService : Service(), TextToSpeech.OnInitListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tts: TextToSpeech? = null
    private lateinit var mediaSession: MediaSession
    private var ttsReady = false
    private var pendingStart = false
    private var blocks: List<ReaderBlock> = emptyList()
    private var bookId: Long = 0L
    private var bookTitle: String = "Книга"
    private var currentIndex: Int = 0
    private var paused = false
    private var languageTag: String? = null
    private var speechRate: Float = 1.0f
    private var speechPitch: Float = 1.0f
    private var spokenRangeStart: Int = -1
    private var spokenRangeEnd: Int = -1
    private var sleepDeadlineMillis: Long = 0L
    private var sleepTimerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        createMediaSession()
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startReading(
                requestedBookId = intent.getLongExtra(EXTRA_BOOK_ID, 0L),
                requestedBlock = intent.getIntExtra(EXTRA_BLOCK_INDEX, 0),
                requestedRate = intent.getFloatExtra(EXTRA_RATE, 1.0f),
                requestedPitch = intent.getFloatExtra(EXTRA_PITCH, 1.0f)
            )
            ACTION_TOGGLE -> togglePause()
            ACTION_NEXT -> skip(1)
            ACTION_PREVIOUS -> skip(-1)
            ACTION_STOP -> stopReading()
            ACTION_SET_SLEEP_TIMER -> setSleepTimer(intent.getIntExtra(EXTRA_SLEEP_MINUTES, 0))
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) {
            ReaderTtsController.publish(TtsUiState(error = "Не удалось инициализировать TTS"))
            stopSelf()
            return
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                spokenRangeStart = -1
                spokenRangeEnd = -1
                publishState()
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                spokenRangeStart = start.coerceAtLeast(0)
                spokenRangeEnd = end.coerceAtLeast(spokenRangeStart)
                publishState()
            }

            override fun onDone(utteranceId: String?) {
                scope.launch { advanceAndSpeak() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                ReaderTtsController.publish(TtsUiState(error = "Ошибка озвучки"))
                scope.launch { advanceAndSpeak() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                ReaderTtsController.publish(TtsUiState(error = "Ошибка озвучки: $errorCode"))
                scope.launch { advanceAndSpeak() }
            }
        })

        if (pendingStart) {
            pendingStart = false
            applyLanguage()
            speakCurrent()
        }
    }

    private fun startReading(
        requestedBookId: Long,
        requestedBlock: Int,
        requestedRate: Float,
        requestedPitch: Float
    ) {
        if (requestedBookId <= 0L) return
        tts?.stop()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepDeadlineMillis = 0L
        blocks = emptyList()
        pendingStart = false
        bookId = requestedBookId
        currentIndex = requestedBlock.coerceAtLeast(0)
        speechRate = requestedRate.coerceIn(0.1f, 4.0f)
        speechPitch = requestedPitch.coerceIn(0.5f, 2.0f)
        paused = false
        spokenRangeStart = -1
        spokenRangeEnd = -1

        mediaSession.isActive = true
        updateMediaSessionState()
        startForeground(NOTIFICATION_ID, buildNotification("Подготовка книги…", isPaused = false))
        ReaderTtsController.publish(TtsUiState(active = true, bookId = bookId, blockIndex = currentIndex, title = bookTitle))

        scope.launch {
            val container = (application as ReaderApplication).container
            val book = withContext(Dispatchers.IO) { container.books.getBook(bookId) }
            val loaded = withContext(Dispatchers.IO) { container.books.loadBlocks(bookId) }
            if (loaded.none { it.kind == ReaderBlock.Kind.PARAGRAPH || it.kind == ReaderBlock.Kind.FOOTNOTE || it.kind == ReaderBlock.Kind.PDF_TEXT || it.kind == ReaderBlock.Kind.DJVU_TEXT }) {
                ReaderTtsController.publish(TtsUiState(error = "В документе нет текстового слоя для озвучки"))
                stopReading()
                return@launch
            }

            blocks = loaded
            bookTitle = book?.title ?: "Книга"
            languageTag = book?.language
            mediaSession.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, bookTitle)
                    .putString(MediaMetadata.METADATA_KEY_AUTHOR, book?.authors.orEmpty())
                    .build()
            )

            currentIndex = findSpeakable(currentIndex.coerceIn(0, blocks.lastIndex), 1)
                ?: findSpeakable(currentIndex.coerceIn(0, blocks.lastIndex), -1)
                ?: 0
            updateNotification()

            if (ttsReady) {
                applyLanguage()
                speakCurrent()
            } else {
                pendingStart = true
            }
        }
    }

    private fun togglePause() {
        if (blocks.isEmpty()) return
        if (paused) {
            paused = false
            updateNotification()
            updateMediaSessionState()
            speakCurrent()
        } else {
            paused = true
            tts?.stop()
            publishState()
            updateNotification()
            updateMediaSessionState()
        }
    }

    private fun skip(direction: Int) {
        if (blocks.isEmpty()) return
        val next = findSpeakable(currentIndex + direction, direction) ?: return
        currentIndex = next
        paused = false
        spokenRangeStart = -1
        spokenRangeEnd = -1
        tts?.stop()
        speakCurrent()
    }

    private fun advanceAndSpeak() {
        if (paused || blocks.isEmpty()) return
        val next = findSpeakable(currentIndex + 1, 1)
        if (next == null) {
            stopReading()
            return
        }
        currentIndex = next
        spokenRangeStart = -1
        spokenRangeEnd = -1
        speakCurrent()
    }

    private fun findSpeakable(start: Int, direction: Int): Int? {
        if (blocks.isEmpty()) return null
        var i = start
        while (i in blocks.indices) {
            val block = blocks[i]
            if (block.isSpeakable && block.text.isNotBlank() && block.kind != ReaderBlock.Kind.CHAPTER) return i
            i += if (direction >= 0) 1 else -1
        }
        return null
    }

    private fun speakCurrent() {
        if (!ttsReady || paused || blocks.isEmpty()) return
        if (currentIndex !in blocks.indices || !blocks[currentIndex].isSpeakable || blocks[currentIndex].kind == ReaderBlock.Kind.CHAPTER) {
            val next = findSpeakable(currentIndex, 1) ?: run { stopReading(); return }
            currentIndex = next
        }
        spokenRangeStart = -1
        spokenRangeEnd = -1
        val text = blocks[currentIndex].text.trim()
        if (text.isBlank()) {
            advanceAndSpeak()
            return
        }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "reader_${bookId}_$currentIndex")
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "reader_${bookId}_$currentIndex")
        scope.launch(Dispatchers.IO) {
            (application as ReaderApplication).container.books.updateProgress(bookId, currentIndex, blocks.size)
        }
        publishState()
        updateNotification()
        updateMediaSessionState()
    }

    private fun applyLanguage() {
        val tag = languageTag?.trim().orEmpty()
        val locale = if (tag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(tag.replace('_', '-'))
        runCatching { tts?.language = locale }
        runCatching { tts?.setSpeechRate(speechRate) }
        runCatching { tts?.setPitch(speechPitch) }
    }

    private fun publishState() {
        ReaderTtsController.publish(
            TtsUiState(
                active = true,
                paused = paused,
                bookId = bookId,
                blockIndex = currentIndex,
                title = bookTitle,
                rangeStart = spokenRangeStart,
                rangeEnd = spokenRangeEnd,
                sleepDeadlineMillis = sleepDeadlineMillis
            )
        )
    }

    private fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes <= 0) {
            sleepDeadlineMillis = 0L
            publishState()
            updateNotification()
            return
        }
        sleepDeadlineMillis = System.currentTimeMillis() + minutes.coerceIn(1, 240) * 60_000L
        publishState()
        updateNotification()
        sleepTimerJob = scope.launch {
            while (sleepDeadlineMillis > 0L) {
                val left = sleepDeadlineMillis - System.currentTimeMillis()
                if (left <= 0L) {
                    stopReading()
                    break
                }
                delay(minOf(left, 15_000L))
                publishState()
                updateNotification()
            }
        }
    }

    private fun stopReading() {
        pendingStart = false
        paused = false
        spokenRangeStart = -1
        spokenRangeEnd = -1
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepDeadlineMillis = 0L
        tts?.stop()
        ReaderTtsController.publish(TtsUiState())
        mediaSession.isActive = false
        updateMediaSessionState(stopped = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createMediaSession() {
        mediaSession = MediaSession(this, "ReaderTtsSession").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (paused) togglePause() else if (blocks.isNotEmpty()) speakCurrent()
                }

                override fun onPause() {
                    if (!paused) togglePause()
                }

                override fun onStop() = stopReading()
                override fun onSkipToNext() = skip(1)
                override fun onSkipToPrevious() = skip(-1)
            })
            isActive = false
        }
    }

    private fun updateMediaSessionState(stopped: Boolean = false) {
        val state = when {
            stopped -> PlaybackState.STATE_STOPPED
            paused -> PlaybackState.STATE_PAUSED
            else -> PlaybackState.STATE_PLAYING
        }
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, currentIndex.toLong(), if (paused || stopped) 0f else 1f)
                .build()
        )
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                text = buildString {
                    append(if (paused) "Пауза" else "Озвучка")
                    append(" · ${currentIndex + 1}/${blocks.size.coerceAtLeast(1)}")
                    if (sleepDeadlineMillis > System.currentTimeMillis()) {
                        val minutes = ((sleepDeadlineMillis - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L) + 1L
                        append(" · таймер ${minutes} мин")
                    }
                },
                isPaused = paused
            )
        )
    }

    private fun buildNotification(text: String, isPaused: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun serviceAction(requestCode: Int, action: String): PendingIntent = PendingIntent.getService(
            this,
            requestCode,
            Intent(this, ReaderTtsService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(bookTitle)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(android.R.drawable.ic_media_previous, "Назад", serviceAction(13, ACTION_PREVIOUS))
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Продолжить" else "Пауза",
                serviceAction(11, ACTION_TOGGLE)
            )
            .addAction(android.R.drawable.ic_media_next, "Вперёд", serviceAction(14, ACTION_NEXT))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Стоп", serviceAction(12, ACTION_STOP))
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Озвучка книг",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновая озвучка текста книги"
                setSound(null, null)
            }
        )
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        mediaSession.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.sergey.reader.tts.START"
        const val ACTION_TOGGLE = "com.sergey.reader.tts.TOGGLE"
        const val ACTION_NEXT = "com.sergey.reader.tts.NEXT"
        const val ACTION_PREVIOUS = "com.sergey.reader.tts.PREVIOUS"
        const val ACTION_STOP = "com.sergey.reader.tts.STOP"
        const val ACTION_SET_SLEEP_TIMER = "com.sergey.reader.tts.SET_SLEEP_TIMER"
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_BLOCK_INDEX = "block_index"
        const val EXTRA_RATE = "rate"
        const val EXTRA_PITCH = "pitch"
        const val EXTRA_SLEEP_MINUTES = "sleep_minutes"
        private const val CHANNEL_ID = "reader_tts"
        private const val NOTIFICATION_ID = 4201
    }
}
