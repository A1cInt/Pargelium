package com.alcint.pargelium

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class PlaylistModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackIds: List<Long>,
    val coverUri: String? = null,
    val bannerUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
data class PlayEvent(val trackId: Long, val timestamp: Long)
data class TrackBookmark(val id: String = UUID.randomUUID().toString(), val name: String, val timeMs: Long)

data class ArtistMetadata(
    val avatarUri: String? = null,
    val bannerUri: String? = null,
    val description: String = ""
)

data class JamesDspFullSnapshot(
    val name: String,
    val eq15Enabled: Boolean = false,
    val eqFilterMode: Int = 2,
    val eqInterpMode: Int = 0,
    val eqGains15: List<Float> = List(15) { 0f },
    val companderEnabled: Boolean = false,
    val companderTc: Float = 0.22f,
    val companderGranularity: Int = 2,
    val companderTfRes: Int = 0,
    val companderGains: List<Float> = List(7) { 0f },
    val bassEnabled: Boolean = false,
    val bassGain: Int = 6,
    val tubeEnabled: Boolean = false,
    val tubeDrive: Float = 2.0f,
    val ddcEnabled: Boolean = false,
    val ddcPresetId: String = "dt770_80",
    val convolverEnabled: Boolean = false,
    val convolverPresetId: Int = 0,
    val convolverWetMix: Float = 100f,
    val convolverPreDelay: Float = 0f,
    val liveprogEnabled: Boolean = false,
    val liveprogScriptId: Int = 0,
    val liveprogParams: List<Float> = listOf(0.35f, 0.74f, 0.42f, 1.0f, 80f, 0.71f, 0f, 0f),
    val reverbEnabled: Boolean = false,
    val reverbPreset: Int = 0,
    val stereoWideEnabled: Boolean = false,
    val stereoWidth: Float = 60f,
    val bs2bEnabled: Boolean = false,
    val bs2bMode: Int = 0,
    val limThreshold: Float = -0.5f,
    val limRelease: Float = 60f,
    val postGain: Float = 0f
)

