package com.aivigil.unitconverter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.ratingPrefsDataStore: DataStore<Preferences> by
preferencesDataStore(name = "rating_prefs")

/**
 * Remembers whether the user has already been sent to the Play Store listing, so the
 * exit prompt stops showing the star row after they've acted on it once.
 *
 * Fails closed in the same direction as [AdPrefsStore]: if the store can't be read,
 * we treat the user as having already rated, so a broken read means *fewer* prompts,
 * never a prompt on every single exit.
 */
class RatingPrefsStore(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.ratingPrefsDataStore)

    /** True once the user has tapped a star (we can't know if they actually submitted). */
    suspend fun hasRated(): Boolean = try {
        dataStore.data.first()[KEY_HAS_RATED] ?: false
    } catch (e: IOException) {
        true // fail closed: don't nag when state is unknown
    }

    /** How many times the exit prompt has shown the star row. */
    suspend fun promptCount(): Int = try {
        dataStore.data.first()[KEY_PROMPT_COUNT] ?: 0
    } catch (e: IOException) {
        Int.MAX_VALUE // fail closed
    }

    suspend fun recordPromptShown() {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_PROMPT_COUNT] = (prefs[KEY_PROMPT_COUNT] ?: 0) + 1
            }
        } catch (e: IOException) {
            // Best effort. A lost increment only costs one extra prompt.
        }
    }

    suspend fun recordRated() {
        try {
            dataStore.edit { prefs -> prefs[KEY_HAS_RATED] = true }
        } catch (e: IOException) {
            // Best effort.
        }
    }

    companion object {
        private val KEY_HAS_RATED = booleanPreferencesKey("has_rated")
        private val KEY_PROMPT_COUNT = intPreferencesKey("prompt_count")

        /**
         * Stop offering the star row after this many exits if the user never acts on it.
         * Someone who has ignored it four times is telling you something.
         */
        const val MAX_PROMPTS = 4
    }
}