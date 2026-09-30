package dev.peterdsp.odivrelo.core

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.peterdsp.odivrelo.core.db.OdivreloDatabase
import dev.peterdsp.odivrelo.core.io.Paths
import dev.peterdsp.odivrelo.core.io.PlatformFiles
import dev.peterdsp.odivrelo.core.model.ErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import kotlin.native.ObjCName
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin) {
    expectSuccess = false
    install(UserAgent) {
        agent = Brand.NAME + "/" + Brand.VERSION + " (iOS)"
    }
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
    }
}

internal actual fun createPlatformDriver(databasePath: String): SqlDriver {
    PlatformFiles.mkdirs(Paths.parentOf(databasePath))
    // NativeSqliteDriver runs the generated create and migrate callbacks.
    return NativeSqliteDriver(
        schema = OdivreloDatabase.Schema,
        name = Paths.nameOf(databasePath),
        onConfiguration = { configuration ->
            configuration.copy(
                extendedConfig = configuration.extendedConfig.copy(
                    basePath = Paths.parentOf(databasePath),
                ),
            )
        },
    )
}

@Throws(OdivreloException::class)
@ObjCName("OdivreloCoreFactory")
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

/**
 * Default writable locations for an iOS host, offered so the Swift application
 * does not have to guess which directory the core expects.
 *
 * Application Support is used rather than Caches because a downloaded release
 * must survive the system reclaiming cache space in the middle of a journey,
 * and neither location is included in a device backup by accident: the host
 * application sets the exclude-from-backup flag on the directory it passes in.
 */
@ObjCName("OdivreloIosPaths")
object OdivreloIosPaths {

    fun applicationSupportDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        )
        val base = paths.firstOrNull() as? String ?: "."
        val directory = Paths.join(base, Brand.SLUG)
        PlatformFiles.mkdirs(directory)
        return directory
    }

    fun defaultPacksDirectory(): String {
        val directory = Paths.join(applicationSupportDirectory(), "packs")
        PlatformFiles.mkdirs(directory)
        return directory
    }

    fun defaultDatabasePath(): String =
        Paths.join(applicationSupportDirectory(), "odivrelo.db")

    fun defaultConfig(languageTag: String): CoreConfig = CoreConfig(
        apiBaseUrl = null,
        staticPacksBaseUrl = Brand.STATIC_PACKS_BASE_URL,
        packsDirectory = defaultPacksDirectory(),
        databasePath = defaultDatabasePath(),
        languageTag = languageTag,
    )
}

private const val REQUEST_TIMEOUT_MILLIS = 30_000L
private const val CONNECT_TIMEOUT_MILLIS = 15_000L
private const val SOCKET_TIMEOUT_MILLIS = 30_000L
