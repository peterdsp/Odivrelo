package dev.peterdsp.poravia.state

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.peterdsp.poravia.app.PoraviaBundle
import dev.peterdsp.poravia.app.PoraviaServices
import dev.peterdsp.poravia.app.loadable
import dev.peterdsp.poravia.core.model.JourneyFilters
import dev.peterdsp.poravia.core.model.Place
import dev.peterdsp.poravia.core.model.RecentSearch
import dev.peterdsp.poravia.core.time.ServiceTime
import dev.peterdsp.poravia.features.Loadable
import dev.peterdsp.poravia.features.offline.DownloadTracker
import dev.peterdsp.poravia.features.search.ChosenPlace
import dev.peterdsp.poravia.features.search.SearchEndpoint
import dev.peterdsp.poravia.features.search.SearchQuery
import dev.peterdsp.poravia.features.settings.Appearance
import dev.peterdsp.poravia.features.settings.AppDataMode
import dev.peterdsp.poravia.features.settings.PoraviaSettings
import dev.peterdsp.poravia.features.settings.SettingsFeature
import dev.peterdsp.poravia.reminders.ReminderOutcome
import dev.peterdsp.poravia.reminders.ReminderRecord
import dev.peterdsp.poravia.wallet.TicketImport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * The application's one piece of state and everything that changes it.
 *
 * A single view model rather than one per screen is a deliberate choice for
 * this product. The adaptive requirement is that two panes and one pane show
 * the same thing, and that folding, rotating, resizing or being killed and
 * restored changes nothing but the geometry. That is far easier to guarantee,
 * and to test, when there is exactly one state object and exactly one place
 * that writes it.
 *
 * The session half of the state is mirrored into [SavedStateHandle] on every
 * change, so it survives process death; the loaded data half is not, because
 * it is reproducible from the installed release and stale data restored from a
 * bundle would be a worse lie than a short reload.
 */
