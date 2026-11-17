package com.reelmonster.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reelmonster.app.domain.model.MunchieTheme
import com.reelmonster.app.domain.model.ReactionStyle
import com.reelmonster.app.domain.model.TargetApp
import com.reelmonster.app.domain.model.TrackingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reel_monster_preferences",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, "reel_monster_prefs")
        )
    }
)

class UserPreferencesManager(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    // Convenience constructor using context DataStore
    constructor(context: Context) : this(context.dataStore)

    companion object {
        val KEY_OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val KEY_TRACKING_MODE = stringPreferencesKey("tracking_mode")
        val KEY_SOUND_FX = booleanPreferencesKey("sound_fx_enabled")
        val KEY_HAPTIC = booleanPreferencesKey("haptic_enabled")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_EDGE_SNAPPING = booleanPreferencesKey("edge_snapping")
        val KEY_SCALE = floatPreferencesKey("bubble_scale")
        val KEY_OPACITY = floatPreferencesKey("bubble_opacity")
        val KEY_MONITOR_INSTAGRAM = booleanPreferencesKey("monitor_instagram")
        val KEY_MONITOR_YOUTUBE = booleanPreferencesKey("monitor_youtube")
        val KEY_TRACKING_PAUSED = booleanPreferencesKey("tracking_paused")
        val KEY_MONSTER_THEME = stringPreferencesKey("monster_theme")
        val KEY_REACTION_STYLE = stringPreferencesKey("reaction_style")
        val KEY_MASCOT_ANIMATION_ENABLED = booleanPreferencesKey("mascot_animation_enabled")
        val KEY_GLOBAL_TRACKING_ENABLED = booleanPreferencesKey("global_tracking_enabled")
        val KEY_DAILY_RESET_DATE = stringPreferencesKey("daily_reset_date")
        val KEY_DAILY_RESET_COUNT = intPreferencesKey("daily_reset_count")

        // Phase 13: Focus Mode Keys
        val KEY_FOCUS_OVERLAY_ENABLED = booleanPreferencesKey("focus_overlay_enabled")
        val KEY_FOCUS_REMINDER_COOLDOWN_MINS = intPreferencesKey("focus_reminder_cooldown_mins")
        val KEY_SAVED_FOCUS_TYPE = stringPreferencesKey("saved_focus_type")
        val KEY_SAVED_FOCUS_TARGET_MS = longPreferencesKey("saved_focus_target_ms")
        val KEY_SAVED_FOCUS_ELAPSED_MS = longPreferencesKey("saved_focus_elapsed_ms")
        val KEY_SAVED_FOCUS_STATUS = stringPreferencesKey("saved_focus_status")
        val KEY_SAVED_FOCUS_START_WALL_TIME = longPreferencesKey("saved_focus_start_wall_time")

        const val MAX_DAILY_RESETS = 5
    }

    // Raw preferences flow with safe error handling
    val preferencesFlow: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    // Reactive Flows
    val isOverlayEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[KEY_OVERLAY_ENABLED] ?: true }
    val isTrackingPausedFlow: Flow<Boolean> = preferencesFlow.map { it[KEY_TRACKING_PAUSED] ?: false }

    val trackingModeFlow: Flow<TrackingMode> = preferencesFlow.map { prefs ->
        val modeStr = prefs[KEY_TRACKING_MODE] ?: TrackingMode.LEVEL_1_MANUAL.name
        try { TrackingMode.valueOf(modeStr) } catch (_: Exception) { TrackingMode.LEVEL_1_MANUAL }
    }

    val isOnboardingCompletedFlow: Flow<Boolean> = preferencesFlow.map { it[KEY_ONBOARDING_DONE] ?: false }
    val isSoundFxEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[KEY_SOUND_FX] ?: false }
    val isHapticEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[KEY_HAPTIC] ?: false }
    val isEdgeSnappingEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[KEY_EDGE_SNAPPING] ?: true }
    val bubbleScaleFlow: Flow<Float> = preferencesFlow.map { it[KEY_SCALE] ?: 1.0f }
    val bubbleOpacityFlow: Flow<Float> = preferencesFlow.map { it[KEY_OPACITY] ?: 0.9f }
    val monitorInstagramFlow: Flow<Boolean> = preferencesFlow.map { it[KEY_MONITOR_INSTAGRAM] ?: true }
    val monitorYouTubeFlow: Flow<Boolean> = preferencesFlow.map { it[KEY_MONITOR_YOUTUBE] ?: true }

    val monsterThemeFlow: Flow<MunchieTheme> = preferencesFlow.map { prefs ->
        val themeStr = prefs[KEY_MONSTER_THEME] ?: MunchieTheme.MINT_CLASSIC.name
        try { MunchieTheme.valueOf(themeStr) } catch (_: Exception) { MunchieTheme.MINT_CLASSIC }
    }

    val reactionStyleFlow: Flow<ReactionStyle> = preferencesFlow.map { prefs ->
        val styleStr = prefs[KEY_REACTION_STYLE] ?: ReactionStyle.BOUNCE.name
        try { ReactionStyle.valueOf(styleStr) } catch (_: Exception) { ReactionStyle.BOUNCE }
    }

    val isMascotAnimationEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[KEY_MASCOT_ANIMATION_ENABLED] ?: true }
    val isGlobalTrackingEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[KEY_GLOBAL_TRACKING_ENABLED] ?: true }
    val dailyResetDateFlow: Flow<String> = preferencesFlow.map { it[KEY_DAILY_RESET_DATE] ?: "" }
    val dailyResetCountFlow: Flow<Int> = preferencesFlow.map { it[KEY_DAILY_RESET_COUNT] ?: 0 }

    // Phase 13: Focus Mode Flows
    val isFocusOverlayEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[KEY_FOCUS_OVERLAY_ENABLED] ?: true }
    val focusReminderCooldownMinsFlow: Flow<Int> = preferencesFlow.map { it[KEY_FOCUS_REMINDER_COOLDOWN_MINS] ?: 3 }

    // StateFlow properties for Compose UI consumption
    val isOverlayEnabled: StateFlow<Boolean> = isOverlayEnabledFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    private val _trackingMode = MutableStateFlow(TrackingMode.LEVEL_1_MANUAL)
    val trackingMode: StateFlow<TrackingMode> = _trackingMode.asStateFlow()

    val isOnboardingCompleted: StateFlow<Boolean> = isOnboardingCompletedFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val isSoundFxEnabled: StateFlow<Boolean> = isSoundFxEnabledFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val isHapticEnabled: StateFlow<Boolean> = isHapticEnabledFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val isEdgeSnappingEnabled: StateFlow<Boolean> = isEdgeSnappingEnabledFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    val monitorInstagram: StateFlow<Boolean> = monitorInstagramFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    val monitorYouTube: StateFlow<Boolean> = monitorYouTubeFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    private val _isTrackingPaused = MutableStateFlow(false)
    val isTrackingPaused: StateFlow<Boolean> = _isTrackingPaused.asStateFlow()

    val monsterTheme: StateFlow<MunchieTheme> = monsterThemeFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = MunchieTheme.MINT_CLASSIC
    )

    val reactionStyle: StateFlow<ReactionStyle> = reactionStyleFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ReactionStyle.BOUNCE
    )

    val isMascotAnimationEnabled: StateFlow<Boolean> = isMascotAnimationEnabledFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    private val _isGlobalTrackingEnabled = MutableStateFlow(true)
    val isGlobalTrackingEnabled: StateFlow<Boolean> = _isGlobalTrackingEnabled.asStateFlow()

    private val _dailyResetDate = MutableStateFlow("")
    val dailyResetDate: StateFlow<String> = _dailyResetDate.asStateFlow()

    private val _dailyResetCount = MutableStateFlow(0)
    val dailyResetCount: StateFlow<Int> = _dailyResetCount.asStateFlow()

    // Phase 13: Focus Mode StateFlows
    val isFocusOverlayEnabled: StateFlow<Boolean> = isFocusOverlayEnabledFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    val focusReminderCooldownMins: StateFlow<Int> = focusReminderCooldownMinsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = 3
    )

    val savedFocusType: StateFlow<String?> = preferencesFlow.map { it[KEY_SAVED_FOCUS_TYPE] }
        .stateIn(scope, SharingStarted.Eagerly, null)
    val savedFocusTargetMs: StateFlow<Long> = preferencesFlow.map { it[KEY_SAVED_FOCUS_TARGET_MS] ?: 0L }
        .stateIn(scope, SharingStarted.Eagerly, 0L)
    val savedFocusElapsedMs: StateFlow<Long> = preferencesFlow.map { it[KEY_SAVED_FOCUS_ELAPSED_MS] ?: 0L }
        .stateIn(scope, SharingStarted.Eagerly, 0L)
    val savedFocusStatus: StateFlow<String?> = preferencesFlow.map { it[KEY_SAVED_FOCUS_STATUS] }
        .stateIn(scope, SharingStarted.Eagerly, null)
    val savedFocusStartWallTime: StateFlow<Long> = preferencesFlow.map { it[KEY_SAVED_FOCUS_START_WALL_TIME] ?: 0L }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    @Volatile private var isResetLoadedFromDisk = false
    @Volatile private var cachedResetDate: String = ""
    @Volatile private var cachedResetCount: Int = 0

    init {
        // Phase 17 (STAB-04): read the persisted tracking flags SYNCHRONOUSLY
        // at construction. Two async-seed designs both raced and both failed a
        // test: continuous collectors re-applied stale emissions and undid a
        // pause -> resume (test06), and generation-guarded one-shot seeds could
        // still apply a previous test's persisted global=false after a clear
        // (test09: diagnostic showed global=false). A single blocking read here
        // is deterministic: after construction the in-memory values are
        // authoritative and only the setters / clearPreferences change them.
        // The read is one small local file, once per process start.
        runBlocking {
            _isGlobalTrackingEnabled.value = isGlobalTrackingEnabledFlow.first()
            _isTrackingPaused.value = isTrackingPausedFlow.first()
            _trackingMode.value = trackingModeFlow.first()
        }
        scope.launch {
            preferencesFlow.collect { prefs ->
                synchronized(this@UserPreferencesManager) {
                    if (!isResetLoadedFromDisk) {
                        val date = prefs[KEY_DAILY_RESET_DATE] ?: ""
                        val count = prefs[KEY_DAILY_RESET_COUNT] ?: 0
                        cachedResetDate = date
                        cachedResetCount = count
                        _dailyResetDate.value = date
                        _dailyResetCount.value = count
                        isResetLoadedFromDisk = true
                    }
                }
            }
        }
    }

    val isTrackingActive: Boolean get() = isGlobalTrackingEnabled.value && !isTrackingPaused.value

    // Suspending update functions
    suspend fun updateOverlayEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_OVERLAY_ENABLED] = enabled }
    }

    suspend fun updateTrackingPaused(paused: Boolean) {
        _isTrackingPaused.value = paused
        dataStore.edit { it[KEY_TRACKING_PAUSED] = paused }
    }

    suspend fun updateTrackingMode(mode: TrackingMode) {
        _trackingMode.value = mode
        dataStore.edit { it[KEY_TRACKING_MODE] = mode.name }
    }

    suspend fun updateOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_DONE] = completed }
    }

    suspend fun updateSoundFxEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SOUND_FX] = enabled }
    }

    suspend fun updateHapticEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_HAPTIC] = enabled }
    }

    suspend fun updateEdgeSnappingEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_EDGE_SNAPPING] = enabled }
    }

    suspend fun updateBubbleScale(scale: Float) {
        dataStore.edit { it[KEY_SCALE] = scale }
    }

    suspend fun updateBubbleOpacity(opacity: Float) {
        dataStore.edit { it[KEY_OPACITY] = opacity }
    }

    suspend fun updateMonitorInstagram(enabled: Boolean) {
        dataStore.edit { it[KEY_MONITOR_INSTAGRAM] = enabled }
    }

    suspend fun updateMonitorYouTube(enabled: Boolean) {
        dataStore.edit { it[KEY_MONITOR_YOUTUBE] = enabled }
    }

    suspend fun updateMonsterTheme(theme: MunchieTheme) {
        dataStore.edit { it[KEY_MONSTER_THEME] = theme.name }
    }

    suspend fun updateReactionStyle(style: ReactionStyle) {
        dataStore.edit { it[KEY_REACTION_STYLE] = style.name }
    }

    suspend fun updateMascotAnimationEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MASCOT_ANIMATION_ENABLED] = enabled }
    }

    suspend fun updateGlobalTrackingEnabled(enabled: Boolean) {
        _isGlobalTrackingEnabled.value = enabled
        dataStore.edit { it[KEY_GLOBAL_TRACKING_ENABLED] = enabled }
    }

    suspend fun updateDailyResetUsage(dateStr: String, count: Int) {
        isResetLoadedFromDisk = true
        synchronized(this) {
            if (cachedResetDate != dateStr || count >= cachedResetCount) {
                cachedResetDate = dateStr
                cachedResetCount = count
                _dailyResetDate.value = dateStr
                _dailyResetCount.value = count
            }
        }
        dataStore.edit {
            it[KEY_DAILY_RESET_DATE] = dateStr
            it[KEY_DAILY_RESET_COUNT] = count
        }
    }

    suspend fun clearPreferences() {
        isResetLoadedFromDisk = true
        synchronized(this) {
            cachedResetDate = ""
            cachedResetCount = 0
            _isGlobalTrackingEnabled.value = true
            _dailyResetDate.value = ""
            _dailyResetCount.value = 0
            _isTrackingPaused.value = false
            _trackingMode.value = TrackingMode.LEVEL_1_MANUAL
        }
        dataStore.edit { it.clear() }
    }

    // Convenience synchronous helper functions launching on scope
    fun setOverlayEnabled(enabled: Boolean) { scope.launch { updateOverlayEnabled(enabled) } }
    fun setTrackingMode(mode: TrackingMode) {
        _trackingMode.value = mode
        scope.launch { updateTrackingMode(mode) }
    }
    fun setGlobalTrackingEnabled(enabled: Boolean) {
        _isGlobalTrackingEnabled.value = enabled
        scope.launch { updateGlobalTrackingEnabled(enabled) }
    }
    fun setDailyResetUsage(dateStr: String, count: Int) {
        isResetLoadedFromDisk = true
        synchronized(this) {
            cachedResetDate = dateStr
            cachedResetCount = count
            _dailyResetDate.value = dateStr
            _dailyResetCount.value = count
        }
        scope.launch { updateDailyResetUsage(dateStr, count) }
    }

    /**
     * Returns the number of daily resets used today according to [dateStr].
     * If the recorded date is different, usage is 0 (automatic midnight renewal).
     */
    fun getDailyResetUsage(dateStr: String): Int {
        return synchronized(this) {
            if (cachedResetDate == dateStr) {
                cachedResetCount
            } else {
                0
            }
        }
    }

    /**
     * Returns the remaining resets available for [dateStr] (0 to 5).
     */
    fun getRemainingDailyResets(dateStr: String): Int {
        val used = getDailyResetUsage(dateStr)
        return (MAX_DAILY_RESETS - used).coerceIn(0, MAX_DAILY_RESETS)
    }
    fun setOnboardingCompleted(completed: Boolean) { scope.launch { updateOnboardingCompleted(completed) } }
    fun setSoundFxEnabled(enabled: Boolean) { scope.launch { updateSoundFxEnabled(enabled) } }
    fun setHapticEnabled(enabled: Boolean) { scope.launch { updateHapticEnabled(enabled) } }
    fun setEdgeSnappingEnabled(enabled: Boolean) { scope.launch { updateEdgeSnappingEnabled(enabled) } }
    fun setMonitorInstagram(enabled: Boolean) { scope.launch { updateMonitorInstagram(enabled) } }
    fun setMonitorYouTube(enabled: Boolean) { scope.launch { updateMonitorYouTube(enabled) } }
    fun setMonsterTheme(theme: MunchieTheme) { scope.launch { updateMonsterTheme(theme) } }
    fun setReactionStyle(style: ReactionStyle) { scope.launch { updateReactionStyle(style) } }
    fun setMascotAnimationEnabled(enabled: Boolean) { scope.launch { updateMascotAnimationEnabled(enabled) } }
    fun setTrackingPaused(paused: Boolean) {
        _isTrackingPaused.value = paused
        scope.launch { updateTrackingPaused(paused) }
    }
    fun pauseTracking() { setTrackingPaused(true) }
    fun resumeTracking() { setTrackingPaused(false) }

    fun isAppMonitored(app: TargetApp): Boolean {
        return when (app) {
            TargetApp.INSTAGRAM -> monitorInstagram.value
            TargetApp.YOUTUBE -> monitorYouTube.value
        }
    }

    // Phase 13: Focus Mode Updaters
    suspend fun updateFocusOverlayEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_FOCUS_OVERLAY_ENABLED] = enabled }
    }
    fun setFocusOverlayEnabled(enabled: Boolean) {
        scope.launch { updateFocusOverlayEnabled(enabled) }
    }

    suspend fun updateFocusReminderCooldownMins(mins: Int) {
        dataStore.edit { it[KEY_FOCUS_REMINDER_COOLDOWN_MINS] = mins.coerceIn(1, 15) }
    }
    fun setFocusReminderCooldownMins(mins: Int) {
        scope.launch { updateFocusReminderCooldownMins(mins) }
    }

    suspend fun saveActiveFocusSessionState(
        type: String,
        targetMs: Long,
        elapsedMs: Long,
        status: String,
        startWallTime: Long
    ) {
        dataStore.edit {
            it[KEY_SAVED_FOCUS_TYPE] = type
            it[KEY_SAVED_FOCUS_TARGET_MS] = targetMs
            it[KEY_SAVED_FOCUS_ELAPSED_MS] = elapsedMs
            it[KEY_SAVED_FOCUS_STATUS] = status
            it[KEY_SAVED_FOCUS_START_WALL_TIME] = startWallTime
        }
    }

    suspend fun clearActiveFocusSessionState() {
        dataStore.edit {
            it.remove(KEY_SAVED_FOCUS_TYPE)
            it.remove(KEY_SAVED_FOCUS_TARGET_MS)
            it.remove(KEY_SAVED_FOCUS_ELAPSED_MS)
            it.remove(KEY_SAVED_FOCUS_STATUS)
            it.remove(KEY_SAVED_FOCUS_START_WALL_TIME)
        }
    }
}
