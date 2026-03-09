package com.alcint.pargelium

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PlaylistDatabase {
    private lateinit var dbHelper: PlaylistDbHelper
    private val gson = Gson()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        dbHelper = PlaylistDbHelper(context.applicationContext)
        migrateFromPrefsIfNeeded()
        isInitialized = true
    }

    private fun migrateFromPrefsIfNeeded() {
        val oldPlaylists = PrefsManager.getOldPlaylists()
        if (oldPlaylists.isNotEmpty()) {
            oldPlaylists.forEach { savePlaylist(it) }
            PrefsManager.clearOldPlaylists()
        }
    }

    fun getPlaylists(): List<PlaylistModel> {
        if (!isInitialized) return emptyList()

        val db = dbHelper.readableDatabase
        val cursor = db.query(PlaylistDbHelper.TABLE_NAME, null, null, null, null, null, "${PlaylistDbHelper.COL_CREATED_AT} ASC")
        val list = mutableListOf<PlaylistModel>()

        with(cursor) {
            while (moveToNext()) {
                val id = getString(getColumnIndexOrThrow(PlaylistDbHelper.COL_ID))
                val name = getString(getColumnIndexOrThrow(PlaylistDbHelper.COL_NAME))
                val trackIdsJson = getString(getColumnIndexOrThrow(PlaylistDbHelper.COL_TRACK_IDS))
                val coverUri = getString(getColumnIndexOrThrow(PlaylistDbHelper.COL_COVER_URI))
                val bannerUri = getString(getColumnIndexOrThrow(PlaylistDbHelper.COL_BANNER_URI))
                val createdAt = getLong(getColumnIndexOrThrow(PlaylistDbHelper.COL_CREATED_AT))

                val type = object : TypeToken<List<Long>>() {}.type
                val trackIds: List<Long> = try { gson.fromJson(trackIdsJson, type) } catch (e: Exception) { emptyList() }

                list.add(PlaylistModel(id, name, trackIds, coverUri, bannerUri, createdAt))
            }
        }
        cursor.close()
        return list
    }

    fun savePlaylist(playlist: PlaylistModel) {
        if (!isInitialized) return
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(PlaylistDbHelper.COL_ID, playlist.id)
            put(PlaylistDbHelper.COL_NAME, playlist.name)
            put(PlaylistDbHelper.COL_TRACK_IDS, gson.toJson(playlist.trackIds))
            put(PlaylistDbHelper.COL_COVER_URI, playlist.coverUri)
            put(PlaylistDbHelper.COL_BANNER_URI, playlist.bannerUri)
            put(PlaylistDbHelper.COL_CREATED_AT, playlist.createdAt)
        }
        db.insertWithOnConflict(PlaylistDbHelper.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deletePlaylist(playlistId: String) {
        if (!isInitialized) return
        val db = dbHelper.writableDatabase
        db.delete(PlaylistDbHelper.TABLE_NAME, "${PlaylistDbHelper.COL_ID} = ?", arrayOf(playlistId))
    }

    fun addTrackToPlaylist(playlistId: String, trackId: Long) {
        val playlists = getPlaylists()
        val playlist = playlists.find { it.id == playlistId }
        if (playlist != null && !playlist.trackIds.contains(trackId)) {
            savePlaylist(playlist.copy(trackIds = playlist.trackIds + trackId))
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: Long) {
        val playlists = getPlaylists()
        val playlist = playlists.find { it.id == playlistId }
        if (playlist != null) {
            savePlaylist(playlist.copy(trackIds = playlist.trackIds.filter { it != trackId }))
        }
    }
}

class PlaylistDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "PargeliumPlaylists.db"

        const val TABLE_NAME = "playlists"
        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_TRACK_IDS = "track_ids"
        const val COL_COVER_URI = "cover_uri"
        const val COL_BANNER_URI = "banner_uri"
        const val COL_CREATED_AT = "created_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID TEXT PRIMARY KEY,
                $COL_NAME TEXT NOT NULL,
                $COL_TRACK_IDS TEXT NOT NULL,
                $COL_COVER_URI TEXT,
                $COL_BANNER_URI TEXT,
                $COL_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }
}