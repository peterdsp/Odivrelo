package dev.peterdsp.odivrelo.core.model

import kotlin.native.ObjCName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The enumerations of public data contract version 1.
 *
 * Every constant carries the exact wire value from
 * `data/schemas/CONTRACT-v1.md`. An unrecognised wire value decodes to the
 * enumeration's `UNKNOWN`-shaped member rather than throwing, so a server that
 * adds a value inside contract 1 cannot make an installed client refuse to
 * start. A missing enumeration is never silently treated as a permissive one.
 */

@Serializable
@ObjCName("OdivreloRightsStatus")
enum class RightsStatus {
    @SerialName("allowed")
    ALLOWED,

    @SerialName("permission_pending")
    PERMISSION_PENDING,

    @SerialName("prohibited")
    PROHIBITED,

    @SerialName("unknown")
    UNKNOWN,
    ;

    /** True only when a source may be shown in full. */
    val isPublishable: Boolean get() = this == ALLOWED
}

@Serializable
@ObjCName("OdivreloReviewState")
enum class ReviewState {
    @SerialName("candidate")
    CANDIDATE,

    @SerialName("verified")
    VERIFIED,

    @SerialName("published")
    PUBLISHED,

    @SerialName("stale")
    STALE,

    @SerialName("withdrawn")
    WITHDRAWN,

    @SerialName("quarantined")
    QUARANTINED,
    ;

    val isShowable: Boolean get() = this == VERIFIED || this == PUBLISHED
}

@Serializable
@ObjCName("OdivreloTimeQuality")
enum class TimeQuality {
    @SerialName("scheduled")
    SCHEDULED,

    @SerialName("approximate")
    APPROXIMATE,

    @SerialName("unknown")
    UNKNOWN,
}

/**
 * How a position or time was derived. Release 1.0.0 publishes only
 * [SCHEDULED]; no client may animate a vehicle it cannot observe.
 */
@Serializable
@ObjCName("OdivreloPositionQuality")
enum class PositionQuality {
    @SerialName("scheduled")
    SCHEDULED,

    @SerialName("predicted")
    PREDICTED,

    @SerialName("estimated")
    ESTIMATED,

    @SerialName("live")
    LIVE,
    ;

    val isRealTime: Boolean get() = this == PREDICTED || this == ESTIMATED || this == LIVE
}

@Serializable
@ObjCName("OdivreloGeometryConfidence")
enum class GeometryConfidence {
    @SerialName("unverified")
    UNVERIFIED,

    @SerialName("ordered_stops_only")
    ORDERED_STOPS_ONLY,

    @SerialName("osm_candidate")
    OSM_CANDIDATE,

    @SerialName("reviewed")
    REVIEWED,

    @SerialName("rejected")
    REJECTED,
    ;

    /** A rejected geometry is never drawn, not even faintly. */
    val isDrawable: Boolean get() = this != REJECTED
}

@Serializable
@ObjCName("OdivreloPurchaseKind")
enum class PurchaseKind {
    @SerialName("online")
    ONLINE,

    @SerialName("ticket_office")
    TICKET_OFFICE,

    @SerialName("phone")
    PHONE,

    @SerialName("onboard")
    ONBOARD,

    @SerialName("unavailable")
    UNAVAILABLE,
}

@Serializable
@ObjCName("OdivreloCoverageState")
enum class CoverageState {
    @SerialName("covered")
    COVERED,

    @SerialName("partial")
    PARTIAL,

    @SerialName("not_covered")
    NOT_COVERED,

    @SerialName("demo")
    DEMO,
}

@Serializable
@ObjCName("OdivreloDataMode")
enum class DataMode {
    @SerialName("real")
    REAL,

    @SerialName("demo")
    DEMO,
    ;

    /**
     * True when every client must show a persistent, non-dismissible notice
     * saying the services are invented.
     */
    val requiresDemoNotice: Boolean get() = this == DEMO
}

@Serializable
@ObjCName("OdivreloFreshnessState")
enum class FreshnessState {
    @SerialName("fresh")
    FRESH,

    @SerialName("aging")
    AGING,

    @SerialName("stale")
    STALE,
}

@Serializable
@ObjCName("OdivreloConfidence")
enum class Confidence {
    @SerialName("reviewed")
    REVIEWED,

    @SerialName("candidate")
    CANDIDATE,
}

@Serializable
@ObjCName("OdivreloBoardingRule")
enum class BoardingRule {
    @SerialName("allowed")
    ALLOWED,

    @SerialName("not_allowed")
    NOT_ALLOWED,

    @SerialName("on_request")
    ON_REQUEST,

    @SerialName("coordinate_with_operator")
    COORDINATE_WITH_OPERATOR,
}

@Serializable
@ObjCName("OdivreloPlaceKind")
enum class PlaceKind {
    @SerialName("stop_place")
    STOP_PLACE,

    @SerialName("stop")
    STOP,
}

/**
 * Why a journey search produced no usable result. `null` on the wire means the
 * search succeeded.
 */
@Serializable
@ObjCName("OdivreloUnavailableReason")
enum class UnavailableReason {
    @SerialName("no_service_on_date")
    NO_SERVICE_ON_DATE,

    @SerialName("outside_coverage")
    OUTSIDE_COVERAGE,

    @SerialName("origin_equals_destination")
    ORIGIN_EQUALS_DESTINATION,
}

@Serializable
@ObjCName("OdivreloErrorCode")
enum class ErrorCode {
    @SerialName("not_found")
    NOT_FOUND,

    @SerialName("invalid_request")
    INVALID_REQUEST,

    @SerialName("unavailable")
    UNAVAILABLE,

    @SerialName("release_mismatch")
    RELEASE_MISMATCH,

    @SerialName("unauthorized")
    UNAUTHORIZED,
}

@Serializable
@ObjCName("OdivreloSegmentRole")
enum class SegmentRole {
    @SerialName("board")
    BOARD,

    @SerialName("onSegment")
    ON_SEGMENT,

    @SerialName("alight")
    ALIGHT,

    @SerialName("beforeBoard")
    BEFORE_BOARD,

    @SerialName("afterAlight")
    AFTER_ALIGHT,
}
