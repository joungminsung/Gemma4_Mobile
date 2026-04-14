package com.gemma4mobile.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.gemma4mobile.db.AppDatabase
import com.gemma4mobile.db.ChatDao
import com.gemma4mobile.inference.GemmaInferenceEngine
import com.gemma4mobile.tools.ToolRouter
import com.gemma4mobile.tools.executor.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "gemma4mobile.db",
        ).addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideChatDao(db: AppDatabase): ChatDao {
        return db.chatDao()
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideInferenceEngine(): GemmaInferenceEngine {
        return GemmaInferenceEngine()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideToolRouter(
        webSearch: WebSearchExecutor,
        calendarRead: CalendarReadExecutor,
        calendarWrite: CalendarWriteExecutor,
        smsRead: SmsReadExecutor,
        smsSend: SmsSendExecutor,
        callLogRead: CallLogReadExecutor,
        makeCall: MakeCallExecutor,
        contactsRead: ContactsReadExecutor,
        contactsWrite: ContactsWriteExecutor,
        alarmRead: AlarmReadExecutor,
        alarmSet: AlarmSetExecutor,
    ): ToolRouter {
        val router = ToolRouter()
        router.register(webSearch)
        router.register(calendarRead)
        router.register(calendarWrite)
        router.register(smsRead)
        router.register(smsSend)
        router.register(callLogRead)
        router.register(makeCall)
        router.register(contactsRead)
        router.register(contactsWrite)
        router.register(alarmRead)
        router.register(alarmSet)
        return router
    }
}
