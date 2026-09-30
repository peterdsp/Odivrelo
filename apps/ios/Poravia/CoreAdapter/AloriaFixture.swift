#if DEBUG
import Foundation

/// The invented demonstration region used by the 1.0.0 beta.
///
/// Aloria does not exist. No operator, terminal, road, fare or departure below
/// corresponds to a real service. Every payload it produces carries
/// `dataMode == .demo`, which the interface turns into a persistent,
/// non-dismissible notice in all three languages.
///
/// This is a fixture, not a second implementation: it returns values already
/// decided, in exactly the shapes the core returns, so previews, tests and the
/// simulator walkthrough can run with no server. It is compiled only in Debug.
public enum AloriaFixture {
    public static let releaseId = "4bf83a41cd6f9514"
    public static let previousReleaseId = "91ac02be7d114f80"
    public static let publishedAt = Date(timeIntervalSince1970: 1_790_000_000)

    public static func envelope(servedFromCache: Bool = false) -> ReleaseEnvelope {
        ReleaseEnvelope(
            contractVersion: Brand.contractVersion,
            releaseId: releaseId,
            dataMode: .demo,
            servedFromCache: servedFromCache,
            publishedAt: publishedAt
        )
    }

    static func text(_ el: String, _ en: String, _ sq: String) -> LocalisedText {
        LocalisedText(el: el, en: en, sq: sq)
    }

    public static func fold(_ value: String) -> String {
        value.folding(
            options: [.diacriticInsensitive, .caseInsensitive],
            locale: Locale(identifier: "el")
        )
    }

    // MARK: - Places

    public static let places: [Place] = [
        Place(
            id: "place.kentro-terminal", kind: .stopPlace,
            name: text("Κεντρικός Σταθμός Αλορίας", "Aloria Central Terminal", "Terminali Qendror i Alorisë"),
            parentId: nil, municipality: "Aloria", latitude: 38.1042, longitude: 23.7318,
            coordinateStatus: "valid", boardingPointCount: 2,
            operatorIds: ["op.aloria-coastal", "op.meranthi-regional"], coverage: .demo
        ),
        Place(
            id: "stop.kentro-bay-a1", kind: .stop,
            name: text("Κεντρικός Σταθμός, θέση A1", "Aloria Central, bay A1", "Qendra e Alorisë, vendi A1"),
            parentId: "place.kentro-terminal", municipality: "Aloria",
            latitude: 38.1044, longitude: 23.7321, coordinateStatus: "valid",
            boardingPointCount: 0, operatorIds: ["op.aloria-coastal"], coverage: .demo
        ),
        Place(
            id: "stop.kentro-bay-b3", kind: .stop,
            name: text("Κεντρικός Σταθμός, θέση B3", "Aloria Central, bay B3", "Qendra e Alorisë, vendi B3"),
            parentId: "place.kentro-terminal", municipality: "Aloria",
            latitude: 38.1039, longitude: 23.7314, coordinateStatus: "valid",
            boardingPointCount: 0, operatorIds: ["op.meranthi-regional"], coverage: .demo
        ),
        Place(
            id: "place.vathia-harbour", kind: .stopPlace,
            name: text("Λιμάνι Βάθειας", "Vathia Harbour", "Porti i Vathias"),
            parentId: nil, municipality: "Vathia", latitude: 37.8811, longitude: 23.5502,
            coordinateStatus: "valid", boardingPointCount: 1,
            operatorIds: ["op.aloria-coastal"], coverage: .demo
        ),
        Place(
            id: "place.meranthi", kind: .stopPlace,
            name: text("Μέρανθη", "Meranthi", "Meranthi"),
            parentId: nil, municipality: "Meranthi", latitude: 38.4127, longitude: 23.9014,
            coordinateStatus: "valid", boardingPointCount: 1,
            operatorIds: ["op.meranthi-regional"], coverage: .demo
        ),
        Place(
            id: "place.kalyx-bay", kind: .stopPlace,
            name: text("Κόλπος Κάλυκα", "Kalyx Bay", "Gjiri i Kaliksit"),
            parentId: nil, municipality: "Kalyx", latitude: 37.6604, longitude: 23.3288,
            coordinateStatus: "valid", boardingPointCount: 1,
            operatorIds: ["op.aloria-coastal"], coverage: .demo
        ),
        Place(
            id: "place.orthia-highlands", kind: .stopPlace,
            name: text("Όρθια Υψώματα", "Orthia Highlands", "Malësia e Orthias"),
            parentId: nil, municipality: "Orthia", latitude: 38.7710, longitude: 24.1121,
            coordinateStatus: "valid", boardingPointCount: 1,
            operatorIds: ["op.meranthi-regional"], coverage: .partial
        ),
        Place(
            id: "place.selvia-port", kind: .stopPlace,
            name: text("Λιμένας Σελβίας", "Selvia Port", "Porti i Selvias"),
            parentId: nil, municipality: "Selvia", latitude: 37.4092, longitude: 23.1044,
            coordinateStatus: "valid", boardingPointCount: 1,
            operatorIds: ["op.aloria-coastal"], coverage: .demo
        ),
    ]

