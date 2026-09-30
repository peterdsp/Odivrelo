package dev.peterdsp.odivrelo.core

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.peterdsp.odivrelo.core.db.OdivreloDatabase
import dev.peterdsp.odivrelo.core.model.ErrorCode
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
object OdivreloAndroid {
    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    internal fun requireContext(): Context = applicationContext
        ?: error(
            "OdivreloAndroid.initialize(context) must be called from Application.onCreate() " +
                "before createOdivreloCore(config).",
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
        schema = OdivreloDatabase.Schema,
        context = OdivreloAndroid.requireContext(),
        name = file.absolutePath,
    )
}

@Throws(OdivreloException::class)
actual fun createOdivreloCore(config: CoreConfig): OdivreloCore {
    requireValidConfig(config)
    return try {
        OdivreloCoreImpl(
            config = config,
            httpClient = createPlatformHttpClient(),
            driver = createPlatformDriver(config.databasePath),
        )
    } catch (error: OdivreloException) {
        throw error
    } catch (error: Throwable) {
        // Opening the database or preparing the packs directory failed. A host
        // application can show this; terminating the process cannot be caught.
        throw OdivreloException(
            code = ErrorCode.UNAVAILABLE,
            message = "Odivrelo could not open its local storage.",
            kind = OdivreloFailureKind.GENERAL,
        )
    }
}

private const val REQUEST_TIMEOUT_MILLIS = 30_000L
private const val CONNECT_TIMEOUT_MILLIS = 15_000L
private const val SOCKET_TIMEOUT_MILLIS = 30_000L
