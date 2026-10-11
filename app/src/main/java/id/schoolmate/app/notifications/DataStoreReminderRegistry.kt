package id.schoolmate.app.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * Registry kept in its own small DataStore file ("reminder_registry"), separate from user data.
 * Entries are "token@epochMillis" strings. Delivered entries older than [RETENTION_MILLIS] are pruned.
 */
class DataStoreReminderRegistry(private val dataStore: DataStore<Preferences>) : ReminderRegistry {

    private object Keys {
        val SCHEDULED = stringSetPreferencesKey("scheduled")
        val DELIVERED = stringSetPreferencesKey("delivered")
    }

    private suspend fun read(): Preferences =
        dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()

    override suspend fun scheduled(): Map<String, Long> =
        read()[Keys.SCHEDULED].orEmpty().mapNotNull(::decode).toMap()

    override suspend fun setScheduled(value: Map<String, Long>) {
        dataStore.edit { it[Keys.SCHEDULED] = value.map { (k, v) -> encode(k, v) }.toSet() }
    }

    override suspend fun wasDelivered(token: String, triggerAtMillis: Long): Boolean =
        encode(token, triggerAtMillis) in read()[Keys.DELIVERED].orEmpty()

    override suspend fun markDelivered(token: String, triggerAtMillis: Long, nowMillis: Long) {
        dataStore.edit { prefs ->
            val kept = prefs[Keys.DELIVERED].orEmpty().filter { e -> decode(e)?.let { nowMillis - it.second < RETENTION_MILLIS } == true }
            prefs[Keys.DELIVERED] = (kept + encode(token, triggerAtMillis)).toSet()
        }
    }

    override suspend fun deliveredTokens(): Set<String> =
        read()[Keys.DELIVERED].orEmpty().mapNotNull { decode(it)?.first }.toSet()

    override suspend fun forgetDelivered(tokens: Set<String>) {
        if (tokens.isEmpty()) return
        dataStore.edit { prefs ->
            prefs[Keys.DELIVERED] = prefs[Keys.DELIVERED].orEmpty().filterNot { decode(it)?.first in tokens }.toSet()
        }
    }

    companion object {
        const val RETENTION_MILLIS = 8L * 24 * 60 * 60 * 1000
        fun encode(token: String, millis: Long) = "$token@$millis"
        fun decode(entry: String): Pair<String, Long>? {
            val at = entry.lastIndexOf('@').takeIf { it > 0 } ?: return null
            return entry.substring(at + 1).toLongOrNull()?.let { entry.substring(0, at) to it }
        }
    }
}