    public static func place(id: String) -> Place? { places.first { $0.id == id } }

    // MARK: - Operators

    public static let coastalOperator = OperatorSummary(
        id: "op.aloria-coastal",
        name: text("Παράκτιες Γραμμές Αλορίας", "Aloria Coastal Lines", "Linjat Bregdetare të Alorisë"),
        logoAvailable: false
    )

    public static let regionalOperator = OperatorSummary(
        id: "op.meranthi-regional",
        name: text("Περιφερειακά Λεωφορεία Μέρανθης", "Meranthi Regional Coaches", "Autobusët Rajonalë të Meranthit"),
        logoAvailable: false
    )

    public static let operators = [coastalOperator, regionalOperator]

    // MARK: - Provenance

    public static let registrySource = Provenance(
        sourceId: "src.aloria-registry",
        sourceName: "Aloria Transport Registry (demonstration source)",
        sourceUrl: URL(string: "https://poravia.peterdsp.dev/demo/sources/aloria-registry"),
        retrievedAt: Date(timeIntervalSince1970: 1_789_900_000),
        rightsStatus: .allowed,
        licence: "CC BY 4.0 (demonstration)"
    )

    public static let timetableSource = Provenance(
        sourceId: "src.aloria-timetables",
        sourceName: "Aloria published timetables (demonstration source)",
        sourceUrl: URL(string: "https://poravia.peterdsp.dev/demo/sources/aloria-timetables"),
        retrievedAt: Date(timeIntervalSince1970: 1_789_960_000),
        rightsStatus: .allowed,
        licence: "CC BY 4.0 (demonstration)"
    )

    public static let boardingSource = Provenance(
        sourceId: "src.aloria-boarding-survey",
        sourceName: "Aloria boarding point survey (demonstration source)",
        sourceUrl: URL(string: "https://poravia.peterdsp.dev/demo/sources/aloria-boarding"),
        retrievedAt: Date(timeIntervalSince1970: 1_789_500_000),
        rightsStatus: .permissionPending,
        licence: "Permission requested, decision pending"
    )

    public static let allProvenance = [registrySource, timetableSource, boardingSource]

    // MARK: - Coverage

    /// The fixture states these in all three languages, exactly as a real
    /// release does, so a run in Greek or Albanian exercises the same resolution
    /// path the shipped data takes rather than falling back to English.
    static let notCoveredStatements = [
        LocalisedText(
            el: "Δεν αναπαρίσταται καμία πραγματική ελληνική υπεραστική γραμμή.",
            en: "No real Greek coach service is represented.",
            sq: "Nuk përfaqësohet asnjë linjë reale autobusash greke."
        ),
        LocalisedText(
            el: "Καμία πώληση εισιτηρίων οποιουδήποτε είδους.",
            en: "No ticket sales of any kind.",
            sq: "Asnjë shitje biletash e asnjë lloji."
        ),
        LocalisedText(
            el: "Καμία ζωντανή παρακολούθηση οχημάτων.",
            en: "No live vehicle tracking.",
            sq: "Asnjë gjurmim i drejtpërdrejtë i automjeteve."
        ),
    ]

    static let unreviewedEvening = LocalisedText(
        el: "Μη ελεγμένα βραδινά δρομολόγια.",
        en: "Unreviewed evening services.",
        sq: "Shërbime mbrëmjeje të pashqyrtuara."
    )

