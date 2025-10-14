package com.grid.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.grid.app.data.local.EncryptionManager
import com.grid.app.data.local.PreferencesManager
import com.grid.app.data.local.BiometricManager
import com.grid.app.data.remote.NetworkClientFactory
import com.grid.app.data.remote.FtpClient
import com.grid.app.data.remote.SftpClient
import com.grid.app.data.remote.SmbClient
import com.grid.app.data.repository.ConnectionRepositoryImpl
import com.grid.app.data.repository.CredentialRepositoryImpl
import com.grid.app.data.repository.FileRepositoryImpl
import com.grid.app.data.repository.SettingsRepositoryImpl
import com.grid.app.domain.repository.ConnectionRepository
import com.grid.app.domain.repository.CredentialRepository
import com.grid.app.domain.repository.FileRepository
import com.grid.app.domain.repository.SettingsRepository
import com.grid.app.domain.usecase.connection.GetAllConnectionsUseCase
import com.grid.app.domain.usecase.connection.DeleteConnectionUseCase
import com.grid.app.domain.usecase.connection.CreateConnectionUseCase
import com.grid.app.domain.usecase.connection.UpdateConnectionUseCase
import com.grid.app.domain.usecase.connection.GetConnectionUseCase
import com.grid.app.domain.usecase.connection.TestConnectionUseCase
import com.grid.app.domain.usecase.connection.ReorderConnectionsUseCase
import com.grid.app.domain.usecase.settings.GetSettingsUseCase
import com.grid.app.domain.usecase.settings.UpdateSettingsUseCase
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
    fun provideFtpClient(): FtpClient {
        return FtpClient()
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
        ftpClient: FtpClient,
        sftpClient: SftpClient,
        smbClient: SmbClient
    ): NetworkClientFactory {
        return NetworkClientFactory(ftpClient, sftpClient, smbClient)
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