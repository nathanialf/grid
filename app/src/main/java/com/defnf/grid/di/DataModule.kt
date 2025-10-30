package com.defnf.grid.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.defnf.grid.data.local.EncryptionManager
import com.defnf.grid.data.local.PreferencesManager
import com.defnf.grid.data.local.BiometricManager
import com.defnf.grid.data.remote.NetworkClientFactory
import com.defnf.grid.data.remote.SftpClient
import com.defnf.grid.data.remote.SmbClient
import com.defnf.grid.data.repository.ConnectionRepositoryImpl
import com.defnf.grid.data.repository.CredentialRepositoryImpl
import com.defnf.grid.data.repository.FileRepositoryImpl
import com.defnf.grid.data.repository.SettingsRepositoryImpl
import com.defnf.grid.data.local.EbookRepositoryImpl
import com.defnf.grid.domain.repository.ConnectionRepository
import com.defnf.grid.domain.repository.CredentialRepository
import com.defnf.grid.domain.repository.FileRepository
import com.defnf.grid.domain.repository.SettingsRepository
import com.defnf.grid.domain.repository.EbookRepository
import com.defnf.grid.domain.usecase.connection.GetAllConnectionsUseCase
import com.defnf.grid.domain.usecase.connection.DeleteConnectionUseCase
import com.defnf.grid.domain.usecase.connection.CreateConnectionUseCase
import com.defnf.grid.domain.usecase.connection.UpdateConnectionUseCase
import com.defnf.grid.domain.usecase.connection.GetConnectionUseCase
import com.defnf.grid.domain.usecase.connection.TestConnectionUseCase
import com.defnf.grid.domain.usecase.connection.ReorderConnectionsUseCase
import com.defnf.grid.domain.usecase.settings.GetSettingsUseCase
import com.defnf.grid.domain.usecase.settings.UpdateSettingsUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "grid_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
    
    @Provides
    @Singleton
    fun provideEncryptionManager(): EncryptionManager {
        return EncryptionManager()
    }
    
    @Provides
    @Singleton
    fun providePreferencesManager(
        dataStore: DataStore<Preferences>,
        encryptionManager: EncryptionManager
    ): PreferencesManager {
        return PreferencesManager(dataStore, encryptionManager)
    }
    
    @Provides
    @Singleton
    fun provideSftpClient(@ApplicationContext context: android.content.Context): SftpClient {
        return SftpClient(context)
    }
    
    @Provides
    @Singleton
    fun provideSmbClient(): SmbClient {
        return SmbClient()
    }
    
    @Provides
    @Singleton
    fun provideNetworkClientFactory(
        sftpClient: SftpClient,
        smbClient: SmbClient
    ): NetworkClientFactory {
        return NetworkClientFactory(sftpClient, smbClient)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    abstract fun bindConnectionRepository(
        connectionRepositoryImpl: ConnectionRepositoryImpl
    ): ConnectionRepository
    
    @Binds
    abstract fun bindFileRepository(
        fileRepositoryImpl: FileRepositoryImpl
    ): FileRepository
    
    @Binds
    abstract fun bindCredentialRepository(
        credentialRepositoryImpl: CredentialRepositoryImpl
    ): CredentialRepository
    
    @Binds
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository
    
    @Binds
    abstract fun bindEbookRepository(
        ebookRepositoryImpl: EbookRepositoryImpl
    ): EbookRepository
}

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    
    @Provides
    @Singleton
    fun provideGetAllConnectionsUseCase(
        connectionRepository: ConnectionRepository
    ): GetAllConnectionsUseCase {
        return GetAllConnectionsUseCase(connectionRepository)
    }
    
    @Provides
    @Singleton
    fun provideDeleteConnectionUseCase(
        connectionRepository: ConnectionRepository
    ): DeleteConnectionUseCase {
        return DeleteConnectionUseCase(connectionRepository)
    }
    
    @Provides
    @Singleton
    fun provideCreateConnectionUseCase(
        connectionRepository: ConnectionRepository
    ): CreateConnectionUseCase {
        return CreateConnectionUseCase(connectionRepository)
    }
    
    @Provides
    @Singleton
    fun provideUpdateConnectionUseCase(
        connectionRepository: ConnectionRepository
    ): UpdateConnectionUseCase {
        return UpdateConnectionUseCase(connectionRepository)
    }
    
    @Provides
    @Singleton
    fun provideGetConnectionUseCase(
        connectionRepository: ConnectionRepository
    ): GetConnectionUseCase {
        return GetConnectionUseCase(connectionRepository)
    }
    
    @Provides
    @Singleton
    fun provideTestConnectionUseCase(
        fileRepository: FileRepository
    ): TestConnectionUseCase {
        return TestConnectionUseCase(fileRepository)
    }
    
    @Provides
    @Singleton
    fun provideGetSettingsUseCase(
        settingsRepository: SettingsRepository
    ): GetSettingsUseCase {
        return GetSettingsUseCase(settingsRepository)
    }
    
    @Provides
    @Singleton
    fun provideUpdateSettingsUseCase(
        settingsRepository: SettingsRepository
    ): UpdateSettingsUseCase {
        return UpdateSettingsUseCase(settingsRepository)
    }
    
    @Provides
    @Singleton
    fun provideReorderConnectionsUseCase(
        connectionRepository: ConnectionRepository
    ): ReorderConnectionsUseCase {
        return ReorderConnectionsUseCase(connectionRepository)
    }
    
    @Provides
    @Singleton
    fun provideBiometricManager(@ApplicationContext context: Context): BiometricManager {
        return BiometricManager(context)
    }
}