    public static func coverage(state: CoverageState) -> CoverageSummary {
        CoverageSummary(
            state: state,
            operatorCount: state == .notCovered ? 0 : 2,
            corridorCount: state == .notCovered ? 0 : 4,
            journeyCount: state == .notCovered ? 0 : 12,
            stopCount: state == .notCovered ? 0 : 5,
            note: state == .partial
                ? LocalisedText(
                    el: "Μερική κάλυψη: ορισμένα δρομολόγια αυτής της ημερομηνίας δεν έχουν ελεγχθεί.",
                    en: "Partial coverage: some services on this date have not been reviewed.",
                    sq: "Mbulim i pjesshëm: disa shërbime të kësaj date nuk janë shqyrtuar."
                )
                : LocalisedText(
                    el: "Δεδομένα επίδειξης για την επινοημένη περιοχή Aloria.",
                    en: "Demonstration data for the invented region of Aloria.",
                    sq: "Të dhëna demonstrimi për rajonin e shpikur Aloria."
                ),
            notCovered: state == .partial
                ? notCoveredStatements + [unreviewedEvening]
                : notCoveredStatements,
            absenceSemantics: [
                "noPackForDate": "noOfflineDataForDate",
                "noJourneys": "noServiceOnDate",
            ],
            operators: state == .notCovered ? [] : [
                OperatorCoverageSummary(
                    operatorId: "op.aloria-coastal",
                    name: LocalisedText(
                        el: "Ακτοπλοϊκή Aloria",
                        en: "Aloria Coastal Lines",
                        sq: "Linjat Bregdetare Aloria"
                    ),
                    journeyCount: 12,
                    routeCount: 4,
                    stopCount: 5,
                    state: state
                ),
            ],
            serviceDates: state == .notCovered
                ? nil
                : ServiceDateRange(
                    from: ServiceDate(iso: "2026-09-28"),
                    to: ServiceDate(iso: "2026-10-04")
                ),
            freshness: nil,
            releaseId: releaseId,
            publishedAt: publishedAt,
            dataMode: .demo
        )
    }

    public static func meta(coverageState: CoverageState) -> MetaSnapshot {
        MetaSnapshot(
            envelope: envelope(),
            product: ProductInfo(
                name: Brand.name,
                version: Brand.marketingVersion,
                commit: BuildInfo.commit,
                builtAt: publishedAt
            ),
            languages: Brand.languageTags,
            coverage: coverage(state: coverageState),
            offlineManifestUrl: URL(string: "\(Brand.websiteURLString)/v1/offline/manifest"),
            gtfsUrl: URL(string: "\(Brand.websiteURLString)/v1/gtfs"),
            attribution: [
                Attribution(
                    name: "Aloria Transport Registry (demonstration source)",
                    url: URL(string: "https://poravia.peterdsp.dev/demo/sources/aloria-registry"),
                    licence: "CC BY 4.0 (demonstration)"
                ),
                Attribution(
                    name: "Apple Maps",
                    url: URL(string: "https://www.apple.com/maps/"),
                    licence: "MapKit terms"
                ),
            ]
        )
    }

    public static var sourceList: SourceList {
        SourceList(
            envelope: envelope(),
            sources: allProvenance.map { entry in
                SourceRecord(
                    id: entry.sourceId,
                    name: entry.sourceName,
                    url: entry.sourceUrl,
                    rightsStatus: entry.rightsStatus,
                    licence: entry.licence,
                    retrievedAt: entry.retrievedAt,
                    note: "Demonstration source. Nothing here describes a real operator."
                )
            }
        )
    }

    // MARK: - Journeys

    static func instant(on date: ServiceDate, hour: Int, minute: Int, dayOffset: Int = 0) -> Date {
        var components = DateComponents()
        components.year = date.year
        components.month = date.month
        components.day = date.day
        components.hour = hour
        components.minute = minute
        components.timeZone = ServiceDate.zone
        let base = ServiceDate.calendar.date(from: components) ?? date.pickerInstant
        guard dayOffset != 0 else { return base }
        return ServiceDate.calendar.date(byAdding: .day, value: dayOffset, to: base) ?? base
    }

