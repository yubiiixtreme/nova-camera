package com.novacamera.di

import android.content.Context
import com.novacamera.core.camera.CameraEngine
import com.novacamera.core.camera.CameraXEngine
import com.novacamera.core.common.DispatcherProvider
import com.novacamera.data.datasource.MediaStoreDataSource
import com.novacamera.data.repository.MediaRepository
import com.novacamera.data.repository.MediaRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBinds {
    @Binds @Singleton abstract fun bindEngine(impl: CameraXEngine): CameraEngine
    @Binds @Singleton abstract fun bindMedia(impl: MediaRepositoryImpl): MediaRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppProvides {
    @Provides @Singleton fun dispatchers(): DispatcherProvider = DispatcherProvider.create()

    @Provides @Singleton
    fun mediaStore(@ApplicationContext ctx: Context) = MediaStoreDataSource(ctx)
}
