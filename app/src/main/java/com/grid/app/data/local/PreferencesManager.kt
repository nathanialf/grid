package com.grid.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.grid.app.domain.model.Connection
import com.grid.app.domain.model.Credential
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val encryptionManager: EncryptionManager
) {
    
    companion object {
        private val CONNECTIONS_KEY = stringPreferencesKey("connections")
        private val CREDENTIALS_KEY = stringPreferencesKey("credentials")
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        private val SHOW_HIDDEN_FILES_KEY = booleanPreferencesKey("show_hidden_files")
        private val DEFAULT_VIEW_MODE_KEY = stringPreferencesKey("default_view_mode")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Connections
    suspend fun saveConnections(connections: List<Connection>) {
        dataStore.edit { preferences ->
            preferences[CONNECTIONS_KEY] = json.encodeToString(connections)
        }
    }
    
    fun getConnections(): Flow<List<Connection>> {
        return dataStore.data.map { preferences ->
            preferences[CONNECTIONS_KEY]?.let { connectionsJson ->
                try {
                    json.decodeFromString<List<Connection>>(connectionsJson)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
        }
    }
    
    // Credentials
    suspend fun saveCredentials(credentials: List<Credential>) {
        dataStore.edit { preferences ->
            val encryptedCredentials = credentials.map { credential ->
                credential.copy(
                    password = encryptionManager.encrypt(credential.password),
                    privateKey = credential.privateKey?.let { 
                        encryptionManager.encrypt(it) 
                    }
                )
            }
            preferences[CREDENTIALS_KEY] = json.encodeToString(encryptedCredentials)
        }
    }
    
    fun getCredentials(): Flow<List<Credential>> {
        return dataStore.data.map { preferences ->
            preferences[CREDENTIALS_KEY]?.let { credentialsJson ->
                try {
                    val encryptedCredentials = json.decodeFromString<List<Credential>>(credentialsJson)
                    encryptedCredentials.map { credential ->
                        credential.copy(
                            password = try { encryptionManager.decrypt(credential.password) } catch (e: Exception) { "" },
                            privateKey = credential.privateKey?.let { 
                                try { encryptionManager.decrypt(it) } catch (e: Exception) { null }
                            }
                        )
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
        }
    }
    
    // Settings
    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] = enabled
        }
    }
    
    fun isBiometricEnabled(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] ?: false
        }
    }
    
    suspend fun setShowHiddenFiles(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_HIDDEN_FILES_KEY] = show
        }
    }
    
    fun getShowHiddenFiles(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[SHOW_HIDDEN_FILES_KEY] ?: false
        }
    }
    
    suspend fun setDefaultViewMode(viewMode: String) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_VIEW_MODE_KEY] = viewMode
        }
    }
    
    fun getDefaultViewMode(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[DEFAULT_VIEW_MODE_KEY] ?: "list"
        }
    }
    
    suspend fun setThemeMode(themeMode: String) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = themeMode
        }
    }
    
    fun getThemeMode(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[THEME_MODE_KEY] ?: "system"
        }
    }
    
    // Individual credential management methods
    suspend fun saveCredential(credentialId: String, credentialJson: String) {
        val currentCredentials = getCredentials().first().toMutableList()
        val existingIndex = currentCredentials.indexOfFirst { it.id == credentialId }
        
        val credential = json.decodeFromString<Credential>(credentialJson)
        
        if (existingIndex >= 0) {
            currentCredentials[existingIndex] = credential
        } else {
            currentCredentials.add(credential)
        }
        
        saveCredentials(currentCredentials)
    }
    
    suspend fun getCredential(credentialId: String): String? {
        val credentials = getCredentials().first()
        val credential = credentials.find { it.id == credentialId }
        return credential?.let { json.encodeToString(it) }
    }
    
    suspend fun deleteCredential(credentialId: String) {
        val currentCredentials = getCredentials().first().toMutableList()
        currentCredentials.removeAll { it.id == credentialId }
        saveCredentials(currentCredentials)
    }
    
    suspend fun getAllCredentials(): Map<String, String> {
        val credentials = getCredentials().first()
        return credentials.associate { it.id to json.encodeToString(it) }
    }
}