    struct Template {
        let id: String
        let operatorSummary: OperatorSummary
        let originId: String
        let destinationId: String
        let departHour: Int
        let departMinute: Int
        let arriveHour: Int
        let arriveMinute: Int
        let arrivesNextDay: Bool
        let intermediateStopCount: Int
        let fare: Fare?
        let purchase: PurchaseOption
        let confidence: Confidence
        let freshnessAgeHours: Int
        let arrivalQuality: TimeQuality
        let stepFree: Bool?
        let boardingReviewState: ReviewState
        let bay: String?
    }

    static let onlinePurchase = PurchaseOption(
        kind: .online,
        url: URL(string: "https://poravia.peterdsp.dev/demo/operators/aloria-coastal/booking"),
        phone: nil, address: nil, openingHours: nil,
        label: text("Συνέχεια στον μεταφορέα", "Continue to the operator", "Vazhdo te operatori"),
        disclaimer: text(
            "Ο μεταφορέας διαχειρίζεται πληρωμή, εισιτήρια, αλλαγές και επιστροφές.",
            "The operator handles payment, tickets, changes and refunds.",
            "Operatori menaxhon pagesën, biletat, ndryshimet dhe rimbursimet."
        )
    )

    static let officePurchase = PurchaseOption(
        kind: .ticketOffice, url: nil,
        phone: "+30 210 0000000",
        address: "Aloria Central Terminal, ticket hall, Aloria (demonstration address)",
        openingHours: "Monday to Friday 06:30 to 21:00, Saturday 07:00 to 15:00",
        label: text("Πώς αγοράζεται", "How to buy this ticket", "Si blihet kjo biletë"),
        disclaimer: text(
            "Δεν υπάρχει επιβεβαιωμένη ηλεκτρονική πώληση για αυτό το δρομολόγιο.",
            "There is no verified online sale for this service.",
            "Nuk ka shitje online të verifikuar për këtë shërbim."
        )
    )

    static let phonePurchase = PurchaseOption(
        kind: .phone, url: nil, phone: "+30 210 0000001", address: nil,
        openingHours: "Daily 08:00 to 20:00",
        label: text("Κλήση για κράτηση", "Call to reserve", "Telefono për rezervim"),
        disclaimer: text(
            "Η κράτηση γίνεται απευθείας με τον μεταφορέα.",
            "Reservation is made directly with the operator.",
            "Rezervimi bëhet drejtpërdrejt me operatorin."
        )
    )

    static let unknownPurchase = PurchaseOption(
        kind: .unavailable, url: nil, phone: nil, address: nil, openingHours: nil,
        label: text("Άγνωστη ηλεκτρονική έκδοση", "Electronic ticketing unknown", "Biletat elektronike të panjohura"),
        disclaimer: text(
            "Δεν έχει ελεγχθεί αν ο μεταφορέας πουλά ηλεκτρονικά.",
            "Whether this operator sells electronically has not been investigated.",
            "Nuk është hetuar nëse ky operator shet në mënyrë elektronike."
        )
    )

