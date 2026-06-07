package com.pabloufor.voiceflow.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pabloufor.voiceflow.BuildConfig
import com.pabloufor.voiceflow.core.concurrency.DefaultDispatcherProvider
import com.pabloufor.voiceflow.core.concurrency.DispatcherProvider
import com.pabloufor.voiceflow.data.chat.ChatRemoteDataSource
import com.pabloufor.voiceflow.data.chat.ChatRepositoryImpl
import com.pabloufor.voiceflow.data.transcription.TranscriptionApi
import com.pabloufor.voiceflow.data.transcription.TranscriptionRepositoryImpl
import com.pabloufor.voiceflow.domain.repository.ChatRepository
import com.pabloufor.voiceflow.domain.repository.TranscriptionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    @Named("baseUrl")
    fun provideBaseUrl(): String = BuildConfig.BASE_URL

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        @Named("baseUrl") baseUrl: String,
        json: Json,
    ): Retrofit {
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideTranscriptionApi(retrofit: Retrofit): TranscriptionApi =
        retrofit.create(TranscriptionApi::class.java)

    @Provides
    @Singleton
    fun provideChatRemoteDataSource(
        okHttpClient: OkHttpClient,
        @Named("baseUrl") baseUrl: String,
        json: Json,
        dispatcher: DispatcherProvider
    ): ChatRemoteDataSource = ChatRemoteDataSource(okHttpClient, baseUrl, json, dispatcher)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindTranscriptionRepository(impl: TranscriptionRepositoryImpl): TranscriptionRepository
}
