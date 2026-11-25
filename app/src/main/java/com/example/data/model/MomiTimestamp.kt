package com.example.data.model

import java.util.Date

/**
 * Lightweight pure-Kotlin Timestamp replacing legacy cloud SDK Timestamp types.
 * Stores epoch seconds + sub-second nanoseconds while providing Date interop and ordering.
 */
data class Timestamp(
    val seconds: Long,
    val nanoseconds: Int = 0
) : Comparable<Timestamp> {

    constructor(date: Date) : this(
        seconds = date.time / 1000L,
        nanoseconds = ((date.time % 1000L).let { if (it < 0) it + 1000L else it } * 1_000_000L).toInt()
    )

    fun toDate(): Date = Date(seconds * 1000L + (nanoseconds / 1_000_000L))

    override fun compareTo(other: Timestamp): Int {
        val secCmp = seconds.compareTo(other.seconds)
        return if (secCmp != 0) secCmp else nanoseconds.compareTo(other.nanoseconds)
    }

    companion object {
        fun now(): Timestamp = Timestamp(Date())
    }
}
