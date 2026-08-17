package org.moodle.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.moodle.core.network.MoodleApi
import org.moodle.core.html.DefaultHtmlMoodleDataSource
import org.moodle.core.html.HtmlMoodleDataSource
import org.moodle.core.html.HtmlCookieStorage
import org.moodle.core.html.KeystoreHtmlCookieStorage
import org.moodle.data.local.MoodleDao
import org.moodle.data.local.MoodleDatabase
import org.moodle.data.repository.DefaultMoodleAuthRepository
import org.moodle.data.repository.DefaultMoodleRepository
import org.moodle.data.repository.MoodleAuthRepository
import org.moodle.data.repository.MoodleRepository
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE site_accounts ADD COLUMN htmlFeaturesJson TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE site_accounts ADD COLUMN authState TEXT NOT NULL DEFAULT 'Authenticated'")
        db.execSQL("ALTER TABLE site_accounts ADD COLUMN moodleVersion TEXT")
        db.execSQL("ALTER TABLE site_accounts ADD COLUMN themeFamily TEXT")
        db.execSQL("UPDATE site_accounts SET connectionMode = 'NativeHtml', authState = 'ReauthenticationRequired' WHERE connectionMode = 'WebFallback'")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS conversations (
                accountId TEXT NOT NULL,
                conversationId INTEGER NOT NULL,
                type TEXT NOT NULL,
                name TEXT NOT NULL,
                membersJson TEXT NOT NULL,
                latestMessagePreview TEXT NOT NULL,
                latestMessageAt INTEGER NOT NULL,
                unreadCount INTEGER NOT NULL,
                isFavourite INTEGER NOT NULL,
                canReply INTEGER NOT NULL,
                PRIMARY KEY(accountId, conversationId)
            )""".trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_conversations_accountId_latestMessageAt " +
                "ON conversations(accountId, latestMessageAt)",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS conversation_members (
                accountId TEXT NOT NULL,
                conversationId INTEGER NOT NULL,
                userId INTEGER NOT NULL,
                fullName TEXT NOT NULL,
                isCurrentUser INTEGER NOT NULL,
                canMessage INTEGER NOT NULL,
                PRIMARY KEY(accountId, conversationId, userId)
            )""".trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_conversation_members_accountId_userId " +
                "ON conversation_members(accountId, userId)",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS messages (
                accountId TEXT NOT NULL,
                messageId INTEGER NOT NULL,
                conversationId INTEGER NOT NULL,
                senderId INTEGER NOT NULL,
                senderName TEXT NOT NULL,
                bodyText TEXT NOT NULL,
                bodyHtml TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                isMine INTEGER NOT NULL,
                isRead INTEGER NOT NULL,
                locallyNotified INTEGER NOT NULL,
                PRIMARY KEY(accountId, messageId)
            )""".trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_messages_accountId_conversationId_createdAt " +
                "ON messages(accountId, conversationId, createdAt)",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS message_drafts (
                accountId TEXT NOT NULL,
                draftKey TEXT NOT NULL,
                body TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(accountId, draftKey)
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS message_sync_state (
                accountId TEXT NOT NULL,
                initialized INTEGER NOT NULL,
                lastSyncEpochSeconds INTEGER NOT NULL,
                PRIMARY KEY(accountId)
            )""".trimIndent(),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().disableHtmlEscaping().create()

    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Provides
    @Singleton
    fun provideMoodleApi(client: OkHttpClient, gson: Gson): MoodleApi = Retrofit.Builder()
        .baseUrl("https://localhost/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(MoodleApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MoodleDatabase =
        Room.databaseBuilder(context, MoodleDatabase::class.java, "mobile-moodle.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideDao(database: MoodleDatabase): MoodleDao = database.moodleDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindHtmlCookieStorage(implementation: KeystoreHtmlCookieStorage): HtmlCookieStorage

    @Binds
    @Singleton
    abstract fun bindHtmlMoodleDataSource(implementation: DefaultHtmlMoodleDataSource): HtmlMoodleDataSource

    @Binds
    @Singleton
    abstract fun bindAuthRepository(implementation: DefaultMoodleAuthRepository): MoodleAuthRepository

    @Binds
    @Singleton
    abstract fun bindMoodleRepository(implementation: DefaultMoodleRepository): MoodleRepository
}
