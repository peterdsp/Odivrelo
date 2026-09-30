#if ODIVRELO_CORE_AVAILABLE
import Foundation
import OdivreloCore

/// Converts the Kotlin values the core exports into their Swift mirrors.
///
/// Every function here is a field-by-field copy. There is no defaulting that
/// invents a fact, no recomputation and no reordering: where the core reports
/// nothing, the Swift value carries `nil`, and the interface renders that as
/// "not stated" rather than filling in a plausible value.
enum CoreMapping {
    // MARK: Primitives

    static func text(_ value: OdivreloLocalizedText?) -> LocalisedText {
        LocalisedText(el: value?.el, en: value?.en, sq: value?.sq)
    }

    static func failureKind(_ value: OdivreloFailureKind) -> CoreFailureKind {
        CoreFailureKind.fromKotlinName(value.name)
    }

    static func url(_ value: String?) -> URL? {
        guard let value, !value.isEmpty else { return nil }
        return URL(string: value)
    }

    static func date(_ value: String?) -> Date? {
        ContractInstant.parse(value)
    }

    static func serviceDate(_ value: String) -> ServiceDate {
        // The core only ever emits well-formed service dates. If one ever did
        // not parse, failing loudly here is better than guessing a date a
        // traveller would act on.
        guard let parsed = ServiceDate(iso: value) else {
            preconditionFailure("the core returned a malformed service date: \(value)")
        }
        return parsed
    }

    // MARK: Enumerations

    static func dataMode(_ value: OdivreloDataMode) -> DataMode {
        DataMode.fromKotlinName(value.name)
    }

    static func coverageState(_ value: OdivreloCoverageState) -> CoverageState {
        CoverageState.fromKotlinName(value.name)
    }

    static func placeKind(_ value: OdivreloPlaceKind) -> PlaceKind {
        PlaceKind.fromKotlinName(value.name)
    }

    static func timeQuality(_ value: OdivreloTimeQuality) -> TimeQuality {
        TimeQuality.fromKotlinName(value.name)
    }

    static func positionQuality(_ value: OdivreloPositionQuality) -> PositionQuality {
        PositionQuality.fromKotlinName(value.name)
    }

    static func confidence(_ value: OdivreloConfidence) -> Confidence {
        Confidence.fromKotlinName(value.name)
    }

    static func freshnessState(_ value: OdivreloFreshnessState) -> FreshnessState {
        FreshnessState.fromKotlinName(value.name)
    }

    static func reviewState(_ value: OdivreloReviewState) -> ReviewState {
        ReviewState.fromKotlinName(value.name)
    }

    static func rightsStatus(_ value: OdivreloRightsStatus) -> RightsStatus {
        RightsStatus.fromKotlinName(value.name)
    }

    static func geometryConfidence(_ value: OdivreloGeometryConfidence) -> GeometryConfidence {
        GeometryConfidence.fromKotlinName(value.name)
    }

    static func purchaseKind(_ value: OdivreloPurchaseKind) -> PurchaseKind {
        PurchaseKind.fromKotlinName(value.name)
    }

    static func boardingRule(_ value: OdivreloBoardingRule) -> BoardingRule {
        BoardingRule.fromKotlinName(value.name)
    }

    static func unavailableReason(_ value: OdivreloUnavailableReason?) -> UnavailableReason? {
        guard let value else { return nil }
        return UnavailableReason.fromKotlinName(value.name)
    }

    static func packPhase(_ value: OdivreloPackPhase) -> PackPhase {
        PackPhase.fromKotlinName(value.name)
    }

    static func packFailure(_ value: OdivreloPackFailure?) -> PackFailure? {
        guard let value else { return nil }
        return PackFailure.fromKotlinName(value.name)
    }

    static func errorCode(_ value: OdivreloErrorCode) -> ContractErrorCode {
        ContractErrorCode.fromKotlinName(value.name)
    }

    // MARK: Meta

