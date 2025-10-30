package com.defnf.grid.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.defnf.grid.domain.model.EbookReadingPosition
import com.defnf.grid.domain.repository.EbookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EbookRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : EbookRepository {

    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun saveReadingPosition(position: EbookReadingPosition) {
        val key = stringPreferencesKey("ebook_position_${position.filePath.hashCode()}")
        dataStore.edit { preferences ->
            preferences[key] = json.encodeToString(position)
        }
    }

    override suspend fun getReadingPosition(filePath: String): EbookReadingPosition? {
        val key = stringPreferencesKey("ebook_position_${filePath.hashCode()}")
        val preferences = dataStore.data.first()
        val positionJson = preferences[key] ?: return null
        
        return try {
            json.decodeFromString<EbookReadingPosition>(positionJson)
        } catch (e: Exception) {
            null
        }
    }

    override fun getAllReadingPositions(): Flow<List<EbookReadingPosition>> {
        return dataStore.data.map { preferences ->
            preferences.asMap().entries
                .filter { it.key.name.startsWith("ebook_position_") }
                .mapNotNull { entry ->
                    try {
                        json.decodeFromString<EbookReadingPosition>(entry.value as String)
                    } catch (e: Exception) {
                        null
                    }
                }
                .sortedByDescending { it.lastReadTime }
        }
    }

    override suspend fun deleteReadingPosition(filePath: String) {
        val key = stringPreferencesKey("ebook_position_${filePath.hashCode()}")
        dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }
}