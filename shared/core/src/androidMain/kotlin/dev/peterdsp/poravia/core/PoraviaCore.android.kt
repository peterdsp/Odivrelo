package dev.peterdsp.poravia.core

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.peterdsp.poravia.core.db.PoraviaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import java.io.File

/**
 * Android needs a `Context` to open a SQLite database, and the core's factory
 * signature is shared with iOS, so the application hands the core its context
 * once at startup. Doing it here rather than through a hidden content provider
 * keeps the dependency visible.
 */
object PoraviaAndroid {
    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    internal fun requireContext(): Context = applicationContext
        ?: error(
            "PoraviaAndroid.initialize(context) must be called from Application.onCreate() " +
                "before createPoraviaCore(config).",
        )
}

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(UserAgent) {
        agent = Brand.NAME + "/" + Brand.VERSION + " (Android)"
    }
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
    }
}

internal actual fun createPlatformDriver(databasePath: String): SqlDriver {
    val file = File(databasePath)
    file.parentFile?.mkdirs()
    // AndroidSqliteDriver runs the generated create and migrate callbacks, so
    // an upgrading installation keeps its saved trips.
    return AndroidSqliteDriver(
        schema = PoraviaDatabase.Schema,
        context = PoraviaAndroid.requireContext(),
        name = file.absolutePath,
    )
}

actual fun createPoraviaCore(config: CoreConfig): PoraviaCore = PoraviaCoreImpl(
    config = config,
    httpClient = createPlatformHttpClient(),
    driver = createPlatformDriver(config.databasePath),
)

private const val REQUEST_TIMEOUT_MILLIS = 30_000L
private const val CONNECT_TIMEOUT_MILLIS = 15_000L
private const val SOCKET_TIMEOUT_MILLIS = 30_000L