    static let templates: [Template] = [
        Template(
            id: "jny.acl-0700", operatorSummary: coastalOperator,
            originId: "place.kentro-terminal", destinationId: "place.vathia-harbour",
            departHour: 7, departMinute: 0, arriveHour: 9, arriveMinute: 40,
            arrivesNextDay: false, intermediateStopCount: 2,
            fare: Fare(amount: 12.50, currency: "EUR", isIndicative: true),
            purchase: onlinePurchase, confidence: .reviewed, freshnessAgeHours: 6,
            arrivalQuality: .scheduled, stepFree: true, boardingReviewState: .published, bay: "A1"
        ),
        Template(
            id: "jny.mrc-1215", operatorSummary: regionalOperator,
            originId: "place.kentro-terminal", destinationId: "place.meranthi",
            departHour: 12, departMinute: 15, arriveHour: 15, arriveMinute: 5,
            arrivesNextDay: false, intermediateStopCount: 3,
            fare: nil, purchase: officePurchase, confidence: .candidate, freshnessAgeHours: 74,
            arrivalQuality: .approximate, stepFree: nil, boardingReviewState: .candidate, bay: "B3"
        ),
        Template(
            id: "jny.acl-1830", operatorSummary: coastalOperator,
            originId: "place.kentro-terminal", destinationId: "place.kalyx-bay",
            departHour: 18, departMinute: 30, arriveHour: 21, arriveMinute: 15,
            arrivesNextDay: false, intermediateStopCount: 1,
            fare: Fare(amount: 14.00, currency: "EUR", isIndicative: true),
            purchase: onlinePurchase, confidence: .reviewed, freshnessAgeHours: 11,
            arrivalQuality: .scheduled, stepFree: false, boardingReviewState: .published, bay: "A2"
        ),
        Template(
            id: "jny.acl-2340", operatorSummary: coastalOperator,
            originId: "place.kentro-terminal", destinationId: "place.selvia-port",
            departHour: 23, departMinute: 40, arriveHour: 2, arriveMinute: 25,
            arrivesNextDay: true, intermediateStopCount: 2,
            fare: Fare(amount: 16.00, currency: "EUR", isIndicative: true),
            purchase: phonePurchase, confidence: .candidate, freshnessAgeHours: 240,
            arrivalQuality: .approximate, stepFree: nil, boardingReviewState: .stale, bay: nil
        ),
        Template(
            id: "jny.mrc-0530", operatorSummary: regionalOperator,
            originId: "place.kentro-terminal", destinationId: "place.orthia-highlands",
            departHour: 5, departMinute: 30, arriveHour: 7, arriveMinute: 55,
            arrivesNextDay: false, intermediateStopCount: 2,
            fare: nil, purchase: unknownPurchase, confidence: .reviewed, freshnessAgeHours: 20,
            arrivalQuality: .scheduled, stepFree: true, boardingReviewState: .published, bay: "B1"
        ),
    ]

    /// The same thresholds the core publishes on `ServiceTime`: fresh below a
    /// day, aging below a week, stale beyond it.
    public static func freshness(checkedAt: Date, now: Date) -> Freshness {
        let hours = Int(max(0, now.timeIntervalSince(checkedAt)) / 3600)
        return Freshness(checkedAt: checkedAt, ageHours: hours, state: state(forAgeHours: hours))
    }

    static func state(forAgeHours hours: Int) -> FreshnessState {
        hours < 24 ? .fresh : (hours < 168 ? .aging : .stale)
    }

    static func freshness(ageHours: Int, stale: Bool) -> Freshness {
        let hours = stale ? max(ageHours, 400) : ageHours
        return Freshness(
            checkedAt: Date().addingTimeInterval(-Double(hours) * 3600),
            ageHours: hours,
            state: state(forAgeHours: hours)
        )
    }

    static func boardingStopId(for journeyId: String) -> String {
        journeyId.hasPrefix("jny.acl") ? "stop.kentro-bay-a1" : "stop.kentro-bay-b3"
    }

    static func journey(from template: Template, on date: ServiceDate, stale: Bool) -> Journey {
        let departure = instant(on: date, hour: template.departHour, minute: template.departMinute)
        let arrival = instant(
            on: date, hour: template.arriveHour, minute: template.arriveMinute,
            dayOffset: template.arrivesNextDay ? 1 : 0
        )
        return Journey(
            id: template.id,
            operatorSummary: template.operatorSummary,
            departure: JourneyEndpoint(
                at: departure,
                stopId: boardingStopId(for: template.id),
                stopName: place(id: template.originId)?.name ?? text("", "", ""),
                quality: .scheduled
            ),
            arrival: JourneyEndpoint(
                at: arrival,
                stopId: template.destinationId,
                stopName: place(id: template.destinationId)?.name ?? text("", "", ""),
                quality: template.arrivalQuality
            ),
            durationMinutes: Int(arrival.timeIntervalSince(departure) / 60),
            intermediateStopCount: template.intermediateStopCount,
            // The service date stays the departure date even when the journey
            // arrives after midnight.
            serviceDate: date,
            crossesMidnight: template.arrivesNextDay,
            positionQuality: .scheduled,
            fare: template.fare,
            freshness: freshness(ageHours: template.freshnessAgeHours, stale: stale),
            confidence: template.confidence,
            purchase: template.purchase,
            accessibleBoardingPoint: template.stepFree
        )
    }

    static func groupId(for placeId: String) -> String {
        place(id: placeId)?.parentId ?? placeId
    }