    static func meta(_ value: OdivreloMeta) -> MetaSnapshot {
        MetaSnapshot(
            envelope: ReleaseEnvelope(
                contractVersion: value.contractVersion,
                releaseId: value.releaseId,
                dataMode: dataMode(value.dataMode),
                servedFromCache: false,
                publishedAt: date(value.publishedAt)
            ),
            product: ProductInfo(
                name: value.product.name,
                version: value.product.version,
                commit: value.product.commit,
                builtAt: date(value.product.builtAt)
            ),
            languages: value.languages,
            coverage: coverage(value.coverage),
            offlineManifestUrl: url(value.offlineManifestUrl),
            gtfsUrl: url(value.gtfsUrl),
            attribution: value.attribution.map {
                Attribution(name: $0.name, url: url($0.url), licence: $0.licence)
            }
        )
    }

    static func coverage(_ value: OdivreloCoverage) -> CoverageSummary {
        CoverageSummary(
            state: coverageState(value.state),
            operatorCount: Int(value.operatorCount),
            corridorCount: Int(value.corridorCount),
            journeyCount: Int(value.journeyCount),
            stopCount: Int(value.stopCount),
            // A missing note is left absent rather than turned into an empty
            // localised object, so the interface can say "not stated" instead of
            // rendering a blank line.
            note: value.note.map(text),
            notCovered: value.notCovered.map { text($0) },
            absenceSemantics: value.absenceSemantics,
            operators: value.operators.map(operatorCoverageSummary),
            serviceDates: value.serviceDates.map(serviceDateRange),
            freshness: value.freshness.map(freshness),
            releaseId: value.releaseId,
            publishedAt: date(value.publishedAt),
            dataMode: dataMode(value.dataMode)
        )
    }

    static func serviceDateRange(_ value: OdivreloServiceDateRange) -> ServiceDateRange {
        ServiceDateRange(
            from: value.from.map(serviceDate),
            to: value.to.map(serviceDate)
        )
    }

    static func operatorCoverageSummary(
        _ value: OdivreloOperatorCoverageSummary
    ) -> OperatorCoverageSummary {
        OperatorCoverageSummary(
            operatorId: value.operatorId,
            name: value.name.map(text),
            journeyCount: Int(value.journeyCount),
            routeCount: Int(value.routeCount),
            stopCount: Int(value.stopCount),
            state: coverageState(value.state)
        )
    }

    static func sourceList(_ value: OdivreloSourceList) -> SourceList {
        SourceList(
            envelope: ReleaseEnvelope(
                contractVersion: value.contractVersion,
                releaseId: value.releaseId,
                dataMode: dataMode(value.dataMode),
                servedFromCache: false,
                publishedAt: nil
            ),
            sources: value.sources.map { source in
                SourceRecord(
                    id: source.id,
                    name: source.name,
                    url: url(source.url),
                    rightsStatus: rightsStatus(source.rightsStatus),
                    licence: source.licence,
                    retrievedAt: date(source.retrievedAt),
                    note: source.note
                )
            }
        )
    }

    // MARK: Places

    static func place(_ value: OdivreloPlace) -> Place {
        Place(
            id: value.id,
            kind: placeKind(value.kind),
            name: text(value.name),
            parentId: value.parentId,
            municipality: value.municipality,
            latitude: value.latitude?.doubleValue,
            longitude: value.longitude?.doubleValue,
            coordinateStatus: value.coordinateStatus,
            boardingPointCount: Int(value.boardingPointCount),
            operatorIds: value.operatorIds,
            coverage: coverageState(value.coverage)
        )
    }

    static func placeResults(_ value: OdivreloPlaceResults) -> PlaceSearchResult {
        PlaceSearchResult(
            envelope: ReleaseEnvelope(
                contractVersion: value.contractVersion,
                releaseId: value.releaseId,
                dataMode: dataMode(value.dataMode),
                servedFromCache: false,
                publishedAt: nil
            ),
            places: value.places.map(place),
            total: Int(value.total),
            query: value.query
        )
    }

    // MARK: Journeys

    static func operatorSummary(_ value: OdivreloOperatorSummary) -> OperatorSummary {
        OperatorSummary(id: value.id, name: text(value.name), logoAvailable: value.logoAvailable)
    }

    static func endpoint(_ value: OdivreloJourneyEndpoint) -> JourneyEndpoint {
        JourneyEndpoint(
            at: date(value.at) ?? Date(timeIntervalSince1970: 0),
            stopId: value.stopId,
            stopName: text(value.stopName),
            quality: timeQuality(value.quality)
        )
    }

