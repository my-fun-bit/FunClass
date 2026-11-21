package com.myfun.funclass.core.database.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myfun.funclass.core.model.ReminderSettings
import com.myfun.funclass.core.model.TimeSchemeGeneratorConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "funclass_prefs")

@Singleton
class AppPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private object Keys {
        val ACTIVE_TABLE_ID = longPreferencesKey("active_table_id")
        val REMINDER_SETTINGS_JSON = stringPreferencesKey("reminder_settings_json")
        val TIME_SCHEME_GEN_CONFIGS_JSON = stringPreferencesKey("time_scheme_gen_configs_json")
        val NOTIFICATION_PERMISSION_PROMPTED = booleanPreferencesKey("notification_permission_prompted")
        val SHOW_HELPER_DESCRIPTIONS = booleanPreferencesKey("show_helper_descriptions")
    }

    val activeTableId: Flow<Long> = context.appDataStore.data.map { it[Keys.ACTIVE_TABLE_ID] ?: 0L }

    val reminderSettings: Flow<ReminderSettings> = context.appDataStore.data.map { prefs ->
        val raw = prefs[Keys.REMINDER_SETTINGS_JSON]
        if (raw.isNullOrBlank()) {
            ReminderSettings()
        } else {
            runCatching { json.decodeFromString<ReminderSettings>(raw) }.getOrElse { ReminderSettings() }
        }
    }

    val hasPromptedNotificationPermission: Flow<Boolean> =
        context.appDataStore.data.map { it[Keys.NOTIFICATION_PERMISSION_PROMPTED] ?: false }

    val showHelperDescriptions: Flow<Boolean> =
        context.appDataStore.data.map { it[Keys.SHOW_HELPER_DESCRIPTIONS] ?: true }

    val timeSchemeGeneratorConfigs: Flow<Map<Long, TimeSchemeGeneratorConfig>> = context.appDataStore.data.map { prefs ->
        val raw = prefs[Keys.TIME_SCHEME_GEN_CONFIGS_JSON]
        if (raw.isNullOrBlank()) {
            emptyMap()
        } else {
            runCatching {
                json.decodeFromString<Map<String, TimeSchemeGeneratorConfig>>(raw)
                    .mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v } }
                    .toMap()
            }.getOrElse { emptyMap() }
        }
    }

    suspend fun getActiveTableIdOnce(): Long = activeTableId.first()

    suspend fun setActiveTableId(id: Long) {
        context.appDataStore.edit { it[Keys.ACTIVE_TABLE_ID] = id }
    }

    suspend fun getReminderSettingsOnce(): ReminderSettings = reminderSettings.first()

    suspend fun updateReminderSettings(settings: ReminderSettings) {
        context.appDataStore.edit {
            it[Keys.REMINDER_SETTINGS_JSON] = json.encodeToString(settings)
        }
    }

    suspend fun setNotificationPermissionPrompted(prompted: Boolean) {
        context.appDataStore.edit { it[Keys.NOTIFICATION_PERMISSION_PROMPTED] = prompted }
    }

    suspend fun setShowHelperDescriptions(show: Boolean) {
        context.appDataStore.edit { it[Keys.SHOW_HELPER_DESCRIPTIONS] = show }
    }

    suspend fun saveTimeSchemeGeneratorConfig(schemeId: Long, config: TimeSchemeGeneratorConfig) {
        context.appDataStore.edit { prefs ->
            val current = prefs[Keys.TIME_SCHEME_GEN_CONFIGS_JSON]
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    runCatching { json.decodeFromString<Map<String, TimeSchemeGeneratorConfig>>(it) }
                        .getOrElse { emptyMap() }
                }
                .orEmpty()
                .toMutableMap()
            current[schemeId.toString()] = config
            prefs[Keys.TIME_SCHEME_GEN_CONFIGS_JSON] = json.encodeToString(current)
        }
    }
}