class PoraviaViewModel(
    application: Application,
    private val handle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val services = PoraviaServices.get(application)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val today: String = ServiceTime.currentServiceDate(Clock.System.now())

    /**
     * The first state, built without waiting for anything.
     *
     * The settings come from the synchronous mirror, so the first frame already
     * knows whether to draw the welcome screen or the application. Waiting for
     * DataStore here would mean drawing an empty window and hoping, and an empty
     * window is exactly what a person would report as "it does not start".
     */
    private val _state = MutableStateFlow(
        AppState(
            settings = services.settingsStore.mirroredSettings() ?: PoraviaSettings(),
            session = restoreSession(),
            today = today,
            notificationsPermitted = services.reminders.hasNotificationPermission(),
        ),
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    /**
     * The downloads in flight, owned outside any screen.
     *
     * This is the shared [DownloadTracker], not a local guard: it is what makes
     * "a geometry change must not restart a download" true. A rotation, a fold
     * or a split-screen resize rebuilds the screen, which asks the tracker to
     * start the same pack again, and the tracker finds the running handle and
     * does nothing.
     */
    private val downloads = DownloadTracker()
    private var placeJob: Job? = null
    private var searchJob: Job? = null
    private var journeyJob: Job? = null

    /**
     * The search whose results are currently held. A geometry change re-renders
     * but must not re-issue an identical query, so a request is skipped when the
     * key has not moved and the answer is already in hand.
     */
    private var loadedSearchKey: String? = null
    private var loadedJourneyKey: String? = null

    init {
        viewModelScope.launch {
            services.settingsStore.settings.collect { settings ->
                val previous = _state.value.settings
                _state.value = _state.value.copy(settings = settings)
                if (previous.resolvedLanguageTag != settings.resolvedLanguageTag ||
                    !_state.value.metaRequested
                ) {
                    refreshRelease()
                }
            }
        }
        viewModelScope.launch {
            services.reminderStore.reminders.collect { records ->
                _state.value = _state.value.copy(reminders = records)
            }
        }
        viewModelScope.launch { refreshTickets() }
    }

    private val AppState.metaRequested: Boolean get() = meta !is Loadable.Idle

    private suspend fun bundle(): PoraviaBundle =
        services.bundle(_state.value.settings.resolvedLanguageTag)

    // -- Session persistence -------------------------------------------------

    private fun restoreSession(): SessionState {
        val raw = handle.get<String>(SESSION_KEY)
        if (raw != null) {
            runCatching { json.decodeFromString(SessionState.serializer(), raw) }
                .getOrNull()
                ?.let { return it }
        }
        return SessionState(query = SearchQuery(serviceDate = today))
    }

    private fun mutateSession(transform: (SessionState) -> SessionState) {
        val next = transform(_state.value.session)
        handle[SESSION_KEY] = json.encodeToString(SessionState.serializer(), next)
        _state.value = _state.value.copy(session = next)
    }

    // -- Settings ------------------------------------------------------------

    fun updateSettings(transform: (PoraviaSettings) -> PoraviaSettings) {
        viewModelScope.launch { services.settingsStore.update(transform) }
    }

    // Every settings change goes through the shared feature rather than a copy()
    // here, so a value this application cannot honour (an unknown language, a
    // reminder lead nobody offers) cannot be written at all.
    fun setLanguage(tag: String) = updateSettings { SettingsFeature.withLanguage(it, tag) }

    fun setAppearance(appearance: Appearance) =
        updateSettings { SettingsFeature.withAppearance(it, appearance) }

    fun setDataMode(mode: AppDataMode) = updateSettings { SettingsFeature.withDataMode(it, mode) }

    fun setLargerTouchTargets(enabled: Boolean) =
        updateSettings { SettingsFeature.withLargerTouchTargets(it, enabled) }

    fun setReduceMotion(enabled: Boolean) =
        updateSettings { SettingsFeature.withReduceMotion(it, enabled) }

    fun setAlwaysExpandStops(enabled: Boolean) =
        updateSettings { SettingsFeature.withAlwaysExpandStops(it, enabled) }

    fun setMeteredDownloads(enabled: Boolean) =
        updateSettings { SettingsFeature.withMeteredDownloads(it, enabled) }

    fun setReminderLead(minutes: Int) =
        updateSettings { SettingsFeature.withReminderLead(it, minutes) }

    fun completeFirstRun() = updateSettings { SettingsFeature.completingFirstRun(it) }

    // -- Navigation session --------------------------------------------------

    fun selectTab(tab: Tab) {
        mutateSession { it.copy(tab = tab) }
        when (tab) {
            Tab.SAVED -> refreshLibrary()
            Tab.OFFLINE -> refreshCatalog()
            Tab.WALLET -> refreshTicketsAsync()
            Tab.SETTINGS -> refreshRelease()
            Tab.SEARCH -> Unit
        }
    }

    fun openPlacePicker(endpoint: SearchEndpoint) {
        mutateSession { it.copy(pickingEndpoint = endpoint) }
        _state.value = _state.value.copy(placeQuery = "", places = Loadable.Idle)
    }

    fun closePlacePicker() {
        mutateSession { it.copy(pickingEndpoint = null) }
    }

    fun setPlaceQuery(query: String) {
        _state.value = _state.value.copy(placeQuery = query, places = Loadable.Loading())
        placeJob?.cancel()
        placeJob = viewModelScope.launch {
            val outcome = bundle().search.places(query)
            _state.value = _state.value.copy(places = outcome)
        }
    }

    fun choosePlace(place: Place, parent: Place?) {
        val endpoint = _state.value.session.pickingEndpoint ?: return
        viewModelScope.launch {
            val chosen = bundle().search.chosenPlace(place, parent)
            mutateSession { session ->
                session.copy(
                    query = session.query.with(endpoint, chosen),
                    pickingEndpoint = null,
                    selectedJourneyId = null,
                )
            }
            _state.value = _state.value.copy(results = Loadable.Idle)
            loadedSearchKey = null
        }
    }

    fun clearEndpoint(endpoint: SearchEndpoint) {
        mutateSession { it.copy(query = it.query.with(endpoint, null), selectedJourneyId = null) }
        _state.value = _state.value.copy(results = Loadable.Idle)
        loadedSearchKey = null
    }

    fun swapEndpoints() {
        mutateSession { it.copy(query = it.query.swapped(), selectedJourneyId = null) }
        _state.value = _state.value.copy(results = Loadable.Idle)
        loadedSearchKey = null
    }

    fun setServiceDate(serviceDate: String) {
        if (ServiceTime.parseServiceDateOrNull(serviceDate) == null) return
        mutateSession { it.copy(query = it.query.copy(serviceDate = serviceDate), selectedJourneyId = null) }
        _state.value = _state.value.copy(results = Loadable.Idle)
        loadedSearchKey = null
    }

    fun shiftServiceDate(days: Int) {
        val next = ServiceTime.shiftServiceDate(_state.value.session.query.serviceDate, days)
        // A service date before today is not offered: the release holds no
        // timetable a person can act on for a day that has gone.
        if (next < today) return
        setServiceDate(next)
    }

    fun setFilters(filters: JourneyFilters) {
        mutateSession { it.copy(query = it.query.copy(filters = filters)) }
        loadedSearchKey = null
        if (_state.value.session.hasRunSearch) runSearch()
    }

    fun clearFilters() = setFilters(JourneyFilters.NONE)

    fun applyRecent(recent: RecentSearch) {
        viewModelScope.launch {
            val query = bundle().search.queryFrom(recent, keepDate = true, today = today)
            mutateSession { it.copy(query = query, selectedJourneyId = null) }
            _state.value = _state.value.copy(results = Loadable.Idle)
            loadedSearchKey = null
            runSearch()
        }
    }

    fun setResultsScroll(index: Int, offset: Int) {
        val session = _state.value.session
        if (session.resultsScrollIndex == index && session.resultsScrollOffset == offset) return
        mutateSession { it.copy(resultsScrollIndex = index, resultsScrollOffset = offset) }
    }

    // -- Loading -------------------------------------------------------------

    fun refreshRelease() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                meta = Loadable.Loading(_state.value.meta.valueOrNull),
            )
            val loaded = bundle()
            val meta = loaded.about.meta()
            val coverage = loaded.about.coverage()
            val sources = loaded.about.sources()
            _state.value = _state.value.copy(meta = meta, coverage = coverage, sources = sources)
            refreshRecents()
            // The search screen names the dates this release actually covers,
            // which it can only do once the catalogue is known.
            refreshCatalog()
        }
    }

    fun refreshRecents() {
        viewModelScope.launch {
            _state.value = _state.value.copy(recents = bundle().search.recents())
        }
    }

    /**
     * Runs the current search.
     *
     * [force] is what a retry button passes. Without it an identical query whose
     * answer is already held is not re-issued, which is what keeps a rotation, a
     * fold or a split-screen change from firing a second search.
     */
    fun runSearch(force: Boolean = false) {
        val query = _state.value.session.query
        val key = searchKey(query)
        if (!force && key == loadedSearchKey && _state.value.results !is Loadable.Idle) return

        searchJob?.cancel()
        mutateSession { it.copy(hasRunSearch = true) }
        _state.value = _state.value.copy(
            results = Loadable.Loading(_state.value.results.valueOrNull),
        )
        searchJob = viewModelScope.launch {
            // SearchFeature owns every decision about what an empty answer
            // means, including keeping "this device holds no timetable for that
            // date" apart from "no service runs that date".
            val outcome = bundle().search.run(query)
            loadedSearchKey = key
            _state.value = _state.value.copy(results = outcome)
            refreshRecents()
        }
    }

    fun loadStop(stopId: String, serviceDate: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                stop = Loadable.Loading(_state.value.stop.valueOrNull),
            )
            // Deliberately the core rather than StopFeature: the feature maps
            // an empty departure list to NO_SERVICE_ON_DATE, which would say
            // "nothing runs" on a date this device simply holds no pack for.
            // StopDetail.dateDataState is the honest answer and this screen
            // reads it.
            val outcome = loadable { bundle().core.stopDetail(stopId, serviceDate) }
            _state.value = _state.value.copy(stop = outcome)
        }
    }

    fun loadOperator(operatorId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                operator = Loadable.Loading(_state.value.operator.valueOrNull),
            )
            _state.value = _state.value.copy(operator = bundle().operator.load(operatorId))
        }
    }

    fun selectJourney(journeyId: String?) {
        mutateSession { it.copy(selectedJourneyId = journeyId) }
        if (journeyId == null) {
            _state.value = _state.value.copy(journey = Loadable.Idle)
            loadedJourneyKey = null
        } else {
            loadJourney(journeyId, _state.value.session.query.serviceDate)
        }
    }

    fun loadJourney(journeyId: String, serviceDate: String, force: Boolean = false) {
        val key = journeyId + "@" + serviceDate
        if (!force && key == loadedJourneyKey && _state.value.journey !is Loadable.Idle) return
        journeyJob?.cancel()
        _state.value = _state.value.copy(
            journey = Loadable.Loading(_state.value.journey.valueOrNull),
        )
        journeyJob = viewModelScope.launch {
            val outcome = bundle().journey.load(journeyId, serviceDate)
            loadedJourneyKey = key
            _state.value = _state.value.copy(journey = outcome)
            refreshLibrary()
        }
    }

    // -- Library -------------------------------------------------------------

    fun refreshLibrary() {
        viewModelScope.launch {
            val loaded = bundle()
            _state.value = _state.value.copy(
                savedTrips = loaded.library.savedTrips(),
                favorites = loaded.library.favorites(),
            )
        }
    }

    fun saveTrip(journeyId: String, serviceDate: String) {
        viewModelScope.launch {
            bundle().journey.save(journeyId, serviceDate)
            refreshLibrary()
        }
    }

    fun removeSavedTrip(savedTripId: String) {
        viewModelScope.launch {
            services.reminders.cancel(savedTripId)
            bundle().library.removeSavedTrip(savedTripId)
            refreshLibrary()
        }
    }

    fun toggleFavorite(placeId: String) {
        viewModelScope.launch {
            bundle().library.toggleFavorite(placeId)
            refreshLibrary()
        }
    }

    // -- Offline packs -------------------------------------------------------

    fun refreshCatalog() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                catalog = Loadable.Loading(_state.value.catalog.valueOrNull),
            )
            val outcome = bundle().offline.catalog()
            _state.value = _state.value.copy(
                catalog = outcome,
                installedBytes = services.installedBytes(),
            )
        }
    }

    /**
     * Starts a download, or does nothing when one for the same pack is already
     * running. Called again after a rotation or a fold, it finds the existing
     * handle and leaves it alone, which is why a geometry change cannot restart
     * a transfer.
     */
    fun downloadPack(packName: String) {
        if (downloads.isRunning(packName)) return
        viewModelScope.launch {
            val loaded = bundle()
            val declaredBytes = _state.value.catalog.valueOrNull
                ?.available
                ?.firstOrNull { it.name == packName }
                ?.bytes
                ?: 0L
            val started = downloads.start(
                packName = packName,
                totalBytes = declaredBytes,
                onChanged = { download ->
                    _state.value = _state.value.copy(downloads = downloads.snapshot())
                    if (download.finished && download.succeeded) refreshCatalog()
                },
                launch = { onProgress, onResult ->
                    loaded.offline.download(packName, onProgress, onResult)
                },
            )
            if (started) _state.value = _state.value.copy(downloads = downloads.snapshot())
        }
    }

    fun cancelDownload(packName: String) {
        downloads.cancel(packName) {
            _state.value = _state.value.copy(downloads = downloads.snapshot())
        }
    }

    /** Clears a finished row so it goes back to its resting state. */
    fun dismissDownload(packName: String) {
        downloads.clear(packName)
        _state.value = _state.value.copy(downloads = downloads.snapshot())
    }

    fun removePack(packName: String) {
        viewModelScope.launch {
            bundle().offline.remove(packName)
            refreshCatalog()
        }
    }

    fun rollbackRelease() {
        viewModelScope.launch {
            bundle().offline.rollback()
            refreshRelease()
            refreshCatalog()
        }
    }

    fun clearOfflineStorage() {
        viewModelScope.launch {
            downloads.cancelAll()
            services.clearOfflineStorage()
            loadedSearchKey = null
            loadedJourneyKey = null
            _state.value = _state.value.copy(
                results = Loadable.Idle,
                journey = Loadable.Idle,
                catalog = Loadable.Idle,
                meta = Loadable.Idle,
                downloads = emptyMap(),
                installedBytes = 0,
            )
            refreshRelease()
            refreshCatalog()
        }
    }

    // -- Travel wallet -------------------------------------------------------

    private fun refreshTicketsAsync() {
        viewModelScope.launch { refreshTickets() }
    }

    private suspend fun refreshTickets() {
        _state.value = _state.value.copy(tickets = services.ticketStore.list())
    }

    fun importTicket(uri: Uri) {
        viewModelScope.launch {
            val now = ServiceTime.formatIsoWithAthensOffset(Clock.System.now())
            when (val outcome = services.ticketStore.import(uri, now)) {
                is TicketImport.Imported -> {
                    refreshTickets()
                    _state.value = _state.value.copy(walletMessage = null)
                }

                TicketImport.UnsupportedType ->
                    _state.value = _state.value.copy(walletMessage = WalletMessage.UnsupportedType)

                is TicketImport.TooLarge ->
                    _state.value = _state.value.copy(
                        walletMessage = WalletMessage.TooLarge(outcome.limitBytes),
                    )

                TicketImport.StorageFull ->
                    _state.value = _state.value.copy(walletMessage = WalletMessage.StorageFull)

                is TicketImport.Unreadable ->
                    _state.value = _state.value.copy(walletMessage = WalletMessage.Unreadable)
            }
        }
    }

    fun openTicket(id: String) {
        mutateSession { it.copy(openTicketId = id) }
        viewModelScope.launch {
            _state.value = _state.value.copy(ticketBytes = services.ticketStore.read(id))
        }
    }

    /**
     * Drops the decrypted bytes. Called when the ticket view closes and when the
     * application goes to the background, so a plaintext ticket is never sitting
     * in memory behind a recents thumbnail.
     */
    fun closeTicket(keepSelection: Boolean = false) {
        if (!keepSelection) mutateSession { it.copy(openTicketId = null) }
        _state.value = _state.value.copy(ticketBytes = null)
    }

    /** Re-reads the open ticket after the application comes back to the front. */
    fun reopenTicketIfAny() {
        val id = _state.value.session.openTicketId ?: return
        if (_state.value.ticketBytes != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(ticketBytes = services.ticketStore.read(id))
        }
    }

    fun deleteTicket(id: String) {
        viewModelScope.launch {
            services.ticketStore.delete(id)
            if (_state.value.session.openTicketId == id) closeTicket()
            refreshTickets()
            _state.value = _state.value.copy(walletMessage = WalletMessage.Deleted)
        }
    }

    fun clearWalletMessage() {
        _state.value = _state.value.copy(walletMessage = null)
    }

    // -- Reminders -----------------------------------------------------------

    fun refreshNotificationPermission() {
        val permitted = services.reminders.hasNotificationPermission()
        val previous = _state.value.notificationsPermitted
        _state.value = _state.value.copy(notificationsPermitted = permitted)
        if (previous && !permitted) {
            viewModelScope.launch {
                services.reminders.rescheduleAll()
                _state.value = _state.value.copy(
                    reminderMessage = ReminderMessage.PermissionRevoked,
                )
            }
        }
    }

    fun scheduleReminder(record: ReminderRecord) {
        viewModelScope.launch {
            when (val outcome = services.reminders.schedule(record)) {
                is ReminderOutcome.Scheduled -> _state.value = _state.value.copy(
                    reminderMessage = ReminderMessage.Scheduled(outcome.record.departureLabel),
                )

                ReminderOutcome.InThePast -> _state.value = _state.value.copy(
                    reminderMessage = ReminderMessage.InThePast,
                )

                ReminderOutcome.PermissionMissing -> _state.value = _state.value.copy(
                    notificationsPermitted = false,
                    reminderMessage = ReminderMessage.PermissionDenied,
                )
            }
        }
    }

    fun cancelReminder(savedTripId: String) {
        viewModelScope.launch {
            services.reminders.cancel(savedTripId)
            _state.value = _state.value.copy(reminderMessage = ReminderMessage.Cancelled)
        }
    }

    fun clearReminderMessage() {
        _state.value = _state.value.copy(reminderMessage = null)
    }

    // -- Booking handoff -----------------------------------------------------

    fun beginPurchaseHandoff(journeyId: String) {
        mutateSession { it.copy(purchaseHandoffJourneyId = journeyId) }
    }

    /**
     * Called when the application comes back to the front. The handoff marker is
     * cleared but nothing else is: the person returns to the same journey, the
     * same service date and the same purchase surface they left.
     */
    fun endPurchaseHandoff() {
        if (_state.value.session.purchaseHandoffJourneyId == null) return
        mutateSession { it.copy(purchaseHandoffJourneyId = null) }
    }

    // -- Deep links ----------------------------------------------------------

    /**
     * The link the activity was started with, applied exactly once.
     *
     * The activity is recreated on every rotation, resize and fold, and each
     * recreation hands the same intent back. Re-applying it would reset the
     * tab, the selection and the search every time the geometry changed, which
     * is precisely the state loss the adaptive gate forbids. The marker lives
     * in saved state, so a restore after process death does not re-apply it
     * either: the restored session is the more recent truth.
     */
    fun handleInitialLink(uri: Uri?) {
        if (handle.get<Boolean>(INITIAL_LINK_KEY) == true) return
        handle[INITIAL_LINK_KEY] = true
        if (uri != null) handleDeepLink(uri)
    }

    fun handleDeepLink(uri: Uri) {
        when (val result = DeepLinkParser.parse(uri, today)) {
            is DeepLinkResult.Unresolved ->
                _state.value = _state.value.copy(unresolvedLink = result.reason)

            is DeepLinkResult.Resolved -> apply(result.link)
        }
    }

    fun clearUnresolvedLink() {
        _state.value = _state.value.copy(unresolvedLink = null)
    }

    private fun apply(link: DeepLink) {
        _state.value = _state.value.copy(unresolvedLink = null)
        when (link) {
            is DeepLink.Search -> {
                mutateSession { session ->
                    session.copy(
                        tab = Tab.SEARCH,
                        selectedJourneyId = null,
                        query = session.query.copy(
                            origin = link.originId?.let { id ->
                                session.query.origin?.takeIf { it.id == id }
                                    ?: ChosenPlace(
                                        id = id,
                                        label = id,
                                        kind = dev.peterdsp.poravia.core.model.PlaceKind.STOP_PLACE,
                                    )
                            } ?: session.query.origin,
                            destination = link.destinationId?.let { id ->
                                session.query.destination?.takeIf { it.id == id }
                                    ?: ChosenPlace(
                                        id = id,
                                        label = id,
                                        kind = dev.peterdsp.poravia.core.model.PlaceKind.STOP_PLACE,
                                    )
                            } ?: session.query.destination,
                            serviceDate = link.serviceDate ?: session.query.serviceDate,
                        ),
                    )
                }
                loadedSearchKey = null
                resolveEndpointLabels()
                if (_state.value.session.query.isRunnable) runSearch(force = true)
            }

            is DeepLink.Journey -> {
                mutateSession {
                    it.copy(
                        tab = Tab.SEARCH,
                        selectedJourneyId = link.id,
                        query = it.query.copy(serviceDate = link.serviceDate),
                    )
                }
                loadJourney(link.id, link.serviceDate, force = true)
            }

            is DeepLink.Operator -> _state.value = _state.value.copy(
                pendingRoute = PendingRoute.Operator(link.id),
            )

            is DeepLink.Stop -> _state.value = _state.value.copy(
                pendingRoute = PendingRoute.Stop(
                    link.id,
                    link.serviceDate ?: _state.value.session.query.serviceDate,
                ),
            )

            DeepLink.Offline -> selectTab(Tab.OFFLINE)
            DeepLink.Trips -> selectTab(Tab.SAVED)
            DeepLink.Wallet -> selectTab(Tab.WALLET)
            DeepLink.Settings -> selectTab(Tab.SETTINGS)
            DeepLink.Coverage -> {
                selectTab(Tab.SETTINGS)
                _state.value = _state.value.copy(pendingRoute = PendingRoute.Coverage)
            }

            DeepLink.Sources -> {
                selectTab(Tab.SETTINGS)
                _state.value = _state.value.copy(pendingRoute = PendingRoute.Sources)
            }
        }
    }

    fun consumePendingRoute() {
        _state.value = _state.value.copy(pendingRoute = null)
    }

    /**
     * A link carries opaque identifiers, not names. The endpoints are shown with
     * their identifier until the release can supply the real label, which is
     * honest about what is known rather than leaving the field blank.
     */
    private fun resolveEndpointLabels() {
        viewModelScope.launch {
            val loaded = bundle()
            val session = _state.value.session
            val origin = session.query.origin
            val destination = session.query.destination
            if (origin == null && destination == null) return@launch
            val lookups = listOfNotNull(origin, destination)
                .filter { it.label == it.id }
                .mapNotNull { chosen ->
                    val found = loaded.search.places(chosen.id)
                    (found as? Loadable.Ready)?.value
                        ?.flatMap { group -> listOfNotNull(group.terminal) + group.boardingPoints }
                        ?.firstOrNull { it.id == chosen.id }
                        ?.let { chosen.id to it }
                }
                .toMap()
            if (lookups.isEmpty()) return@launch
            mutateSession { current ->
                current.copy(
                    query = current.query.copy(
                        origin = current.query.origin?.let { chosen ->
                            lookups[chosen.id]?.let {
                                chosen.copy(
                                    label = it.name.resolve(loaded.languageTag),
                                    kind = it.kind,
                                )
                            } ?: chosen
                        },
                        destination = current.query.destination?.let { chosen ->
                            lookups[chosen.id]?.let {
                                chosen.copy(
                                    label = it.name.resolve(loaded.languageTag),
                                    kind = it.kind,
                                )
                            } ?: chosen
                        },
                    ),
                )
            }
        }
    }

    // -- Diagnostics ---------------------------------------------------------

    /**
     * Technical facts that identify a build and its data exactly, and nothing
     * else. No identifiers, no ticket information, no file paths, no secrets.
     */
    fun diagnosticsText(windowLabel: String, postureLabel: String): String {
        val current = _state.value
        val meta = current.meta.valueOrNull
        return buildString {
            appendLine("product=" + dev.peterdsp.poravia.core.Brand.NAME)
            appendLine("version=" + dev.peterdsp.poravia.BuildConfig.VERSION_NAME)
            appendLine("versionCode=" + dev.peterdsp.poravia.BuildConfig.VERSION_CODE)
            appendLine("commit=" + dev.peterdsp.poravia.BuildConfig.GIT_COMMIT)
            appendLine("contract=" + dev.peterdsp.poravia.core.Brand.CONTRACT_VERSION)
            appendLine("releaseId=" + (meta?.releaseId ?: "none"))
            appendLine("releasePublishedAt=" + (meta?.publishedAt ?: "none"))
            appendLine("releaseDataMode=" + (meta?.dataMode?.name?.lowercase() ?: "unknown"))
            appendLine("appDataMode=" + current.settings.dataMode.name.lowercase())
            appendLine("language=" + current.settings.resolvedLanguageTag)
            appendLine("appearance=" + current.settings.appearance.name.lowercase())
            appendLine("serviceDate=" + current.session.query.serviceDate)
            appendLine("today=" + current.today)
            appendLine("window=" + windowLabel)
            appendLine("posture=" + postureLabel)
            appendLine("installedPacks=" + (current.catalog.valueOrNull?.installed?.size ?: 0))
            appendLine("installedBytes=" + current.installedBytes)
            appendLine("notificationsPermitted=" + current.notificationsPermitted)
            appendLine("remindersScheduled=" + current.reminders.size)
            appendLine("ticketsStored=" + current.tickets.size)
            appendLine("sdk=" + android.os.Build.VERSION.SDK_INT)
            append("device=" + android.os.Build.MODEL)
        }
    }

    override fun onCleared() {
        downloads.cancelAll()
        super.onCleared()
    }

    private fun searchKey(query: SearchQuery): String = listOf(
        query.origin?.id.orEmpty(),
        query.destination?.id.orEmpty(),
        query.serviceDate,
        query.filters.toString(),
    ).joinToString("|")

    companion object {
        private const val SESSION_KEY = "poravia.session"
        private const val INITIAL_LINK_KEY = "poravia.initialLinkHandled"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory
                    .APPLICATION_KEY] as Application
                PoraviaViewModel(application, createSavedStateHandle())
            }
        }
    }
}

/** A one-shot navigation a deep link asked for, consumed by the navigator. */
sealed interface PendingRoute {
    data class Operator(val id: String) : PendingRoute
    data class Stop(val id: String, val serviceDate: String) : PendingRoute
    data object Coverage : PendingRoute
    data object Sources : PendingRoute
}
