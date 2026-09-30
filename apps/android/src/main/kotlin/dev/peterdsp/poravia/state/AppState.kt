package dev.peterdsp.poravia.state

import dev.peterdsp.poravia.core.model.Coverage
import dev.peterdsp.poravia.core.model.FavoritePlace
import dev.peterdsp.poravia.core.model.JourneyResults
import dev.peterdsp.poravia.core.model.Meta
import dev.peterdsp.poravia.core.model.OfflineCatalog
import dev.peterdsp.poravia.core.model.OperatorDetail
import dev.peterdsp.poravia.core.model.RecentSearch
import dev.peterdsp.poravia.core.model.SavedTrip
import dev.peterdsp.poravia.core.model.SourceList
import dev.peterdsp.poravia.core.model.StopDetail
import dev.peterdsp.poravia.features.JourneyDetailView
import dev.peterdsp.poravia.features.Loadable
import dev.peterdsp.poravia.features.offline.DownloadState
import dev.peterdsp.poravia.features.search.PlaceGroup
import dev.peterdsp.poravia.features.search.SearchEndpoint
import dev.peterdsp.poravia.features.search.SearchQuery
import dev.peterdsp.poravia.features.settings.PoraviaSettings
import dev.peterdsp.poravia.reminders.ReminderRecord
import dev.peterdsp.poravia.wallet.TicketRecord
import kotlinx.serialization.Serializable

/** The five top-level places in the application. */
enum class Tab { SEARCH, SAVED, OFFLINE, WALLET, SETTINGS }

/**
 * Everything a person would be annoyed to lose.
 *
 * This whole value is written into the activity's saved instance state, so a
 * rotation, a fold, a split-screen change, a background trim or an outright
 * process kill all come back to the same screen with the same query, the same
 * service date, the same filters, the same selected journey and the same
 * scroll position.
 *
 * [openTicketId] is deliberately only an identifier. The ticket document itself
 * is never persisted anywhere but its encrypted file: restoring "you were
 * looking at this ticket" re-reads and re-decrypts it, so the plaintext never
 * ends up in a system-saved bundle.
 */
@Serializable
data class SessionState(
    val query: SearchQuery,
    val tab: Tab = Tab.SEARCH,
    val selectedJourneyId: String? = null,
    val resultsScrollIndex: Int = 0,
    val resultsScrollOffset: Int = 0,
    val openTicketId: String? = null,
    val pickingEndpoint: SearchEndpoint? = null,
    /**
     * Set when a booking handoff was started and not yet returned from. It
     * survives the Custom Tab, so coming back lands on the same journey with
     * the same purchase surface rather than at the top of a fresh search.
     */
    val purchaseHandoffJourneyId: String? = null,
    val hasRunSearch: Boolean = false,
)

/** Why a link could not be resolved, in terms the screen can state. */
sealed interface UnresolvedLink {
    data class Unknown(val raw: String) : UnresolvedLink
    data class Expired(val raw: String, val serviceDate: String) : UnresolvedLink
    data class ForeignHost(val raw: String) : UnresolvedLink
}

/**
 * The whole application state in one value.
 *
 * A single state object is what makes the adaptive requirement provable: two
 * panes and one pane read exactly the same state, so a fold cannot leave the
 * detail pane looking at a journey the list no longer has.
 */