    public static func journeys(
        originId: String,
        destinationId: String,
        serviceDate: ServiceDate,
        stale: Bool
    ) -> [Journey] {
        let origin = groupId(for: originId)
        let destination = groupId(for: destinationId)
        return templates
            .filter { groupId(for: $0.originId) == origin && groupId(for: $0.destinationId) == destination }
            .map { journey(from: $0, on: serviceDate, stale: stale) }
            .sorted { $0.departure.at < $1.departure.at }
    }

    public static func boardingPoint(for journeyId: String) -> BoardingPoint? {
        guard let template = templates.first(where: { $0.id == journeyId }),
              let stop = place(id: boardingStopId(for: journeyId)),
              let terminal = place(id: template.originId)
        else { return nil }
        return BoardingPoint(
            stopId: stop.id,
            name: stop.name,
            terminalName: terminal.name,
            bay: template.bay,
            latitude: stop.latitude,
            longitude: stop.longitude,
            instructions: text(
                "Από την κεντρική αίθουσα, έξοδος προς τις θέσεις επιβίβασης και στη συνέχεια αριστερά.",
                "From the main hall, take the exit towards the boarding bays and turn left.",
                "Nga salla kryesore, dilni drejt vendeve të hipjes dhe kthehuni majtas."
            ),
            reviewState: template.boardingReviewState,
            reviewedAt: template.boardingReviewState == .published
                ? Date(timeIntervalSince1970: 1_789_700_000) : nil,
            stepFree: template.stepFree
        )
    }

    static func restrictions(for template: Template) -> [Restriction] {
        var list: [Restriction] = []
        if template.arrivesNextDay {
            list.append(Restriction(code: "overnight", text: text(
                "Το δρομολόγιο διασχίζει τα μεσάνυχτα και φτάνει την επόμενη ημερολογιακή ημέρα.",
                "This service crosses midnight and arrives on the following calendar day.",
                "Ky shërbim kalon mesnatën dhe mbërrin ditën tjetër kalendarike."
            )))
        }
        if template.stepFree == nil {
            list.append(Restriction(code: "step_free_unreviewed", text: text(
                "Η πρόσβαση χωρίς σκαλοπάτια δεν έχει ελεγχθεί για αυτή τη θέση επιβίβασης.",
                "Step-free access has not been reviewed for this boarding point.",
                "Qasja pa shkallë nuk është shqyrtuar për këtë vend hipjeje."
            )))
        }
        list.append(Restriction(code: "no_live_tracking", text: text(
            "Αυτή η έκδοση περιέχει μόνο προγραμματισμένα δεδομένα. Δεν υπάρχει ζωντανή παρακολούθηση.",
            "This release carries scheduled data only. Live tracking is not available.",
            "Ky publikim përmban vetëm të dhëna të planifikuara. Gjurmimi i drejtpërdrejtë nuk ofrohet."
        )))
        return list
    }

    static func stops(for template: Template, on date: ServiceDate) -> [JourneyStop] {
        let departure = instant(on: date, hour: template.departHour, minute: template.departMinute)
        let arrival = instant(
            on: date, hour: template.arriveHour, minute: template.arriveMinute,
            dayOffset: template.arrivesNextDay ? 1 : 0
        )
        let total = arrival.timeIntervalSince(departure)
        let count = template.intermediateStopCount

        let names = [
            text("Διασταύρωση Πέτρινης Γέφυρας", "Stone Bridge junction", "Kryqëzimi i Urës së Gurit"),
            text("Παραλιακή στάση Νήριδας", "Nerida coastal halt", "Ndalesa bregdetare e Neridës"),
            text("Κόμβος Αλκυόνης", "Alkyone interchange", "Nyja e Alkiones"),
        ]

        var calls: [JourneyStop] = [
            JourneyStop(
                stopId: boardingStopId(for: template.id), sequence: 1,
                name: place(id: template.originId)?.name ?? text("", "", ""),
                arrivalAt: nil, departureAt: departure, timeQuality: .scheduled,
                pickup: .allowed, dropoff: .notAllowed
            ),
        ]
        for index in 0..<count {
            let at = departure.addingTimeInterval(total * Double(index + 1) / Double(count + 1))
            calls.append(JourneyStop(
                stopId: "stop.intermediate.\(template.id).\(index)",
                sequence: index + 2,
                name: names[index % names.count],
                arrivalAt: at, departureAt: at.addingTimeInterval(120),
                timeQuality: index == count - 1 ? .approximate : .scheduled,
                pickup: index == 0 ? .allowed : .onRequest,
                dropoff: index == count - 1 ? .coordinateWithOperator : .allowed
            ))
        }
        calls.append(JourneyStop(
            stopId: template.destinationId, sequence: count + 2,
            name: place(id: template.destinationId)?.name ?? text("", "", ""),
            arrivalAt: arrival, departureAt: nil, timeQuality: template.arrivalQuality,
            pickup: .notAllowed, dropoff: .allowed
        ))
        return calls
    }