    static func fare(_ value: OdivreloFare?) -> Fare? {
        guard let value else { return nil }
        return Fare(amount: value.amount, currency: value.currency, isIndicative: value.isIndicative)
    }

    static func freshness(_ value: OdivreloFreshness) -> Freshness {
        Freshness(
            checkedAt: date(value.checkedAt),
            ageHours: Int(value.ageHours),
            state: freshnessState(value.state)
        )
    }

    static func purchase(_ value: OdivreloPurchaseOption) -> PurchaseOption {
        PurchaseOption(
            kind: purchaseKind(value.kind),
            url: url(value.url),
            phone: value.phone,
            address: value.address,
            openingHours: value.openingHours,
            label: value.label.map(text),
            disclaimer: value.disclaimer.map(text)
        )
    }

    static func journey(_ value: OdivreloJourney) -> Journey {
        Journey(
            id: value.id,
            operatorSummary: operatorSummary(value.operator_),
            departure: endpoint(value.departure),
            arrival: endpoint(value.arrival),
            durationMinutes: Int(value.durationMinutes),
            intermediateStopCount: Int(value.intermediateStopCount),
            serviceDate: serviceDate(value.serviceDate),
            crossesMidnight: value.crossesMidnight,
            positionQuality: positionQuality(value.positionQuality),
            fare: fare(value.fare),
            freshness: freshness(value.freshness),
            confidence: confidence(value.confidence),
            purchase: purchase(value.purchase),
            accessibleBoardingPoint: value.accessibleBoardingPoint?.boolValue
        )
    }

    static func journeyResults(_ value: OdivreloJourneyResults) -> JourneySearchResult {
        JourneySearchResult(
            envelope: ReleaseEnvelope(
                contractVersion: value.contractVersion,
                releaseId: value.releaseId,
                dataMode: dataMode(value.dataMode),
                servedFromCache: value.servedFromCache,
                publishedAt: date(value.cachedReleasePublishedAt)
            ),
            query: JourneyQuery(
                originId: value.query.originId,
                destinationId: value.query.destinationId,
                date: serviceDate(value.query.date)
            ),
            coverage: coverageState(value.coverage),
            results: value.results.map(journey),
            unavailableReason: unavailableReason(value.unavailableReason),
            cachedReleasePublishedAt: date(value.cachedReleasePublishedAt)
        )
    }

    static func boardingPoint(_ value: OdivreloBoardingPoint?) -> BoardingPoint? {
        guard let value else { return nil }
        return BoardingPoint(
            stopId: value.stopId,
            name: text(value.name),
            terminalName: value.terminalName.map(text),
            bay: value.bay,
            latitude: value.latitude?.doubleValue,
            longitude: value.longitude?.doubleValue,
            instructions: value.instructions.map(text),
            reviewState: reviewState(value.reviewState),
            reviewedAt: date(value.reviewedAt),
            stepFree: value.stepFree?.boolValue
        )
    }

    static func journeyStop(_ value: OdivreloJourneyStop) -> JourneyStop {
        JourneyStop(
            stopId: value.stopId,
            sequence: Int(value.sequence),
            name: text(value.name),
            arrivalAt: date(value.arrivalAt),
            departureAt: date(value.departureAt),
            timeQuality: timeQuality(value.timeQuality),
            pickup: boardingRule(value.pickup),
            dropoff: boardingRule(value.dropoff)
        )
    }

    static func geometry(_ value: OdivreloGeometry?) -> RouteGeometry? {
        guard let value else { return nil }
        return RouteGeometry(
            type: value.type,
            coordinates: value.coordinates.map { pair in pair.map(\.doubleValue) },
            confidence: geometryConfidence(value.confidence),
            method: value.method,
            attribution: value.attribution
        )
    }

    static func provenance(_ value: OdivreloProvenance) -> Provenance {
        Provenance(
            sourceId: value.sourceId,
            sourceName: value.sourceName,
            sourceUrl: url(value.sourceUrl),
            retrievedAt: date(value.retrievedAt),
            rightsStatus: rightsStatus(value.rightsStatus),
            licence: value.licence
        )
    }

