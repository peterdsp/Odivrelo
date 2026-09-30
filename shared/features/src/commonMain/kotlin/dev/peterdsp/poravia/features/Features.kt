package dev.peterdsp.poravia.features

import dev.peterdsp.poravia.core.PoraviaCore
import dev.peterdsp.poravia.core.extras
import dev.peterdsp.poravia.core.model.Coverage
import dev.peterdsp.poravia.core.model.FavoritePlace
import dev.peterdsp.poravia.core.model.InstalledPack
import dev.peterdsp.poravia.core.model.JourneyDetail
import dev.peterdsp.poravia.core.model.Meta
import dev.peterdsp.poravia.core.model.OfflineCatalog
import dev.peterdsp.poravia.core.model.OperatorDetail
import dev.peterdsp.poravia.core.model.PackProgress
import dev.peterdsp.poravia.core.model.PackResult
import dev.peterdsp.poravia.core.model.SavedTrip
import dev.peterdsp.poravia.core.model.SourceList
import dev.peterdsp.poravia.core.model.StopDetail
import dev.peterdsp.poravia.core.Cancellable
import kotlin.native.ObjCName

/**
 * Journey detail, including the offline copy of a saved trip.
 *
 * When the installed release cannot answer (because it was removed, replaced or
 * never installed) but the trip is saved, the cached copy is used and the caller
 * is told so. A traveller standing at a bay is not helped by a spinner.
 */
@ObjCName("PoraviaJourneyDetailFeature")
class JourneyDetailFeature(private val core: PoraviaCore) {

    suspend fun load(journeyId: String, serviceDate: String): Loadable<JourneyDetailView> {
        val live = runFeature { core.journeyDetail(journeyId, serviceDate) }
        if (live is Loadable.Ready) {
            return Loadable.Ready(JourneyDetailView(live.value, fromSavedCopy = false))
        }
        val cached = cachedCopy(journeyId, serviceDate)
        return if (cached != null) {
            Loadable.Ready(JourneyDetailView(cached, fromSavedCopy = true))
        } else {
            live as Loadable<JourneyDetailView>
        }
    }

    private suspend fun cachedCopy(journeyId: String, serviceDate: String): JourneyDetail? {
        val extras = core.extras ?: return null
        val trip = runCatching { core.savedTrips() }.getOrNull()
            ?.firstOrNull { it.journeyId == journeyId && it.serviceDate == serviceDate }
            ?: return null
        return runCatching { extras.savedTripDetail(trip.id) }.getOrNull()
    }

    suspend fun save(journeyId: String, serviceDate: String): Loadable<SavedTrip> =
        runFeature { core.saveTrip(journeyId, serviceDate) }

    suspend fun remove(savedTripId: String): Loadable<Unit> =
        runFeature { core.removeSavedTrip(savedTripId) }

    suspend fun savedTripFor(journeyId: String, serviceDate: String): SavedTrip? =
        runCatching { core.savedTrips() }.getOrNull()
            ?.firstOrNull { it.journeyId == journeyId && it.serviceDate == serviceDate }
}

@ObjCName("PoraviaJourneyDetailView")
data class JourneyDetailView(
    val detail: JourneyDetail,
    /** True when this came from the saved copy rather than the installed release. */
    val fromSavedCopy: Boolean,
)

@ObjCName("PoraviaOperatorFeature")
class OperatorFeature(private val core: PoraviaCore) {
    suspend fun load(operatorId: String): Loadable<OperatorDetail> =
        runFeature { core.operatorDetail(operatorId) }
}

@ObjCName("PoraviaStopFeature")
class StopFeature(private val core: PoraviaCore) {
    suspend fun load(stopId: String, serviceDate: String): Loadable<StopDetail> {
        val outcome = runFeature { core.stopDetail(stopId, serviceDate) }
        if (outcome is Loadable.Ready && outcome.value.stop.departures.isEmpty()) {
            return Loadable.Empty(EmptyReason.NO_SERVICE_ON_DATE, outcome.value.stop.coverage)
        }
        return outcome
    }
}

/** Favourites, saved trips and the coverage statement behind them. */
@ObjCName("PoraviaLibraryFeature")
class LibraryFeature(private val core: PoraviaCore) {

    suspend fun favorites(): Loadable<List<FavoritePlace>> {
        val outcome = runFeature { core.favorites() }
        if (outcome is Loadable.Ready && outcome.value.isEmpty()) {
            return Loadable.Empty(EmptyReason.NOTHING_SAVED)
        }
        return outcome
    }

    suspend fun toggleFavorite(placeId: String): Loadable<Boolean> =
        runFeature { core.toggleFavorite(placeId) }

    suspend fun savedTrips(): Loadable<List<SavedTrip>> {
        val outcome = runFeature { core.savedTrips() }
        if (outcome is Loadable.Ready && outcome.value.isEmpty()) {
            return Loadable.Empty(EmptyReason.NOTHING_SAVED)
        }
        return outcome
    }

    suspend fun removeSavedTrip(id: String): Loadable<Unit> =
        runFeature { core.removeSavedTrip(id) }

    suspend fun cachedTrip(savedTripId: String): JourneyDetail? =
        core.extras?.let { extras -> runCatching { extras.savedTripDetail(savedTripId) }.getOrNull() }
}

/** The offline packs screen: catalogue, downloads, rollback and removal. */
@ObjCName("PoraviaOfflineFeature")
class OfflineFeature(private val core: PoraviaCore) {

    suspend fun catalog(): Loadable<OfflineCatalog> {
        val outcome = runFeature { core.offlineCatalog() }
        if (outcome is Loadable.Ready &&
            outcome.value.available.isEmpty() &&
            outcome.value.installed.isEmpty()
        ) {
            return Loadable.Empty(EmptyReason.NOTHING_INSTALLED)
        }
        return outcome
    }

    suspend fun installed(): Loadable<List<InstalledPack>> = runFeature { core.installedPacks() }

    fun download(
        packName: String,
        onProgress: (PackProgress) -> Unit,
        onResult: (PackResult) -> Unit,
    ): Cancellable = core.downloadPack(packName, onProgress, onResult)

    suspend fun remove(packName: String): Loadable<Unit> = runFeature { core.removePack(packName) }

    suspend fun rollback(): Loadable<Meta> = runFeature { core.rollbackToPreviousRelease() }

    suspend fun adoptBundledRelease(): Boolean =
        core.extras?.let { runCatching { it.adoptSeededRelease() }.getOrDefault(false) } ?: false
}

/** Release identity, coverage and sources, as shown in settings and about. */
@ObjCName("PoraviaAboutFeature")
class AboutFeature(private val core: PoraviaCore) {

    suspend fun meta(): Loadable<Meta> = runFeature { core.meta() }

    suspend fun coverage(): Loadable<Coverage> = runFeature { core.coverage() }

    suspend fun sources(): Loadable<SourceList> {
        val outcome = runFeature { core.sources() }
        if (outcome is Loadable.Ready && outcome.value.sources.isEmpty()) {
            return Loadable.Empty(EmptyReason.NOTHING_INSTALLED)
        }
        return outcome
    }
}