object PrefsManager {
    private const val PREFS_NAME = "pargelium_audio_prefs"
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        PlaylistDatabase.init(context)
    }

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

    fun addTrackToPlaylist(playlistId: String, trackId: Long) = PlaylistDatabase.addTrackToPlaylist(playlistId, trackId)
    fun removeTrackFromPlaylist(playlistId: String, trackId: Long) = PlaylistDatabase.removeTrackFromPlaylist(playlistId, trackId)

    fun saveCustomCanvas(trackKey: String, uri: String?) {
        if (uri == null) prefs.edit().remove("canvas_$trackKey").apply() else prefs.edit().putString("canvas_$trackKey", uri).apply()
    }

    fun getCustomCanvas(trackKey: String): String? = prefs.getString("canvas_$trackKey", null)

    fun getTrackBookmarks(trackKey: String): List<TrackBookmark> {
        val json = prefs.getString("bookmarks_$trackKey", "[]")
        val type = object : TypeToken<List<TrackBookmark>>() {}.type
        return try {
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
    fun saveLastQueue(trackIds: List<Long>) = prefs.edit().putString("last_queue", gson.toJson(trackIds)).apply()

    fun getLastQueue(): List<Long> {
        val json = prefs.getString("last_queue", "[]")
        val type = object : TypeToken<List<Long>>() {}.type
        return try {
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

    private var cachedPlayEvents: MutableList<PlayEvent>? = null

    fun recordPlay(trackId: Long) {
        val events = (cachedPlayEvents ?: getPlayEvents().toMutableList()).also { cachedPlayEvents = it }
        events.add(PlayEvent(trackId, System.currentTimeMillis()))
        if (events.size > 2500) {
            events.subList(0, events.size - 2500).clear()
        }
        prefs.edit().putString("play_events_v2", gson.toJson(events)).apply()
    }

    fun getPlayEvents(): List<PlayEvent> {
        cachedPlayEvents?.let { return it }
        val json = prefs.getString("play_events_v2", "[]")
        val type = object : TypeToken<List<PlayEvent>>() {}.type
        val loaded: List<PlayEvent> = try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        cachedPlayEvents = loaded.toMutableList()
        return loaded
    }

    fun getTopTracksIds(days: Int): Map<Long, Int> {
        val events = getPlayEvents()
        val cutoff = if (days == -1) 0L else System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        return events.filter { it.timestamp >= cutoff }.groupingBy { it.trackId }.eachCount()
    }

    fun saveEnabled(enabled: Boolean) = prefs.edit().putBoolean("global_enabled", enabled).apply()
    fun getEnabled(): Boolean = prefs.getBoolean("global_enabled", true)

    private var cachedEqGains: FloatArray? = null
    private var cachedEqGainsStr: String? = null

    fun saveEqGains(gains: FloatArray) = prefs.edit().putString("eq_gains", gains.joinToString(",")).apply()

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

    fun saveUserEqEnabled(enabled: Boolean) = prefs.edit().putBoolean("user_eq_enabled", enabled).apply()
    fun getUserEqEnabled(): Boolean = prefs.getBoolean("user_eq_enabled", false)

    fun saveEq15BandMode(enabled: Boolean) = prefs.edit().putBoolean("dsp_eq_15band_mode", enabled).apply()
    fun getEq15BandMode(): Boolean = prefs.getBoolean("dsp_eq_15band_mode", true)

    fun saveEqFilterMode(mode: Int) = prefs.edit().putInt("dsp_eq_filter_mode", mode).apply()
    fun getEqFilterMode(): Int = prefs.getInt("dsp_eq_filter_mode", 2)

    fun saveEqInterpolationMode(mode: Int) = prefs.edit().putInt("dsp_eq_interp_mode", mode).apply()
    fun getEqInterpolationMode(): Int = prefs.getInt("dsp_eq_interp_mode", 0)

    fun saveUserEqGains(gains: FloatArray) {
        val str = gains.joinToString(",")
        cachedUserEqGainsStr = str
        cachedUserEqGains = gains.copyOf()
        prefs.edit().putString("user_eq_gains", str).apply()
    }

    fun getUserEqGains(): FloatArray {
        val str = prefs.getString("user_eq_gains", "") ?: ""
        if (cachedUserEqGains != null && cachedUserEqGainsStr == str) return cachedUserEqGains!!
        cachedUserEqGainsStr = str
        cachedUserEqGains = try {
            val parsed = str.split(",").map { it.toFloat() }.toFloatArray()
            if (parsed.size >= 10) parsed else FloatArray(10) { 0f }
        } catch (e: Exception) {
            FloatArray(10) { 0f }
        }
        return cachedUserEqGains!!
    }

    private var cachedUserEqGains15: FloatArray? = null
    private var cachedUserEqGains15Str: String? = null

    fun saveUserEqGains15(gains: FloatArray) {
        val str = gains.joinToString(",")
        cachedUserEqGains15Str = str
        cachedUserEqGains15 = gains.copyOf()
        prefs.edit().putString("user_eq_gains_15", str).apply()
    }

    fun getUserEqGains15(): FloatArray {
        val str = prefs.getString("user_eq_gains_15", "") ?: ""
        if (cachedUserEqGains15 != null && cachedUserEqGains15Str == str) return cachedUserEqGains15!!
        cachedUserEqGains15Str = str
        cachedUserEqGains15 = try {
            val parsed = str.split(",").map { it.toFloat() }.toFloatArray()
            if (parsed.size == 15) parsed else FloatArray(15) { 0f }
        } catch (_: Exception) {
            FloatArray(15) { 0f }
        }
        return cachedUserEqGains15!!
    }

    fun saveCustomGraphicEqString(str: String) = prefs.edit().putString("dsp_custom_graphiceq", str).apply()
    fun getCustomGraphicEqString(): String = prefs.getString("dsp_custom_graphiceq", "") ?: ""

    private var cachedCompanderGains: FloatArray? = null
    private var cachedCompanderGainsStr: String? = null

    fun saveCompanderEnabled(enabled: Boolean) = prefs.edit().putBoolean("dsp_compander_enabled", enabled).apply()
    fun getCompanderEnabled(): Boolean = prefs.getBoolean("dsp_compander_enabled", false)
    fun saveCompanderTimeConstant(tc: Float) = prefs.edit().putFloat("dsp_compander_tc", tc).apply()
    fun getCompanderTimeConstant(): Float = prefs.getFloat("dsp_compander_tc", 0.22f)
    fun saveCompanderGranularity(gran: Int) = prefs.edit().putInt("dsp_compander_gran", gran).apply()
    fun getCompanderGranularity(): Int = prefs.getInt("dsp_compander_gran", 2)
    fun saveCompanderTfResolution(res: Int) = prefs.edit().putInt("dsp_compander_tfres", res).apply()
    fun getCompanderTfResolution(): Int = prefs.getInt("dsp_compander_tfres", 0)

    fun saveCompanderGains(gains: FloatArray) {
        val str = gains.joinToString(",")
        cachedCompanderGainsStr = str
        cachedCompanderGains = gains.copyOf()
        prefs.edit().putString("dsp_compander_gains", str).apply()
    }

    fun getCompanderGains(): FloatArray {
        val str = prefs.getString("dsp_compander_gains", "") ?: ""
        if (cachedCompanderGains != null && cachedCompanderGainsStr == str) return cachedCompanderGains!!
        cachedCompanderGainsStr = str
        cachedCompanderGains = try {
            val parsed = str.split(",").map { it.toFloat() }.toFloatArray()
            if (parsed.size == 7) parsed else FloatArray(7) { 0f }
        } catch (_: Exception) {
            FloatArray(7) { 0f }
        }
        return cachedCompanderGains!!
    }

    // ViPER-DDC
    fun saveDdcEnabled(enabled: Boolean) = prefs.edit().putBoolean("dsp_ddc_enabled", enabled).apply()
    fun getDdcEnabled(): Boolean = prefs.getBoolean("dsp_ddc_enabled", false)
    fun saveDdcPresetId(id: String) = prefs.edit().putString("dsp_ddc_preset_id", id).apply()
    fun getDdcPresetId(): String = prefs.getString("dsp_ddc_preset_id", "dt770_80") ?: "dt770_80"
    fun saveCustomVdcContent(vdc: String?) {
        if (vdc == null) prefs.edit().remove("dsp_custom_vdc").apply() else prefs.edit().putString("dsp_custom_vdc", vdc).apply()
    }
    fun getCustomVdcContent(): String? = prefs.getString("dsp_custom_vdc", null)

    // Convolver
    fun saveConvolverEnabled(enabled: Boolean) = prefs.edit().putBoolean("dsp_convolver_enabled", enabled).apply()
    fun getConvolverEnabled(): Boolean = prefs.getBoolean("dsp_convolver_enabled", false)
    fun saveConvolverPresetId(id: Int) = prefs.edit().putInt("dsp_convolver_preset_id", id).apply()
    fun getConvolverPresetId(): Int = prefs.getInt("dsp_convolver_preset_id", 0)
    fun saveConvolverWetMix(mix: Float) = prefs.edit().putFloat("dsp_convolver_wet_mix", mix).apply()
    fun getConvolverWetMix(): Float = prefs.getFloat("dsp_convolver_wet_mix", 100f)
    fun saveConvolverPreDelayMs(ms: Float) = prefs.edit().putFloat("dsp_convolver_predelay_ms", ms).apply()
    fun getConvolverPreDelayMs(): Float = prefs.getFloat("dsp_convolver_predelay_ms", 0f)
    fun saveConvolverTailTrim(trim: Float) = prefs.edit().putFloat("dsp_convolver_tail_trim", trim).apply()
    fun getConvolverTailTrim(): Float = prefs.getFloat("dsp_convolver_tail_trim", 100f)
    fun saveConvolverInvertPhase(inv: Boolean) = prefs.edit().putBoolean("dsp_convolver_inv_phase", inv).apply()
    fun getConvolverInvertPhase(): Boolean = prefs.getBoolean("dsp_convolver_inv_phase", false)

    // EEL2 Liveprog
    private var cachedLiveprogParams: FloatArray? = null
    private var cachedLiveprogParamsStr: String? = null

    fun saveLiveprogEnabled(enabled: Boolean) = prefs.edit().putBoolean("dsp_liveprog_enabled", enabled).apply()
    fun getLiveprogEnabled(): Boolean = prefs.getBoolean("dsp_liveprog_enabled", false)
    fun saveLiveprogScriptId(id: Int) = prefs.edit().putInt("dsp_liveprog_script_id", id).apply()
    fun getLiveprogScriptId(): Int = prefs.getInt("dsp_liveprog_script_id", 0)

    fun saveLiveprogParams(params: FloatArray) {
        val str = params.joinToString(",")
        cachedLiveprogParamsStr = str
        cachedLiveprogParams = params.copyOf()
        prefs.edit().putString("dsp_liveprog_params", str).apply()
    }

    fun getLiveprogParams(): FloatArray {
        val str = prefs.getString("dsp_liveprog_params", "") ?: ""
        if (cachedLiveprogParams != null && cachedLiveprogParamsStr == str) return cachedLiveprogParams!!
        cachedLiveprogParamsStr = str
        cachedLiveprogParams = try {
            val parsed = str.split(",").map { it.toFloat() }.toFloatArray()
            if (parsed.size >= 4) {
                FloatArray(8) { idx -> parsed.getOrElse(idx) { 0f } }
            } else {
                floatArrayOf(0.35f, 0.74f, 0.42f, 1.0f, 80.0f, 0.707f, 0.0f, 0.0f)
            }
        } catch (_: Exception) {
            floatArrayOf(0.35f, 0.74f, 0.42f, 1.0f, 80.0f, 0.707f, 0.0f, 0.0f)
        }
        return cachedLiveprogParams!!
    }

    fun saveCustomEelCode(code: String?) {
        if (code == null) prefs.edit().remove("dsp_custom_eel_code").apply() else prefs.edit().putString("dsp_custom_eel_code", code).apply()
    }
    fun getCustomEelCode(): String? = prefs.getString("dsp_custom_eel_code", null)

    // Full DSP Snapshots
    fun getSavedDspSnapshots(): List<JamesDspFullSnapshot> {
        val json = prefs.getString("dsp_saved_snapshots", "[]")
        val type = object : TypeToken<List<JamesDspFullSnapshot>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDspSnapshots(list: List<JamesDspFullSnapshot>) {
        prefs.edit().putString("dsp_saved_snapshots", gson.toJson(list)).apply()
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

    fun saveAutoEqEnabled(enabled: Boolean) = prefs.edit().putBoolean("auto_eq_enabled", enabled).apply()
    fun getAutoEqEnabled(): Boolean = prefs.getBoolean("auto_eq_enabled", false)

    fun saveCurrentAutoEqProfile(profile: AutoEqProfile?) {
        if (profile == null) prefs.edit().remove("current_auto_eq_profile_json").apply() else prefs.edit().putString("current_auto_eq_profile_json", gson.toJson(profile)).apply()
    }

    fun getCurrentAutoEqProfile(): AutoEqProfile? {
        val json = prefs.getString("current_auto_eq_profile_json", null) ?: return null
        return try {
            gson.fromJson(json, AutoEqProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveAutoDetectHeadphones(enabled: Boolean) = prefs.edit().putBoolean("auto_detect_headphones", enabled).apply()
    fun getAutoDetectHeadphones(): Boolean = prefs.getBoolean("auto_detect_headphones", true)

    fun saveThemeMode(mode: Int) = prefs.edit().putInt("theme_mode", mode).apply()
    fun getThemeMode(): Int = prefs.getInt("theme_mode", 2)
    fun saveSecureMode(enabled: Boolean) = prefs.edit().putBoolean("secure_mode", enabled).apply()
    fun getSecureMode(): Boolean = prefs.getBoolean("secure_mode", false)
    fun saveFossWearEnabled(enabled: Boolean) = prefs.edit().putBoolean("foss_wear_enabled", enabled).apply()
    fun getFossWearEnabled(): Boolean = prefs.getBoolean("foss_wear_enabled", false)

    fun saveAdvancedSettingsEnabled(enabled: Boolean) = prefs.edit().putBoolean("advanced_settings_enabled", enabled).apply()
    fun getAdvancedSettingsEnabled(): Boolean = prefs.getBoolean("advanced_settings_enabled", false)

    fun saveFeatureVisuals(enabled: Boolean) = prefs.edit().putBoolean("adv_feature_visuals", enabled).apply()
    fun getFeatureVisuals(): Boolean = prefs.getBoolean("adv_feature_visuals", true)

    fun saveFeatureStreaming(enabled: Boolean) = prefs.edit().putBoolean("adv_feature_streaming", enabled).apply()
    fun getFeatureStreaming(): Boolean = prefs.getBoolean("adv_feature_streaming", true)

    fun saveFeatureEqualizer(enabled: Boolean) = prefs.edit().putBoolean("adv_feature_equalizer", enabled).apply()
    fun getFeatureEqualizer(): Boolean = prefs.getBoolean("adv_feature_equalizer", true)

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

    fun saveShowTrackInfoBar(enabled: Boolean) = prefs.edit().putBoolean("show_track_info_bar", enabled).apply()
    fun getShowTrackInfoBar(): Boolean = prefs.getBoolean("show_track_info_bar", true)

    fun saveAutoUpdateEnabled(enabled: Boolean) = prefs.edit().putBoolean("auto_update_enabled", enabled).apply()
    fun getAutoUpdateEnabled(): Boolean = prefs.getBoolean("auto_update_enabled", true)

    fun saveLastUpdateCheckTime(timeMs: Long) = prefs.edit().putLong("last_update_check_time", timeMs).apply()
    fun getLastUpdateCheckTime(): Long = prefs.getLong("last_update_check_time", 0L)

    fun saveSkippedVersion(version: String) = prefs.edit().putString("skipped_update_version", version).apply()
    fun getSkippedVersion(): String? = prefs.getString("skipped_update_version", null)

    fun saveWidgetState(title: String, artist: String, isPlaying: Boolean) {
        prefs.edit()
            .putString("widget_title", title)
            .putString("widget_artist", artist)
            .putBoolean("widget_is_playing", isPlaying)
            .apply()
    }

    fun getWidgetTitle(): String = prefs.getString("widget_title", "Pargelium") ?: "Pargelium"
    fun getWidgetArtist(): String = prefs.getString("widget_artist", "Выберите трек") ?: "Выберите трек"
    fun getWidgetIsPlaying(): Boolean = prefs.getBoolean("widget_is_playing", false)

    // Vacuum Tube
    fun getTubeEnabled(): Boolean = prefs.getBoolean("dsp_tube_enabled", false)
    fun saveTubeEnabled(enabled: Boolean) = prefs.edit().putBoolean("dsp_tube_enabled", enabled).apply()
    fun getTubeDrive(): Float = prefs.getFloat("dsp_tube_drive", 2.0f)
    fun saveTubeDrive(drive: Float) = prefs.edit().putFloat("dsp_tube_drive", drive).apply()

    // Stereo Widener
    fun getStereoWideEnabled(): Boolean = prefs.getBoolean("dsp_stereowide_enabled", false)
    fun saveStereoWideEnabled(enabled: Boolean) = prefs.edit().putBoolean("dsp_stereowide_enabled", enabled).apply()
    fun getStereoWidth(): Float = prefs.getFloat("dsp_stereowide_width", 60.0f)
    fun saveStereoWidth(width: Float) = prefs.edit().putFloat("dsp_stereowide_width", width).apply()

    // BS2B Crossfeed
    fun getBs2bEnabled(): Boolean = prefs.getBoolean("dsp_bs2b_enabled", false)
    fun saveBs2bEnabled(enabled: Boolean) = prefs.edit().putBoolean("dsp_bs2b_enabled", enabled).apply()
    fun getBs2bMode(): Int = prefs.getInt("dsp_bs2b_mode", 0)
    fun saveBs2bMode(mode: Int) = prefs.edit().putInt("dsp_bs2b_mode", mode).apply()

    // Master Limiter
    fun getLimThreshold(): Float = prefs.getFloat("dsp_lim_threshold", -0.5f)
    fun saveLimThreshold(th: Float) = prefs.edit().putFloat("dsp_lim_threshold", th).apply()
    fun getLimRelease(): Float = prefs.getFloat("dsp_lim_release", 60.0f)
    fun saveLimRelease(rel: Float) = prefs.edit().putFloat("dsp_lim_release", rel).apply()
    fun getPostGain(): Float = prefs.getFloat("dsp_post_gain", 0.0f)
    fun savePostGain(gain: Float) = prefs.edit().putFloat("dsp_post_gain", gain).apply()

    private const val KEY_NAV_BAR_STYLE = "nav_bar_style"

    fun saveNavBarStyle(style: Int) {
        prefs.edit().putInt(KEY_NAV_BAR_STYLE, style).apply()
    }

    fun getNavBarStyle(): Int {
        return prefs.getInt(KEY_NAV_BAR_STYLE, 0)
    }

    private const val KEY_PLAYER_BUTTON_STYLE = "player_button_style"

    fun savePlayerButtonStyle(style: Int) {
        prefs.edit().putInt(KEY_PLAYER_BUTTON_STYLE, style).apply()
    }

    fun getPlayerButtonStyle(): Int {
        return prefs.getInt(KEY_PLAYER_BUTTON_STYLE, 0)
    }
}