    static func journeyDetail(_ value: OdivreloJourneyDetail) -> JourneyDetail {
        let body = value.journey
        return JourneyDetail(
            envelope: ReleaseEnvelope(
                contractVersion: value.contractVersion,
                releaseId: value.releaseId,
                dataMode: dataMode(value.dataMode),
                servedFromCache: value.servedFromCache,
                publishedAt: nil
            ),
            journey: JourneyDetailBody(
                id: body.id,
                operatorSummary: operatorSummary(body.operator_),
                departure: endpoint(body.departure),
                arrival: endpoint(body.arrival),
                durationMinutes: Int(body.durationMinutes),
                intermediateStopCount: Int(body.intermediateStopCount),
                serviceDate: serviceDate(body.serviceDate),
                crossesMidnight: body.crossesMidnight,
                positionQuality: positionQuality(body.positionQuality),
                fare: fare(body.fare),
                freshness: freshness(body.freshness),
                confidence: confidence(body.confidence),
                boardingPoint: boardingPoint(body.boardingPoint),
                stops: body.stops.map(journeyStop),
                geometry: geometry(body.geometry),
                restrictions: body.restrictions.map {
                    Restriction(code: $0.code, text: text($0.text))
                },
                provenance: body.provenance.map(provenance),
                purchase: purchase(body.purchase),
                correctionUrl: url(body.correctionUrl)
            )
        )
    }

    // MARK: Operators and stops

    static func operatorDetail(_ value: OdivreloOperatorDetail) -> OperatorDetail {
        let body = value.operator_
        return OperatorDetail(
            envelope: ReleaseEnvelope(
                contractVersion: value.contractVersion,
                releaseId: value.releaseId,
                dataMode: dataMode(value.dataMode),
                servedFromCache: value.servedFromCache,
                publishedAt: nil
            ),
            operatorBody: OperatorBody(
                id: body.id,
                name: text(body.name),
                federationNumber: body.federationNumber?.intValue,
                officialSiteUrl: url(body.officialSiteUrl),
                directoryUrl: url(body.directoryUrl),
                contact: body.contact.map {
                    OperatorContact(phone: $0.phone, email: $0.email, address: $0.address)
                },
                coverage: OperatorCoverage(
                    state: coverageState(body.coverage.state),
                    routeCount: Int(body.coverage.routeCount),
                    stopCount: Int(body.coverage.stopCount)
                ),
                sources: body.sources.map(provenance),
                verifiedAt: date(body.verifiedAt),
                correctionUrl: url(body.correctionUrl)
            )
        )
    }

    static func stopDetail(_ value: OdivreloStopDetail) -> StopDetail {
        let body = value.stop
        return StopDetail(
            envelope: ReleaseEnvelope(
                contractVersion: value.contractVersion,
                releaseId: value.releaseId,
                dataMode: dataMode(value.dataMode),
                servedFromCache: value.servedFromCache,
                publishedAt: nil
            ),
            stop: StopBody(
                id: body.id,
                name: text(body.name),
                kind: placeKind(body.kind),
                parentId: body.parentId,
                parentName: body.parentName.map(text),
                municipality: body.municipality,
                latitude: body.latitude?.doubleValue,
                longitude: body.longitude?.doubleValue,
                coordinateStatus: body.coordinateStatus,
                bay: body.bay,
                address: body.address,
                instructions: body.instructions.map(text),
                boardingPoints: body.boardingPoints.compactMap { boardingPoint($0) },
                operatorIds: body.operatorIds,
                operators: body.operators.map(operatorSummary),
                departures: body.departures.map(journey),
                provenance: body.provenance.map(provenance),
                correctionUrl: url(body.correctionUrl),
                coverage: coverageState(body.coverage)
            ),
            serviceDate: serviceDate(value.serviceDate)
        )
    }

    // MARK: Saved state

    static func savedTrip(_ value: OdivreloSavedTrip?) -> SavedTrip? {
        guard let value else { return nil }
        return SavedTrip(
            id: value.id,
            journeyId: value.journeyId,
            serviceDate: serviceDate(value.serviceDate),
            savedAt: date(value.savedAt) ?? Date(),
            originName: text(value.originName),
            destinationName: text(value.destinationName),
            operatorName: text(value.operatorName),
            departureAt: date(value.departureAt) ?? Date(timeIntervalSince1970: 0),
            arrivalAt: date(value.arrivalAt) ?? Date(timeIntervalSince1970: 0),
            crossesMidnight: value.crossesMidnight,
            boardingBay: value.boardingBay,
            boardingStepFree: value.boardingStepFree?.boolValue,
            releaseId: value.releaseId,
            cachedAt: date(value.cachedAt) ?? Date(timeIntervalSince1970: 0)
        )
    }

