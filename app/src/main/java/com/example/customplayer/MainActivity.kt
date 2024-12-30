package com.example.customplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.customplayer.adapters.SongAdapter
import com.example.customplayer.models.Song

class MainActivity : ComponentActivity() {

    private lateinit var songAdapter: SongAdapter
    private lateinit var recyclerView: RecyclerView

    private lateinit var btnPlayPause: Button
    private lateinit var btnNext: Button

    // Для запроса разрешений
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val deniedPermissions = permissions.filterValues { !it }
            if (deniedPermissions.isNotEmpty()) {
                Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_SHORT).show()
            } else {
                // Все разрешения даны
                loadSongsFromDevice()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Создаем корневой LinearLayout
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 1. RecyclerView для отображения песен
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = 32
            }
        }
        songAdapter = SongAdapter(emptyList()) { selectedSong ->
            val index = MusicService.playlist.indexOf(selectedSong.uri)
            if (index >= 0) {
                MusicService.currentIndex = index
                val serviceIntent = Intent(this, MusicService::class.java).apply {
                    action = MusicService.ACTION_PLAY
                }
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                Toast.makeText(this, "Трек не найден в плейлисте", Toast.LENGTH_SHORT).show()
            }
        }
        //Разделение визуально
        val dividerItemDecoration = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecoration)
        recyclerView.adapter = songAdapter
        rootLayout.addView(recyclerView)

        // 2. LinearLayout для панели управления
        val controlPanelLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 2.1. TextView — показывает детали текущего трека
        val tvCurrentTrack = TextView(this).apply {
            text = "Текущий трек: —"
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }
        controlPanelLayout.addView(tvCurrentTrack)

        // Подписываемся на LiveData из MusicService, чтобы обновлять текст при смене трека
        MusicService.currentSongTitle.observe(this, Observer { newTitle ->
            tvCurrentTrack.text = "Текущий трек: $newTitle"
        })

        // 2.2. Кнопки управления (Play/Pause, Next)
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 40
            }
        }

        btnPlayPause = Button(this).apply {
            text = "Play/Pause"
            setOnClickListener {
                val intent = Intent(this@MainActivity, MusicService::class.java).apply {
                    action = MusicService.ACTION_TOGGLE_PLAYBACK
                }
                ContextCompat.startForegroundService(this@MainActivity, intent)
            }
        }

        btnNext = Button(this).apply {
            text = "Next"
            setOnClickListener {
                val intent = Intent(this@MainActivity, MusicService::class.java).apply {
                    action = MusicService.ACTION_NEXT
                }
                ContextCompat.startForegroundService(this@MainActivity, intent)
            }
        }

        buttonLayout.addView(btnPlayPause)
        buttonLayout.addView(btnNext)
        controlPanelLayout.addView(buttonLayout)

        // Добавляем панель управления в корневой макет
        rootLayout.addView(controlPanelLayout)

        // Устанавливаем разметку
        setContentView(rootLayout)

        // Проверяем разрешения
        checkPermissions()

        // Обрабатываем внешний Intent
        handleExternalIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExternalIntent(intent)
    }

    private fun handleExternalIntent(intent: Intent?) {
        if (intent == null) return

        // Если получили Uri во внешнем Intent
        val dataUri: Uri? = intent.data
        val songUriString = intent.getStringExtra("SONG_URI")

        val finalUri = dataUri ?: songUriString?.let { Uri.parse(it) }
        finalUri?.let {
            MusicService.playlist = mutableListOf(it)
            MusicService.currentIndex = 0

            val serviceIntent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_PLAY
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    private fun checkPermissions() {
        // Формируем список разрешений, которые нам нужны
        val neededPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // На Android 13+ (API 33 и выше)
            neededPermissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // На Android 12 и ниже
            neededPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Проверяем, какие из них не выданы
        val permissionsToRequest = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            // Запрашиваем  разрешения
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Все нужные разрешения уже есть
            loadSongsFromDevice()
        }
    }

    // Загружаем треки с устройства
    private fun loadSongsFromDevice() {
        val songs = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE
        )

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor: Cursor? = contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val title = it.getString(titleColumn)
                val contentUri = Uri.withAppendedPath(collection, "$id")

                songs.add(
                    Song(
                        id,
                        title,
                        contentUri
                    )
                )
            }
        }
        cursor?.close()

        // Обновляем адаптер
        songAdapter.updateSongs(songs)

        // Обновляем плейлист в MusicService
        MusicService.playlist = songs.map { it.uri }.toMutableList()
        MusicService.currentIndex = 0



    }
}