    /// Straight segments between ordered stops. Never labelled exact.
    static func geometry(for template: Template, on date: ServiceDate) -> RouteGeometry? {
        let coordinates: [[Double]] = [
            [place(id: template.originId)?.longitude, place(id: template.originId)?.latitude],
            [23.6411, 38.0102],
            [23.5905, 37.9410],
            [place(id: template.destinationId)?.longitude, place(id: template.destinationId)?.latitude],
        ].compactMap { pair in
            guard let lon = pair[0], let lat = pair[1] else { return nil }
            return [lon, lat]
        }
        guard coordinates.count >= 2 else { return nil }
        return RouteGeometry(
            type: "LineString",
            coordinates: coordinates,
            confidence: .orderedStopsOnly,
            method: "ordered stop sequence, straight segments",
            attribution: "Aloria Transport Registry (demonstration source)"
        )
    }

    public static func journeyDetail(
        journeyId: String,
        serviceDate: ServiceDate,
        stale: Bool
    ) -> JourneyDetail? {
        guard let template = templates.first(where: { $0.id == journeyId }) else { return nil }
        let summary = journey(from: template, on: serviceDate, stale: stale)
        return JourneyDetail(
            envelope: envelope(),
            journey: JourneyDetailBody(
                id: summary.id,
                operatorSummary: summary.operatorSummary,
                departure: summary.departure,
                arrival: summary.arrival,
                durationMinutes: summary.durationMinutes,
                intermediateStopCount: summary.intermediateStopCount,
                serviceDate: summary.serviceDate,
                crossesMidnight: summary.crossesMidnight,
                positionQuality: summary.positionQuality,
                fare: summary.fare,
                freshness: summary.freshness,
                confidence: summary.confidence,
                boardingPoint: boardingPoint(for: journeyId),
                stops: stops(for: template, on: serviceDate),
                geometry: geometry(for: template, on: serviceDate),
                restrictions: restrictions(for: template),
                provenance: allProvenance,
                purchase: template.purchase,
                correctionUrl: URL(string: "https://poravia.peterdsp.dev/corrections/journey/\(journeyId)")
            )
        )
    }

    // MARK: - Operator and stop pages

    public static func operatorDetail(id: String) -> OperatorDetail? {
        guard let summary = operators.first(where: { $0.id == id }) else { return nil }
        let isCoastal = id == coastalOperator.id
        let slug = isCoastal ? "aloria-coastal" : "meranthi-regional"
        return OperatorDetail(
            envelope: envelope(),
            operatorBody: OperatorBody(
                id: summary.id,
                name: summary.name,
                federationNumber: isCoastal ? 12 : 27,
                officialSiteUrl: URL(string: "https://poravia.peterdsp.dev/demo/operators/\(slug)"),
                directoryUrl: URL(string: "https://poravia.peterdsp.dev/demo/directory"),
                contact: OperatorContact(
                    phone: isCoastal ? "+30 210 0000000" : "+30 210 0000002",
                    email: "demo@poravia.peterdsp.dev",
                    address: isCoastal
                        ? "Aloria Central Terminal, Aloria (demonstration address)"
                        : "Meranthi main square, Meranthi (demonstration address)"
                ),
                coverage: OperatorCoverage(
                    state: .demo,
                    routeCount: isCoastal ? 3 : 2,
                    stopCount: isCoastal ? 9 : 6
                ),
                sources: [registrySource, timetableSource],
                verifiedAt: Date(timeIntervalSince1970: 1_789_800_000),
                correctionUrl: URL(string: "https://poravia.peterdsp.dev/corrections/operator/\(summary.id)")
            )
        )
    }

