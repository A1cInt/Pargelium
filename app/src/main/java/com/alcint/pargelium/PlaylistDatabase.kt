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
    private val trackListType = object : TypeToken<List<Long>>() {}.type

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            dbHelper = PlaylistDbHelper(context.applicationContext)
            migrateFromPrefsIfNeeded()
            isInitialized = true
        }
    }

    private fun migrateFromPrefsIfNeeded() {
        val oldPlaylists = PrefsManager.getOldPlaylists()
        if (oldPlaylists.isNotEmpty()) {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                oldPlaylists.forEach { savePlaylistInternal(db, it) }
                db.setTransactionSuccessful()
                PrefsManager.clearOldPlaylists()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun getPlaylists(): List<PlaylistModel> {
        if (!isInitialized) return emptyList()

        val list = mutableListOf<PlaylistModel>()

        dbHelper.readableDatabase.query(
            PlaylistDbHelper.TABLE_NAME,
            null, null, null, null, null,
            "${PlaylistDbHelper.COL_CREATED_AT} ASC"
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(PlaylistDbHelper.COL_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(PlaylistDbHelper.COL_NAME)
            val trackIdsIdx = cursor.getColumnIndexOrThrow(PlaylistDbHelper.COL_TRACK_IDS)
            val coverUriIdx = cursor.getColumnIndexOrThrow(PlaylistDbHelper.COL_COVER_URI)
            val bannerUriIdx = cursor.getColumnIndexOrThrow(PlaylistDbHelper.COL_BANNER_URI)
            val createdAtIdx = cursor.getColumnIndexOrThrow(PlaylistDbHelper.COL_CREATED_AT)

            while (cursor.moveToNext()) {
                val trackIds: List<Long> = try {
                    gson.fromJson(cursor.getString(trackIdsIdx), trackListType) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }

                list.add(
                    PlaylistModel(
                        cursor.getString(idIdx),
                        cursor.getString(nameIdx),
                        trackIds,
                        cursor.getString(coverUriIdx),
                        cursor.getString(bannerUriIdx),
                        cursor.getLong(createdAtIdx)
                    )
                )
            }
        }
        return list
    }

    fun getPlaylistById(playlistId: String): PlaylistModel? {
        if (!isInitialized) return null

        dbHelper.readableDatabase.query(
            PlaylistDbHelper.TABLE_NAME,
            null,
            "${PlaylistDbHelper.COL_ID} = ?",
            arrayOf(playlistId),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val trackIds: List<Long> = try {
                    gson.fromJson(cursor.getString(cursor.getColumnIndexOrThrow(PlaylistDbHelper.COL_TRACK_IDS)), trackListType) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }

                return PlaylistModel(
                    cursor.getString(cursor.getColumnIndexOrThrow(PlaylistDbHelper.COL_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(PlaylistDbHelper.COL_NAME)),
                    trackIds,
                    cursor.getString(cursor.getColumnIndexOrThrow(PlaylistDbHelper.COL_COVER_URI)),
                    cursor.getString(cursor.getColumnIndexOrThrow(PlaylistDbHelper.COL_BANNER_URI)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(PlaylistDbHelper.COL_CREATED_AT))
                )
            }
        }
        return null
    }

    fun savePlaylist(playlist: PlaylistModel) {
        if (!isInitialized) return
        savePlaylistInternal(dbHelper.writableDatabase, playlist)
    }

    private fun savePlaylistInternal(db: SQLiteDatabase, playlist: PlaylistModel) {
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
        dbHelper.writableDatabase.delete(PlaylistDbHelper.TABLE_NAME, "${PlaylistDbHelper.COL_ID} = ?", arrayOf(playlistId))
    }

    fun addTrackToPlaylist(playlistId: String, trackId: Long) {
        val playlist = getPlaylistById(playlistId) ?: return
        if (!playlist.trackIds.contains(trackId)) {
            val newTrackIds = ArrayList<Long>(playlist.trackIds.size + 1).apply {
                addAll(playlist.trackIds)
                add(trackId)
            }
            savePlaylist(playlist.copy(trackIds = newTrackIds))
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: Long) {
        val playlist = getPlaylistById(playlistId) ?: return
        if (playlist.trackIds.contains(trackId)) {
            savePlaylist(playlist.copy(trackIds = playlist.trackIds.filterNot { it == trackId }))
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