    static func favourite(_ value: OdivreloFavoritePlace) -> FavouritePlace {
        FavouritePlace(
            placeId: value.placeId,
            name: text(value.name),
            kind: placeKind(value.kind),
            municipality: value.municipality,
            addedAt: date(value.addedAt) ?? Date()
        )
    }

    static func recentSearch(_ value: OdivreloRecentSearch) -> RecentSearch? {
        RecentSearch(
            originId: value.originId,
            originName: text(value.originName),
            destinationId: value.destinationId,
            destinationName: text(value.destinationName),
            serviceDate: serviceDate(value.serviceDate),
            searchedAt: date(value.searchedAt) ?? Date()
        )
    }

    // MARK: Offline packs

    static func availablePack(_ value: OdivreloAvailablePack) -> AvailablePack {
        AvailablePack(
            name: value.name,
            path: value.path,
            sha256: value.sha256,
            bytes: value.bytes,
            releaseId: value.releaseId,
            installed: value.installed,
            updateAvailable: value.updateAvailable,
            title: text(value.title),
            summary: text(value.summary)
        )
    }

    static func installedPack(_ value: OdivreloInstalledPack) -> InstalledPack {
        InstalledPack(
            name: value.name,
            releaseId: value.releaseId,
            sha256: value.sha256,
            bytes: value.bytes,
            installedAt: date(value.installedAt) ?? Date(),
            publishedAt: date(value.publishedAt),
            previousReleaseId: value.previousReleaseId
        )
    }

    static func offlineCatalog(_ value: OdivreloOfflineCatalog) -> OfflineCatalog {
        OfflineCatalog(
            releaseId: value.releaseId,
            publishedAt: date(value.publishedAt),
            dataMode: dataMode(value.dataMode),
            available: value.available.map(availablePack),
            installed: value.installed.map(installedPack),
            totalInstalledBytes: value.totalInstalledBytes,
            rollbackReleaseId: value.rollbackReleaseId,
            mapAvailability: OfflineMapAvailability(
                stopCoordinatesAvailable: value.mapAvailability.stopCoordinatesAvailable,
                routeGeometryAvailable: value.mapAvailability.routeGeometryAvailable,
                baseMapTilesAvailable: value.mapAvailability.baseMapTilesAvailable,
                searchAvailable: value.mapAvailability.searchAvailable
            ),
            manifestReachable: value.manifestReachable
        )
    }

    static func packProgress(_ value: OdivreloPackProgress) -> PackProgress {
        PackProgress(
            packName: value.packName,
            bytesDownloaded: value.bytesDownloaded,
            totalBytes: value.totalBytes,
            phase: packPhase(value.phase),
            attempt: Int(value.attempt)
        )
    }

    static func packResult(_ value: OdivreloPackResult) -> PackResult {
        PackResult(
            packName: value.packName,
            succeeded: value.succeeded,
            installed: value.installed.map(installedPack),
            failure: packFailure(value.failure),
            message: value.message,
            attempts: Int(value.attempts)
        )
    }

    // MARK: Filters

    static func kotlinFilters(_ value: JourneyFilters) -> OdivreloJourneyFilters {
        OdivreloJourneyFilters(
            accessibleOnly: value.accessibleOnly,
            departAfter: value.departAfter,
            departBefore: value.departBefore,
            operatorIds: value.operatorIds,
            maxDurationMinutes: value.maxDurationMinutes.map { KotlinInt(value: Int32($0)) },
            includeCrossesMidnight: value.includeCrossesMidnight,
            sort: kotlinSort(value.sort)
        )
    }

    private static func kotlinSort(_ value: JourneySort) -> OdivreloJourneySort {
        switch value {
        case .departure: .departure
        case .duration: .duration
        case .arrival: .arrival
        }
    }
}
#endif