data class AppState(
    val settings: PoraviaSettings = PoraviaSettings(),
    val session: SessionState,
    val today: String,
    val meta: Loadable<Meta> = Loadable.Idle,
    val coverage: Loadable<Coverage> = Loadable.Idle,
    val sources: Loadable<SourceList> = Loadable.Idle,
    val places: Loadable<List<PlaceGroup>> = Loadable.Idle,
    val placeQuery: String = "",
    val recents: Loadable<List<RecentSearch>> = Loadable.Idle,
    val results: Loadable<JourneyResults> = Loadable.Idle,
    val journey: Loadable<JourneyDetailView> = Loadable.Idle,
    val stop: Loadable<StopDetail> = Loadable.Idle,
    val operator: Loadable<OperatorDetail> = Loadable.Idle,
    val savedTrips: Loadable<List<SavedTrip>> = Loadable.Idle,
    val favorites: Loadable<List<FavoritePlace>> = Loadable.Idle,
    val catalog: Loadable<OfflineCatalog> = Loadable.Idle,
    /**
     * Every download the shared [dev.peterdsp.poravia.features.offline.DownloadTracker]
     * knows about, running and finished. The tracker owns the handles, so a
     * rebuilt screen finds the same transfer rather than starting a second one.
     */
    val downloads: Map<String, DownloadState> = emptyMap(),
    val installedBytes: Long = 0,
    val tickets: List<TicketRecord> = emptyList(),
    val ticketBytes: ByteArray? = null,
    val walletMessage: WalletMessage? = null,
    val reminders: List<ReminderRecord> = emptyList(),
    val notificationsPermitted: Boolean = false,
    val reminderMessage: ReminderMessage? = null,
    val unresolvedLink: UnresolvedLink? = null,
    val pendingRoute: PendingRoute? = null,
    val announcement: String? = null,
) {
    /** The dates the installed release actually names, newest first. */
    val datesWithData: List<String>
        get() = catalog.valueOrNull?.let { value ->
            (value.available.map { it.name } + value.installed.map { it.name })
                .mapNotNull { name ->
                    JOURNEYS_PACK.matchEntire(name)?.groupValues?.get(1)
                }
                .distinct()
                .sorted()
        }.orEmpty()

    val isDemo: Boolean
        get() = settings.showsDemoNotice || meta.valueOrNull?.isDemo == true

    override fun equals(other: Any?): Boolean = this === other || (
        other is AppState &&
            settings == other.settings &&
            session == other.session &&
            today == other.today &&
            meta == other.meta &&
            coverage == other.coverage &&
            sources == other.sources &&
            places == other.places &&
            placeQuery == other.placeQuery &&
            recents == other.recents &&
            results == other.results &&
            journey == other.journey &&
            stop == other.stop &&
            operator == other.operator &&
            savedTrips == other.savedTrips &&
            favorites == other.favorites &&
            catalog == other.catalog &&
            downloads == other.downloads &&
            installedBytes == other.installedBytes &&
            tickets == other.tickets &&
            ticketBytes.contentEqualsOrNull(other.ticketBytes) &&
            walletMessage == other.walletMessage &&
            reminders == other.reminders &&
            notificationsPermitted == other.notificationsPermitted &&
            reminderMessage == other.reminderMessage &&
            unresolvedLink == other.unresolvedLink &&
            pendingRoute == other.pendingRoute &&
            announcement == other.announcement
        )

    override fun hashCode(): Int {
        var result = settings.hashCode()
        result = 31 * result + session.hashCode()
        result = 31 * result + today.hashCode()
        result = 31 * result + results.hashCode()
        result = 31 * result + journey.hashCode()
        result = 31 * result + tickets.hashCode()
        result = 31 * result + (ticketBytes?.size ?: 0)
        return result
    }

    private companion object {
        val JOURNEYS_PACK = Regex("journeys-(\\d{4}-\\d{2}-\\d{2})")

        fun ByteArray?.contentEqualsOrNull(other: ByteArray?): Boolean = when {
            this == null && other == null -> true
            this == null || other == null -> false
            else -> this.contentEquals(other)
        }
    }
}

sealed interface WalletMessage {
    data object UnsupportedType : WalletMessage
    data class TooLarge(val limitBytes: Long) : WalletMessage
    data object StorageFull : WalletMessage
    data object Unreadable : WalletMessage
    data object Deleted : WalletMessage
}

sealed interface ReminderMessage {
    data class Scheduled(val at: String) : ReminderMessage
    data object Cancelled : ReminderMessage
    data object InThePast : ReminderMessage
    data object PermissionDenied : ReminderMessage
    data object PermissionRevoked : ReminderMessage
}
