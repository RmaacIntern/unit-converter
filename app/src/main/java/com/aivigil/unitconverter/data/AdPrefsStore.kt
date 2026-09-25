package com.aivigil.unitconverter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.TimeZone

private val Context.adPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ad_prefs")

/**
 * Interstitial frequency rules that must not depend on Remote Config alone:
 * never in the first session after install, and at most N per local calendar day.
 *
 * Every failure path fails closed (no interstitial), never open.
 */
class AdPrefsStore(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::getDefault,
) {
    constructor(context: Context) : this(context.applicationContext.adPrefsDataStore)

    private val sessionLock = Mutex()
    private var firstSession: Boolean? = null

    /**
     * True for the whole process lifetime of the first launch after install.
     * The first call registers this process as a session, so AdsController calls it at startup.
     */
    suspend fun isFirstSession(): Boolean = sessionLock.withLock {
        firstSession ?: registerSession().also { firstSession = it }
    }

    suspend fun interstitialsShownToday(): Int {
        val prefs = try {
            dataStore.data.first()
        } catch (e: IOException) {
            return Int.MAX_VALUE // unknown count: fail closed
        }
        return if (prefs[KEY_DAY] == today()) prefs[KEY_COUNT] ?: 0 else 0
    }

    suspend fun canShowInterstitial(dailyCap: Int): Boolean = interstitialsShownToday() < dailyCap

    /** Call from onAdShowedFullScreenContent, so only real impressions count. */
    suspend fun recordInterstitialShown() {
        try {
            dataStore.edit { prefs ->
                val today = today()
                val shownToday = if (prefs[KEY_DAY] == today) prefs[KEY_COUNT] ?: 0 else 0
                prefs[KEY_DAY] = today
                prefs[KEY_COUNT] = shownToday + 1
            }
        } catch (e: IOException) {
            // Not counted. The next read will most likely fail too and fail closed.
        }
    }

    private suspend fun registerSession(): Boolean = try {
        val prefs = dataStore.edit { it[KEY_SESSIONS] = (it[KEY_SESSIONS] ?: 0) + 1 }
        prefs[KEY_SESSIONS] == 1
    } catch (e: IOException) {
        true // unknown history: treat as first session, i.e. no interstitials
    }

    /** Local calendar day, computed without java.time (API 26+) so minSdk 24 needs no desugaring. */
    private fun today(): Long {
        val now = clock()
        return (now + timeZone().getOffset(now)).floorDiv(MILLIS_PER_DAY)
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        val KEY_SESSIONS = intPreferencesKey("session_count")
        val KEY_DAY = longPreferencesKey("interstitial_day")
        val KEY_COUNT = intPreferencesKey("interstitial_count_today")
    }
}
