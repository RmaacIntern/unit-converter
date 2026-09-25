package com.aivigil.unitconverter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aivigil.unitconverter.domain.Category
import com.aivigil.unitconverter.domain.NumberFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class UserSettings(
    val decimalPlaces: Int,
    val defaultCategory: Category,
)

/** User settings in DataStore: they survive rotation and process death with no database. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    /** Emits once the file is read. Until then the Main screen is in its Loading state. */
    val settings: Flow<UserSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            UserSettings(
                decimalPlaces = (prefs[KEY_DECIMAL_PLACES] ?: NumberFormatter.DEFAULT_DECIMALS)
                    .coerceIn(NumberFormatter.MIN_DECIMALS, NumberFormatter.MAX_DECIMALS),
                defaultCategory = Category.fromId(prefs[KEY_DEFAULT_CATEGORY]) ?: Category.Length,
            )
        }
        .distinctUntilChanged()

    suspend fun setDecimalPlaces(places: Int) = safeEdit {
        it[KEY_DECIMAL_PLACES] = places.coerceIn(NumberFormatter.MIN_DECIMALS, NumberFormatter.MAX_DECIMALS)
    }

    suspend fun setDefaultCategory(category: Category) = safeEdit {
        it[KEY_DEFAULT_CATEGORY] = category.id
    }

    /**
     * A failed write leaves the stored value unchanged; the settings flow keeps emitting it, so
     * the UI snaps back instead of crashing.
     */
    private suspend fun safeEdit(block: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit { block(it) }
        } catch (e: IOException) {
            // Intentionally swallowed: see KDoc.
        }
    }

    private companion object {
        val KEY_DECIMAL_PLACES = intPreferencesKey("decimal_places")
        val KEY_DEFAULT_CATEGORY = stringPreferencesKey("default_category")
    }
}