    public static func stopDetail(id: String, serviceDate: ServiceDate) -> StopDetail? {
        guard let value = place(id: id) else { return nil }
        let boarding = places.filter { $0.parentId == value.id }.map { child in
            BoardingPoint(
                stopId: child.id, name: child.name, terminalName: value.name,
                bay: child.id.hasSuffix("a1") ? "A1" : (child.id.hasSuffix("b3") ? "B3" : nil),
                latitude: child.latitude, longitude: child.longitude, instructions: nil,
                reviewState: .published, reviewedAt: Date(timeIntervalSince1970: 1_789_700_000),
                stepFree: child.id.hasSuffix("a1") ? true : nil
            )
        }
        let departures = templates
            .filter { groupId(for: $0.originId) == (value.parentId ?? value.id) }
            .map { journey(from: $0, on: serviceDate, stale: false) }
            .sorted { $0.departure.at < $1.departure.at }

        return StopDetail(
            envelope: envelope(),
            stop: StopBody(
                id: value.id, name: value.name, kind: value.kind,
                parentId: value.parentId,
                parentName: value.parentId.flatMap { place(id: $0)?.name },
                municipality: value.municipality,
                latitude: value.latitude, longitude: value.longitude,
                coordinateStatus: value.coordinateStatus,
                bay: value.id.hasSuffix("a1") ? "A1" : (value.id.hasSuffix("b3") ? "B3" : nil),
                address: value.kind == .stopPlace
                    ? "\(value.name.en ?? value.id), Aloria (demonstration address)" : nil,
                instructions: value.kind == .stop ? text(
                    "Από την κεντρική αίθουσα προς τις θέσεις επιβίβασης.",
                    "From the main hall towards the boarding bays.",
                    "Nga salla kryesore drejt vendeve të hipjes."
                ) : nil,
                boardingPoints: boarding,
                operatorIds: value.operatorIds,
                operators: operators.filter { value.operatorIds.contains($0.id) },
                departures: departures,
                provenance: [registrySource, boardingSource],
                correctionUrl: URL(string: "https://poravia.peterdsp.dev/corrections/stop/\(value.id)"),
                coverage: value.coverage
            ),
            serviceDate: serviceDate
        )
    }

    // MARK: - Packs

    /// Pack names follow the canonical logical names the release generator
    /// emits. Nothing on this side reads a pack's contents.
    public static func availablePacks(installed: Set<String>) -> [AvailablePack] {
        [
            ("meta", 4_212, "Release identity", "Contract version, release id and coverage summary."),
            ("places", 1_148_562, "Places", "Every terminal and boarding point."),
            ("operators", 96_430, "Operators", "Every operator with contacts and sources."),
            ("stops", 812_004, "Stops", "Every stop page with its boarding points."),
            ("journeys-\(ServiceDate.fromPicker(Date()).iso)", 4_404_019, "Timetable for today",
             "Journey results and details for this service date."),
            ("sources", 22_118, "Sources", "Source registry with rights status and licence."),
        ].map { name, bytes, title, summary in
            AvailablePack(
                name: name,
                path: "packs/\(name)-\(String(sha(name).prefix(16))).json",
                sha256: sha(name),
                bytes: Int64(bytes),
                releaseId: releaseId,
                installed: installed.contains(name),
                updateAvailable: false,
                title: LocalisedText(el: title, en: title, sq: title),
                summary: LocalisedText(el: summary, en: summary, sq: summary)
            )
        }
    }

    /// A stable, obviously synthetic digest for the fixture. It is never used
    /// to verify anything: verification is the core's, over real bytes.
    static func sha(_ seed: String) -> String {
        var value = UInt64(5_381)
        for byte in seed.utf8 { value = value &* 33 &+ UInt64(byte) }
        var digest = ""
        for index in 0..<8 {
            digest += String(format: "%08x", UInt32(truncatingIfNeeded: value &* UInt64(index + 1)))
        }
        return String(digest.prefix(64))
    }

    public static let mapAvailability = OfflineMapAvailability(
        stopCoordinatesAvailable: true,
        routeGeometryAvailable: true,
        // Base map imagery is never part of a pack, and the interface says so.
        baseMapTilesAvailable: false,
        searchAvailable: true
    )
}
#endif
