package com.omninumeris.application

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object VaultModule {

    @Provides
    @Singleton
    fun provideVault(): Vault = SecureVault()
}
