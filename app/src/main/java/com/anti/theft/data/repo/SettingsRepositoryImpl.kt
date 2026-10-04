package com.anti.theft.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.anti.theft.domain.models.AppPermission
import com.anti.theft.domain.models.ProtectionFeature
import com.anti.theft.domain.models.ProtectionSettingsModel
import com.anti.theft.domain.models.Sensitivity
import com.anti.theft.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val settings: Flow<ProtectionSettingsModel> = preferences
        .map { p ->
            ProtectionSettingsModel(
                chargingEnabled = p[CHARGING_ENABLED] ?: false,
                motionEnabled = p[MOTION_ENABLED] ?: false,
                clapEnabled = p[CLAP_ENABLED] ?: false,
                pocketEnabled = p[POCKET_ENABLED] ?: false,
                motionSensitivity = p[MOTION_SENSITIVITY].toSensitivity(),
                clapSensitivity = p[CLAP_SENSITIVITY].toSensitivity(),
            )
        }
        .distinctUntilChanged()

    override val requestedPermissions: Flow<Set<AppPermission>> = preferences
        .map { p -> AppPermission.entries.filterTo(mutableSetOf()) { p[it.requestedKey()] == true } }
        .distinctUntilChanged()

    override val batteryTipDismissed: Flow<Boolean> = preferences
        .map { it[BATTERY_TIP_DISMISSED] ?: false }
        .distinctUntilChanged()

    override suspend fun setFeatureEnabled(feature: ProtectionFeature, enabled: Boolean) {
        val key = when (feature) {
            ProtectionFeature.CHARGING -> CHARGING_ENABLED
            ProtectionFeature.MOTION -> MOTION_ENABLED
            ProtectionFeature.CLAP -> CLAP_ENABLED
            ProtectionFeature.POCKET -> POCKET_ENABLED
        }
        dataStore.edit { it[key] = enabled }
    }

    override suspend fun setSensitivity(feature: ProtectionFeature, sensitivity: Sensitivity) {
        val key = when (feature) {
            ProtectionFeature.MOTION -> MOTION_SENSITIVITY
            ProtectionFeature.CLAP -> CLAP_SENSITIVITY
            ProtectionFeature.CHARGING, ProtectionFeature.POCKET -> return
        }
        dataStore.edit { it[key] = sensitivity.name }
    }

    override suspend fun markPermissionRequested(permission: AppPermission) {
        dataStore.edit { it[permission.requestedKey()] = true }
    }

    override suspend fun dismissBatteryTip() {
        dataStore.edit { it[BATTERY_TIP_DISMISSED] = true }
    }

    private fun String?.toSensitivity(): Sensitivity =
        Sensitivity.entries.firstOrNull { it.name == this } ?: Sensitivity.MEDIUM

    private fun AppPermission.requestedKey() =
        booleanPreferencesKey("requested_${name.lowercase()}")

    private companion object {
        val CHARGING_ENABLED = booleanPreferencesKey("charging_enabled")
        val MOTION_ENABLED = booleanPreferencesKey("motion_enabled")
        val CLAP_ENABLED = booleanPreferencesKey("clap_enabled")
        val POCKET_ENABLED = booleanPreferencesKey("pocket_enabled")
        val MOTION_SENSITIVITY = stringPreferencesKey("motion_sensitivity")
        val CLAP_SENSITIVITY = stringPreferencesKey("clap_sensitivity")
        val BATTERY_TIP_DISMISSED = booleanPreferencesKey("battery_tip_dismissed")
    }
}
