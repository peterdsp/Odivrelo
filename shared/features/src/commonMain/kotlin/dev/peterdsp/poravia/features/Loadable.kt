package dev.peterdsp.poravia.features

import dev.peterdsp.poravia.core.model.CoverageState
import dev.peterdsp.poravia.core.model.ErrorCode
import dev.peterdsp.poravia.core.model.UnavailableReason
import kotlin.native.ObjCName

/**
 * Every state a screen can actually be in.
 *
 * There is no implicit "we have nothing, show a spinner for ever" case: an
 * absent result is either [Empty] with a reason a person can act on, or
 * [Failed] with whether retrying is worth their time.
 */
@ObjCName("PoraviaLoadable")
sealed interface Loadable<out T> {

    /** Nothing has been asked for yet. */
    @ObjCName("PoraviaLoadableIdle")
    data object Idle : Loadable<Nothing>

    /** A request is in flight. [previous] lets a list stay on screen while it refreshes. */
    @ObjCName("PoraviaLoadableLoading")
    data class Loading<T>(val previous: T? = null) : Loadable<T>

    @ObjCName("PoraviaLoadableReady")
    data class Ready<T>(val value: T) : Loadable<T>

    /** A successful request that legitimately found nothing. */
    @ObjCName("PoraviaLoadableEmpty")
    data class Empty(val reason: EmptyReason, val coverage: CoverageState? = null) :
        Loadable<Nothing>

    @ObjCName("PoraviaLoadableFailed")
    data class Failed(
        val reason: FailureReason,
        val code: ErrorCode? = null,
        val message: String? = null,
    ) : Loadable<Nothing> {
        val isRetryable: Boolean get() = reason.isRetryable
    }

    val valueOrNull: T?
        get() = when (this) {
            is Ready -> value
            is Loading -> previous
            else -> null
        }

    val isLoading: Boolean get() = this is Loading
}

/** Why a successful request produced nothing to show. */
@ObjCName("PoraviaEmptyReason")
enum class EmptyReason {
    /** The traveller has not typed or chosen enough yet. */
    NEEDS_INPUT,
    NO_MATCHING_PLACE,
    /**
     * A pack for the date was read and it contains no matching journey. This is a
     * statement about service.
     */
    NO_SERVICE_ON_DATE,

    /**
     * No pack for the date is installed and none could be fetched. This is a
     * statement about coverage, not about service.
     *
     * A release only materialises journeys packs for the dates its data names:
     * date-specific service dates, calendar template dates, and dates an added
     * exception names. A weekday-recurring service on some other date is resolved
     * by the service on request. Saying "no service runs that day" here would
     * invent a certainty the data does not support.
     */
    NO_OFFLINE_DATA_FOR_DATE,

    OUTSIDE_COVERAGE,
    PARTIAL_COVERAGE,
    ORIGIN_EQUALS_DESTINATION,
    FILTERED_OUT,
    NOTHING_SAVED,
    NOTHING_INSTALLED,
    ;

    companion object {
        fun of(reason: UnavailableReason?): EmptyReason = when (reason) {
            UnavailableReason.NO_SERVICE_ON_DATE -> NO_SERVICE_ON_DATE
            UnavailableReason.OUTSIDE_COVERAGE -> OUTSIDE_COVERAGE
            UnavailableReason.ORIGIN_EQUALS_DESTINATION -> ORIGIN_EQUALS_DESTINATION
            null -> FILTERED_OUT
        }
    }
}

/**
 * Why a request failed, in terms a screen can turn into an honest sentence and
 * the right action.
 */
@ObjCName("PoraviaFailureReason")
enum class FailureReason {
    /** No release is installed yet, so there is nothing to read. */
    NO_DATA_INSTALLED,

    /** Installed packs disagree about which release they belong to. */
    RELEASE_MISMATCH,

    /** Installed data could not be read and needs reinstalling. */
    DATA_UNREADABLE,

    /** The device has no usable network. */
    OFFLINE,

    /** The origin answered with an error. */
    SERVER_ERROR,

    /** The request itself was wrong, for example a malformed date. */
    INVALID_REQUEST,

    /** The thing asked for does not exist in this release. */
    NOT_FOUND,

    /** Storage ran out. */
    STORAGE_FULL,

    /** A permission the person has to grant is missing. */
    PERMISSION_DENIED,

    /** A permission that was granted has since been taken away. */
    PERMISSION_REVOKED,

    /** The service date has already passed. */
    EXPIRED_SERVICE,

    /**
     * The installed release publishes no timetable for the requested date and no
     * service was reachable to resolve it. Retrying the same request will not
     * help; downloading that date, or connecting, will.
     */
    NO_OFFLINE_DATA_FOR_DATE,

    /** Something the product has no better name for. */
    UNKNOWN,
    ;

    val isRetryable: Boolean
        get() = when (this) {
            OFFLINE, SERVER_ERROR, DATA_UNREADABLE, UNKNOWN, STORAGE_FULL -> true
            NO_DATA_INSTALLED, RELEASE_MISMATCH, INVALID_REQUEST, NOT_FOUND,
            PERMISSION_DENIED, PERMISSION_REVOKED, EXPIRED_SERVICE,
            NO_OFFLINE_DATA_FOR_DATE,
            -> false
        }
}
