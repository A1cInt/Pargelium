package com.alcint.pargelium

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class PlaylistModel(val id: String = UUID.randomUUID().toString(), val name: String, val trackIds: List<Long>, val coverUri: String? = null, val bannerUri: String? = null, val createdAt: Long = System.currentTimeMillis())
data class PlayEvent(val trackId: Long, val timestamp: Long)
data class TrackBookmark(val id: String = UUID.randomUUID().toString(), val name: String, val timeMs: Long)

data class ArtistMetadata(
    val avatarUri: String? = null,
    val bannerUri: String? = null,
    val description: String = ""
)

object PrefsManager {
    private const val PREFS_NAME = "pargelium_audio_prefs"
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        PlaylistDatabase.init(context)
    }

    // ==========================================
    // Исполнители
    // ==========================================
    fun saveArtistMetadata(artistName: String, meta: ArtistMetadata) {
        val safeName = artistName.replace(Regex("[^A-Za-z0-9]"), "_")
        prefs.edit().putString("artist_meta_$safeName", gson.toJson(meta)).apply()
    }

    fun getArtistMetadata(artistName: String): ArtistMetadata {
        val safeName = artistName.replace(Regex("[^A-Za-z0-9]"), "_")
        val json = prefs.getString("artist_meta_$safeName", null) ?: return ArtistMetadata()
        return try {
            gson.fromJson(json, ArtistMetadata::class.java)
        } catch (e: Exception) {
            ArtistMetadata()
        }
    }

    // ==========================================
    // Плейлисты
    // ==========================================

    fun getOldPlaylists(): List<PlaylistModel> {
        val json = prefs.getString("playlists", "[]")
        val type = object : TypeToken<List<PlaylistModel>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearOldPlaylists() = prefs.edit().remove("playlists").apply()

    fun getPlaylists(): List<PlaylistModel> = PlaylistDatabase.getPlaylists()
    fun savePlaylist(playlist: PlaylistModel) = PlaylistDatabase.savePlaylist(playlist)
    fun deletePlaylist(playlistId: String) = PlaylistDatabase.deletePlaylist(playlistId)
    fun addTrackToPlaylist(playlistId: String, trackId: Long) =
        PlaylistDatabase.addTrackToPlaylist(playlistId, trackId)

    fun removeTrackFromPlaylist(playlistId: String, trackId: Long) =
        PlaylistDatabase.removeTrackFromPlaylist(playlistId, trackId)


    fun saveCustomCanvas(trackKey: String, uri: String?) {
        if (uri == null) prefs.edit().remove("canvas_$trackKey").apply() else prefs.edit()
            .putString("canvas_$trackKey", uri).apply()
    }

    fun getCustomCanvas(trackKey: String): String? = prefs.getString("canvas_$trackKey", null)

    fun getTrackBookmarks(trackKey: String): List<TrackBookmark> {
        val json = prefs.getString("bookmarks_$trackKey", "[]");
        val type = object : TypeToken<List<TrackBookmark>>() {}.type; return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTrackBookmarks(trackKey: String, bookmarks: List<TrackBookmark>) =
        prefs.edit().putString("bookmarks_$trackKey", gson.toJson(bookmarks)).apply()

    fun saveLastTrackId(id: Long) = prefs.edit().putLong("last_track_id", id).apply()
    fun getLastTrackId(): Long = prefs.getLong("last_track_id", -1L)
    fun saveLastPosition(pos: Long) = prefs.edit().putLong("last_position", pos).apply()
    fun getLastPosition(): Long = prefs.getLong("last_position", 0L)
    fun saveLastQueue(trackIds: List<Long>) =
        prefs.edit().putString("last_queue", gson.toJson(trackIds)).apply()

    fun getLastQueue(): List<Long> {
        val json = prefs.getString("last_queue", "[]");
        val type = object : TypeToken<List<Long>>() {}.type; return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveUserName(name: String) = prefs.edit().putString("user_name", name).apply()
    fun getUserName(): String = prefs.getString("user_name", "User") ?: "User"
    fun saveAvatarUri(uri: String) = prefs.edit().putString("user_avatar", uri).apply()
    fun getAvatarUri(): String? = prefs.getString("user_avatar", null)
    fun saveBannerUri(uri: String) = prefs.edit().putString("user_banner", uri).apply()
    fun getBannerUri(): String? = prefs.getString("user_banner", null)

    fun recordPlay(trackId: Long) {
        val events = getPlayEvents().toMutableList(); events.add(
            PlayEvent(
                trackId,
                System.currentTimeMillis()
            )
        ); prefs.edit().putString("play_events_v2", gson.toJson(events)).apply()
    }

    fun getPlayEvents(): List<PlayEvent> {
        val json = prefs.getString("play_events_v2", "[]");
        val type = object : TypeToken<List<PlayEvent>>() {}.type; return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getTopTracksIds(days: Int): Map<Long, Int> {
        val events = getPlayEvents();
        val cutoff =
            if (days == -1) 0L else System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L); return events.filter { it.timestamp >= cutoff }
            .groupingBy { it.trackId }.eachCount()
    }

    // ==========================================
    // Настройки звука и эффектов
    // ==========================================

    fun saveEnabled(enabled: Boolean) = prefs.edit().putBoolean("global_enabled", enabled).apply()
    fun getEnabled(): Boolean = prefs.getBoolean("global_enabled", true)

    private var cachedEqGains: FloatArray? = null
    private var cachedEqGainsStr: String? = null
    fun saveEqGains(gains: FloatArray) =
        prefs.edit().putString("eq_gains", gains.joinToString(",")).apply()

    fun getEqGains(): FloatArray {
        val str = prefs.getString("eq_gains", "") ?: ""
        if (cachedEqGains != null && cachedEqGainsStr == str) return cachedEqGains!!
        cachedEqGainsStr = str
        cachedEqGains = try {
            str.split(",").map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            FloatArray(15) { 0f }
        }
        return cachedEqGains!!
    }

    private var cachedUserEqGains: FloatArray? = null
    private var cachedUserEqGainsStr: String? = null
    fun saveUserEqEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("user_eq_enabled", enabled).apply()

    fun getUserEqEnabled(): Boolean = prefs.getBoolean("user_eq_enabled", false)
    fun saveUserEqGains(gains: FloatArray) =
        prefs.edit().putString("user_eq_gains", gains.joinToString(",")).apply()

    fun getUserEqGains(): FloatArray {
        val str = prefs.getString("user_eq_gains", "") ?: ""
        if (cachedUserEqGains != null && cachedUserEqGainsStr == str) return cachedUserEqGains!!
        cachedUserEqGainsStr = str
        cachedUserEqGains = try {
            str.split(",").map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            FloatArray(10) { 0f }
        }
        return cachedUserEqGains!!
    }

    fun saveBassEnabled(enabled: Boolean) = prefs.edit().putBoolean("bass_enabled", enabled).apply()
    fun getBassEnabled(): Boolean = prefs.getBoolean("bass_enabled", false)
    fun saveBass(value: Int) = prefs.edit().putInt("bass", value).apply()
    fun getBass(): Int = prefs.getInt("bass", 0)
    fun saveBassFreq(value: Int) = prefs.edit().putInt("bass_freq", value).apply()
    fun getBassFreq(): Int = prefs.getInt("bass_freq", 60)

    fun saveRoom(enabled: Boolean) = prefs.edit().putBoolean("room_enabled", enabled).apply()
    fun getRoom(): Boolean = prefs.getBoolean("room_enabled", false)
    fun saveReverbMode(mode: Int) = prefs.edit().putInt("reverb_mode", mode).apply()
    fun getReverbMode(): Int = prefs.getInt("reverb_mode", 0)
    fun saveReverbSize(value: Int) = prefs.edit().putInt("reverb_size", value).apply()
    fun getReverbSize(): Int = prefs.getInt("reverb_size", 50)
    fun saveReverbDamp(value: Int) = prefs.edit().putInt("reverb_damp", value).apply()
    fun getReverbDamp(): Int = prefs.getInt("reverb_damp", 30)
    fun saveReverbMix(value: Int) = prefs.edit().putInt("reverb_mix", value).apply()
    fun getReverbMix(): Int = prefs.getInt("reverb_mix", 30)

    fun saveHaas(enabled: Boolean) = prefs.edit().putBoolean("haas", enabled).apply()
    fun getHaas(): Boolean = prefs.getBoolean("haas", false)
    fun saveHaasDelay(value: Int) = prefs.edit().putInt("haas_delay", value).apply()
    fun getHaasDelay(): Int = prefs.getInt("haas_delay", 10)

    fun saveSpatializer(enabled: Boolean) = prefs.edit().putBoolean("spatializer", enabled).apply()
    fun getSpatializer(): Boolean = prefs.getBoolean("spatializer", false)
    fun saveSpatialWidth(value: Int) = prefs.edit().putInt("spatial_width", value).apply()
    fun getSpatialWidth(): Int = prefs.getInt("spatial_width", 50)

    fun saveTube(enabled: Boolean) = prefs.edit().putBoolean("tube", enabled).apply()
    fun getTube(): Boolean = prefs.getBoolean("tube", false)

    fun saveMp3Restorer(enabled: Boolean) = prefs.edit().putBoolean("mp3_restore", enabled).apply()
    fun getMp3Restorer(): Boolean = prefs.getBoolean("mp3_restore", false)
    fun saveExciterIntensity(value: Int) = prefs.edit().putInt("exciter_intensity", value).apply()
    fun getExciterIntensity(): Int = prefs.getInt("exciter_intensity", 50)

    fun saveCrossfeed(enabled: Boolean) = prefs.edit().putBoolean("crossfeed", enabled).apply()
    fun getCrossfeed(): Boolean = prefs.getBoolean("crossfeed", false)

    fun saveSmart(enabled: Boolean) = prefs.edit().putBoolean("smart", enabled).apply()
    fun getSmart(): Boolean = prefs.getBoolean("smart", false)

    fun saveAutoEqEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("auto_eq_enabled", enabled).apply()

    fun getAutoEqEnabled(): Boolean = prefs.getBoolean("auto_eq_enabled", false)
    fun saveCurrentAutoEqProfile(profile: AutoEqProfile?) {
        if (profile == null) prefs.edit().remove("current_auto_eq_profile_json")
            .apply() else prefs.edit()
            .putString("current_auto_eq_profile_json", gson.toJson(profile)).apply()
    }

    fun getCurrentAutoEqProfile(): AutoEqProfile? {
        val json =
            prefs.getString("current_auto_eq_profile_json", null) ?: return null; return try {
            gson.fromJson(json, AutoEqProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveAutoDetectHeadphones(enabled: Boolean) =
        prefs.edit().putBoolean("auto_detect_headphones", enabled).apply()

    fun getAutoDetectHeadphones(): Boolean = prefs.getBoolean("auto_detect_headphones", true)

    fun saveThemeMode(mode: Int) = prefs.edit().putInt("theme_mode", mode).apply()
    fun getThemeMode(): Int = prefs.getInt("theme_mode", 2)
    fun saveSecureMode(enabled: Boolean) = prefs.edit().putBoolean("secure_mode", enabled).apply()
    fun getSecureMode(): Boolean = prefs.getBoolean("secure_mode", false)
    fun saveFossWearEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("foss_wear_enabled", enabled).apply()

    fun getFossWearEnabled(): Boolean = prefs.getBoolean("foss_wear_enabled", false)

    // ==========================================
    // Продвинутые настройки
    // ==========================================

    // Главный рубильник
    fun saveAdvancedSettingsEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("advanced_settings_enabled", enabled).apply()

    fun getAdvancedSettingsEnabled(): Boolean = prefs.getBoolean("advanced_settings_enabled", false)

    // Базовые функции
    fun saveFeatureVisuals(enabled: Boolean) =
        prefs.edit().putBoolean("adv_feature_visuals", enabled).apply()

    fun getFeatureVisuals(): Boolean = prefs.getBoolean("adv_feature_visuals", true)

    fun saveFeatureStreaming(enabled: Boolean) =
        prefs.edit().putBoolean("adv_feature_streaming", enabled).apply()

    fun getFeatureStreaming(): Boolean = prefs.getBoolean("adv_feature_streaming", true)

    fun saveFeatureEqualizer(enabled: Boolean) =
        prefs.edit().putBoolean("adv_feature_equalizer", enabled).apply()

    fun getFeatureEqualizer(): Boolean = prefs.getBoolean("adv_feature_equalizer", true)

    // Модуль плейлистов
    fun saveFeaturePlaylists(enabled: Boolean) = prefs.edit().putBoolean("adv_feature_playlists", enabled).apply()
    fun getFeaturePlaylists(): Boolean = prefs.getBoolean("adv_feature_playlists", true)

    fun savePlaylistDynamicColors(enabled: Boolean) = prefs.edit().putBoolean("adv_playlist_dynamic_colors", enabled).apply()
    fun getPlaylistDynamicColors(): Boolean = prefs.getBoolean("adv_playlist_dynamic_colors", true)

    fun savePlaylistTrackCovers(enabled: Boolean) = prefs.edit().putBoolean("adv_playlist_track_covers", enabled).apply()
    fun getPlaylistTrackCovers(): Boolean = prefs.getBoolean("adv_playlist_track_covers", true)

    fun savePlaylistBanners(enabled: Boolean) = prefs.edit().putBoolean("adv_playlist_banners", enabled).apply()
    fun getPlaylistBanners(): Boolean = prefs.getBoolean("adv_playlist_banners", true)

    fun savePlaylistAnimations(enabled: Boolean) = prefs.edit().putBoolean("adv_playlist_animations", enabled).apply()
    fun getPlaylistAnimations(): Boolean = prefs.getBoolean("adv_playlist_animations", true)
}