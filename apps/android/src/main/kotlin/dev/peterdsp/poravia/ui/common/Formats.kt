package dev.peterdsp.poravia.ui.common

import android.content.Context
import dev.peterdsp.poravia.R
import dev.peterdsp.poravia.core.model.Freshness
import dev.peterdsp.poravia.core.model.FreshnessState
import dev.peterdsp.poravia.core.time.ServiceTime
import dev.peterdsp.poravia.features.format.ByteUnit
import dev.peterdsp.poravia.features.format.Display
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every visible time in Poravia is Athens local time.
 *
 * The device's own time zone is deliberately not used. A traveller in Tirana
 * looking at a coach from Kithra needs the time printed on the operator's
 * timetable, not the time on their phone, and the two differ. The core resolves
 * the instant; this file only renders it.
 */
object Formats {

    fun time(context: Context, isoInstant: String): String {
        val instant = ServiceTime.parseInstantOrNull(isoInstant)
            ?: return context.getString(R.string.quality_unknown)
        val local = ServiceTime.localTimeOf(instant)
        return pad(local.hour) + ":" + pad(local.minute)
    }

    /** A weekday and date a person recognises, in their own language. */
    fun serviceDate(serviceDate: String, locale: Locale): String {
        val date = ServiceTime.parseServiceDateOrNull(serviceDate) ?: return serviceDate
        val java = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
        return java.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    }

    fun serviceDateLong(serviceDate: String, locale: Locale): String {
        val date = ServiceTime.parseServiceDateOrNull(serviceDate) ?: return serviceDate
        val java = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
        return java.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale))
    }

    /** A timestamp shown as a date and time, for "retrieved at" and similar. */
    fun timestamp(isoInstant: String?, locale: Locale): String? {
        val instant = ServiceTime.parseInstantOrNull(isoInstant) ?: return null
        val local = ServiceTime.localDateTimeOf(instant)
        val java = java.time.LocalDateTime.of(
            local.year,
            local.monthNumber,
            local.dayOfMonth,
            local.hour,
            local.minute,
        )
        return java.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale))
    }

    /**
     * How a duration splits is decided once, in the shared layer, so the three
     * clients cannot disagree about it. Only the wording is local.
     */
    fun duration(context: Context, minutes: Int): String {
        val parts = Display.duration(minutes)
        return if (parts.hasHours) {
            context.getString(R.string.duration_hours_minutes, parts.hours, parts.minutes)
        } else {
            context.getString(R.string.duration_minutes, parts.minutes)
        }
    }

    fun durationSpoken(context: Context, minutes: Int): String {
        val parts = Display.duration(minutes)
        return if (parts.hasHours) {
            context.getString(R.string.duration_a11y_hours_minutes, parts.hours, parts.minutes)
        } else {
            context.getString(R.string.duration_a11y_minutes, parts.minutes)
        }
    }

    fun freshnessLabel(context: Context, freshness: Freshness): String =
        context.getString(
            when (freshness.state) {
                FreshnessState.FRESH -> R.string.freshness_fresh
                FreshnessState.AGING -> R.string.freshness_aging
                FreshnessState.STALE -> R.string.freshness_stale
            },
        )

    /**
     * Hours or days is a judgement the shared layer makes, because "62 hours" is
     * a number a person has to do arithmetic on and "2 days" is not.
     */
    fun freshnessAge(context: Context, freshness: Freshness): String {
        val display = Display.freshness(freshness)
        return when {
            display.isRecent -> context.getString(R.string.freshness_checked_recently)
            display.preferDays ->
                context.getString(R.string.freshness_checked_days, display.ageDays)

            else -> context.getString(R.string.freshness_checked_hours, display.ageHours)
        }
    }

    fun bytes(bytes: Long): String {
        val size = Display.bytes(bytes)
        val unit = when (size.unit) {
            ByteUnit.BYTES -> "B"
            ByteUnit.KILOBYTES -> "kB"
            ByteUnit.MEGABYTES -> "MB"
            ByteUnit.GIGABYTES -> "GB"
        }
        return String.format(Locale.ROOT, "%." + size.decimals + "f %s", size.value, unit)
    }

    fun money(amount: Double, currency: String): String =
        String.format(Locale.ROOT, "%.2f", amount) + " " +
            (if (currency.equals("EUR", ignoreCase = true)) "€" else currency)

    private fun pad(value: Int): String = if (value < 10) "0" + value else value.toString()
}
