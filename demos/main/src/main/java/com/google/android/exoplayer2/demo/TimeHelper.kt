package com.google.android.exoplayer2.demo

import android.os.Build
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

class TimeHelper {

    companion object {
        const val TEN_SECONDS = 10
        const val THIRTY_SECONDS = 30
        const val SIXTY_SECONDS = 60
        const val THREE_MINS_IN_SECONDS = 3 * SIXTY_SECONDS
        const val TEN_MILLI_SECONDS = TEN_SECONDS * 1000
        const val SECONDS_TO_MILLISECONDS = 1000
        const val SECONDS_TO_MICROSECONDS = 1000_000
        const val MILLISECONDS_TO_MICROSECONDS = 1000
        const val MINUTE_TO_MILLISECONDS = SIXTY_SECONDS * SECONDS_TO_MILLISECONDS
        const val MINUTE_TO_MICROSECONDS = SIXTY_SECONDS * SECONDS_TO_MILLISECONDS * MILLISECONDS_TO_MICROSECONDS
        const val TWO_MINUTE_TO_MILLISECONDS = 2 * MINUTE_TO_MILLISECONDS
        const val TEN_MINS_IN_MILLISECONDS = 10 * MINUTE_TO_MILLISECONDS
        const val FIFTEEN_MINS_IN_MILLISECONDS = 15 * MINUTE_TO_MILLISECONDS
        const val ONE_HOUR_IN_MILLISECONDS = 60 * MINUTE_TO_MILLISECONDS
        const val THREE_HOURS_IN_MILLISECONDS = 3 * ONE_HOUR_IN_MILLISECONDS
        const val SIX_HOURS_IN_MILLISECONDS = 6 * ONE_HOUR_IN_MILLISECONDS
        const val ONE_DAY_IN_MILLISECONDS = 24 * ONE_HOUR_IN_MILLISECONDS
        const val ONE_MINUTE_IN_SECONDS = 60
        const val ONE_HOUR_IN_SECONDS = 60 * 60

        const val DEFAULT_CACHE_DURATION: Long = (1000 * 60 * 60 * 3).toLong() // 3 hours

        private const val UTC_ID = "UTC"

        private const val DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

        /**
         * Converts seconds to minutes. Rounds down to the nearest second.
         * Accepts negative seconds and 0.
         *
         * @param seconds The seconds to convert
         * @return The rounded down minutes equivalent
         */
        @JvmStatic
        fun secondsToMinutes(seconds: Int): Int {
            return Math.round((seconds / SIXTY_SECONDS).toFloat())
        }

        @JvmStatic
        fun secondToMillisecs(second: Long): Long {
            return second * SECONDS_TO_MILLISECONDS
        }

        fun milliToSecond(milliseconds: Long) = milliseconds / SECONDS_TO_MILLISECONDS

        fun milliToMinute(milliseconds: Long) = milliseconds / MINUTE_TO_MILLISECONDS

        fun milliToMicro(milliseconds: Long) = milliseconds * MILLISECONDS_TO_MICROSECONDS

        fun microToMinute(microSeconds: Long) = microSeconds / MINUTE_TO_MICROSECONDS

        fun microToSecond(microSeconds: Long) = microSeconds / SECONDS_TO_MICROSECONDS

        @JvmStatic
        fun nonZeroValidDurationInMilliSecond(validDurationInSecond: Long?): Long {
            return if (validDurationInSecond != null && validDurationInSecond != 0L) {
                secondToMillisecs(validDurationInSecond)
            } else DEFAULT_CACHE_DURATION
        }

        /**
         * Get a diff between two dates
         * @param oldDate the oldest date
         * @param newDate the newest date
         * @param timeUnit the unit in which you want the diff
         * @return the diff value, in the provided unit
         */
        @JvmStatic
        fun getDateDiff(oldDate: Date, newDate: Date, timeUnit: TimeUnit): Long {
            val diffInMillies = newDate.time - oldDate.time
            return timeUnit.convert(diffInMillies, TimeUnit.MILLISECONDS)
        }

        @JvmStatic
        fun nowInSec() = (System.currentTimeMillis() / 1000L).toInt()

        /**
         * Convert a millisecond duration to a string format
         *
         * @param durationMs A duration to convert to a string form
         * @return A string of the form Hours:Minutes:Seconds or Minutes:Seconds.
         */
        @JvmStatic
        fun getDurationBreakdown(durationMs: Long): String {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % SIXTY_SECONDS
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % SIXTY_SECONDS
            val hours = TimeUnit.MILLISECONDS.toHours(durationMs)

            return if (hours != 0L) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

        /**
         * Convert an epoch milliseconds to a UTC string format
         *
         * @param milli A milliseconds to convert to a UTC string form
         * @return A string of the form, like 2023-01-13 00:54:50.000037.
         */
        @JvmStatic
        fun utcTime(milli: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS")
            sdf.timeZone = TimeZone.getTimeZone(UTC_ID)
            return sdf.format(Date(milli))
        }

        @JvmStatic
        fun utcTimeNow() = utcTime(System.currentTimeMillis())

        @JvmStatic
        fun iso8601ToUtcEpochMS(strTime: String): Long {
            val format = SimpleDateFormat(DATETIME_FORMAT, Locale.US)
            format.timeZone = TimeZone.getTimeZone(UTC_ID)
            try {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    OffsetDateTime.parse(strTime).atZoneSameInstant(format.timeZone.toZoneId()).toInstant()
                        .toEpochMilli()
                } else {
                    format.parse(strTime).time
                }
            } catch (exception: Exception) {
                return 0
            }
        }

        /**
         * Check if a milliseconds is at hour sharp with the given tolerance
         *
         * @param milliseconds A milliseconds to check
         * @param tolerance tolerance in seconds
         *
         * @return true if the @param milliseconds is on a sharp hour with the given tolerance.
         */
        fun atHourSharp(milliseconds: Long, toleranceInSecond: Int): Boolean {
            val timeInSecond = milliseconds / SECONDS_TO_MILLISECONDS
            val secondsToHour = timeInSecond % ONE_HOUR_IN_SECONDS
            return secondsToHour <= toleranceInSecond || ONE_HOUR_IN_SECONDS - secondsToHour <= toleranceInSecond
        }
    }
}
