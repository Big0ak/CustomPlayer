package com.example.customplayer

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import java.io.IOException

class MusicService : Service(), MediaPlayer.OnCompletionListener {

    companion object {
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_TOGGLE_PLAYBACK = "ACTION_TOGGLE_PLAYBACK"
        const val ACTION_NEXT = "ACTION_NEXT"

        const val CHANNEL_ID = "MusicServiceChannel"
        const val NOTIFICATION_ID = 1

        private val _currentSongTitle = MutableLiveData<String>("Нет трека")
        val currentSongTitle: LiveData<String> get() = _currentSongTitle

        var playlist: MutableList<Uri> = mutableListOf()
        var currentIndex: Int = 0
    }

    private var mediaPlayer: MediaPlayer? = null
    private var songTitle: String = "Нет трека"

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                if (playlist.isNotEmpty()) {
                    val uriString = playlist[currentIndex].toString()
                    playSong(uriString)
                }
            }
            ACTION_TOGGLE_PLAYBACK -> {
                togglePlayback()
            }
            ACTION_NEXT -> {
                nextTrack()
            }
        }
        return START_STICKY
    }

    /*
     * Запускаем воспроизведение трека **асинхронно**.
     */
    @SuppressLint("ForegroundServiceType")
    private fun playSong(uriString: String) {
        // Если  были корутины, можно вызывать release() прямо здесь
        mediaPlayer?.release()

        // Создаем новый MediaPlayer
        mediaPlayer = MediaPlayer().apply {
            // Указываем источник
            setDataSource(this@MusicService, Uri.parse(uriString))

            // Этот колбэк сработает, когда плеер будет действительно готов к воспроизведению
            setOnPreparedListener {
                // Здесь плеер уже в состоянии Prepared
                start()

                // Получаем title из метаданных
                songTitle = getSongTitle(uriString)
                _currentSongTitle.value = songTitle

                // Запускаем Foreground Service и показываем уведомление
                startForeground(NOTIFICATION_ID, createNotification())
            }

            // Колбэк завершения трека
            setOnCompletionListener(this@MusicService)

            // Асинхронная подготовка (не блокирует поток)
            // После вызова prepareAsync() сразу вернет управление, а фактическая
            // подготовка произойдет в фоне. Когда закончится — вызовется onPreparedListener
            prepareAsync()
        }
    }

    /**
     * Пауза / Возобновление
     */
    private fun togglePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _currentSongTitle.value = "Пауза: $songTitle"
            } else {
                it.start()
                _currentSongTitle.value = songTitle
            }
            updateNotification()
        }
    }

    /**
     * Следующий трек
     */
    private fun nextTrack() {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlist.size
        playSong(playlist[currentIndex].toString())
    }

    /**
     * Создание (или обновление) уведомления
     */
    private fun createNotification(): Notification {
        val playPauseAction = if (mediaPlayer?.isPlaying == true) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Пауза",
                getPendingIntent(ACTION_TOGGLE_PLAYBACK)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Воспроизвести",
                getPendingIntent(ACTION_TOGGLE_PLAYBACK)
            )
        }

        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "Следующий",
            getPendingIntent(ACTION_NEXT)
        )

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            pendingIntentFlags
        )

        val largeIcon = BitmapFactory.decodeResource(resources, android.R.drawable.ic_media_play)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CustomPlayer")
            .setContentText(songTitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(largeIcon)
            .setContentIntent(pendingIntent)
            .addAction(playPauseAction)
            .addAction(nextAction)
            // Во время активного воспроизведения уведомление можно пометить .setOngoing(true)
            .setOngoing(mediaPlayer?.isPlaying == true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Обновление уведомления (когда переключаемся между Play/Pause и т.п.)
     */
    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Получаем название трека из метаданных
     */
    private fun getSongTitle(uriString: String): String {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, Uri.parse(uriString))
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?: "Неизвестный трек"
        retriever.release()
        return title
    }

    /**
     * Когда трек заканчивается — переключаемся дальше
     */
    override fun onCompletion(mp: MediaPlayer?) {
        nextTrack()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Создаем канал уведомлений
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Music Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Создаем PendingIntent для кнопок в уведомлении
     */
    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            this.action = action
        }
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        return PendingIntent.getBroadcast(this, 0, intent, flags)
    }
}
