package com.pabloufor.voiceflow.di

import com.pabloufor.voiceflow.core.concurrency.DefaultDispatcherProvider
import com.pabloufor.voiceflow.core.concurrency.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent


@Module
@InstallIn(SingletonComponent::class)
abstract class DispatcherModule {

    @Binds
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider
}