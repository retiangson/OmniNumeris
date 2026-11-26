package com.omninumeris.app

import android.content.Context
import com.omninumeris.application.CheckWorkspaceStorage
import com.omninumeris.domain.WorkspaceStorage
import com.omninumeris.infrastructure.storage.PrivateWorkspaceStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object WorkspaceModule {
    @Provides
    fun provideWorkspaceStorage(@ApplicationContext context: Context): WorkspaceStorage =
        PrivateWorkspaceStorage { context.filesDir }

    @Provides
    fun provideCheckWorkspaceStorage(storage: WorkspaceStorage): CheckWorkspaceStorage =
        CheckWorkspaceStorage(storage)
}
