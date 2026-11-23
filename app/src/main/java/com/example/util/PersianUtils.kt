package com.example.util

import java.util.Calendar
import java.util.Locale

object PersianUtils {
    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun toPersianDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(persianDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun toEnglishDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun Int.toPersianNumber(): String = toPersianDigits(this.toString())

    fun Long.toPersianNumber(): String = toPersianDigits(this.toString())

    fun parseNonNegativeInt(input: String): Int {
        val cleaned = toEnglishDigits(input).filter { it.isDigit() }
        return cleaned.toIntOrNull()?.coerceAtLeast(0) ?: 0
    }

    /**
     * Accurate Gregorian to Jalali (Solar Hijri / Shamsi) date conversion.
     * Returns triple of (jy, jm, jd).
     */
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return Triple(jy, jm, jd)
    }

    fun formatJalaliDateTime(timestampMillis: Long, persianDigitsOutput: Boolean = true): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val (jy, jm, jd) = gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val raw = String.format(Locale.US, "%04d/%02d/%02d - %02d:%02d", jy, jm, jd, hour, minute)
        return if (persianDigitsOutput) toPersianDigits(raw) else raw
    }

    fun formatJalaliFileStamp(timestampMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val (jy, jm, jd) = gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)
        val ms = cal.get(Calendar.MILLISECOND)
        return String.format(Locale.US, "%04d%02d%02d_%02d%02d%02d_%03d", jy, jm, jd, hour, minute, second, ms)
    }

    /**
     * Exact inverse conversion: Jalali (jy, jm, jd) to Gregorian (gy, gm, gd).
     */
    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val jy1 = jy + 1595
        var days = -355668 + (365 * jy1) + ((jy1 / 33) * 8) + (((jy1 % 33) + 3) / 4) + jd +
            if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            gy += 100 * (--days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val isLeap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)
        val salA = intArrayOf(0, 31, if (isLeap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 1
        while (gm <= 12 && gd > salA[gm]) {
            gd -= salA[gm]
            gm++
        }
        return Triple(gy, gm, gd)
    }

    fun formatJalaliDateOnly(timestampMillis: Long, persianDigitsOutput: Boolean = true): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val (jy, jm, jd) = gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
        val raw = String.format(Locale.US, "%04d/%02d/%02d", jy, jm, jd)
        return if (persianDigitsOutput) toPersianDigits(raw) else raw
    }

    fun splitJalaliDateTime(jalaliDateTime: String): Pair<String, String> {
        val parts = jalaliDateTime.split("-").map { it.trim() }
        val datePart = parts.getOrNull(0).orEmpty()
        val timePart = parts.getOrNull(1).orEmpty()
        return datePart to timePart
    }

    /**
     * Parses Jalali date ("1405/09/01" or Persian digits) and time ("14:25") into epoch millis + normalized Jalali display string.
     * Returns null if invalid.
     */
    fun parseJalaliDateTime(dateInput: String, timeInput: String): Pair<Long, String>? {
        val dateParts = toEnglishDigits(dateInput.trim()).split("/", "-", ".").filter { it.isNotBlank() }
        if (dateParts.size != 3) return null
        val jy = dateParts[0].toIntOrNull() ?: return null
        val jm = dateParts[1].toIntOrNull() ?: return null
        val jd = dateParts[2].toIntOrNull() ?: return null
        if (jy !in 1300..1550 || jm !in 1..12) return null
        val maxDay = when {
            jm <= 6 -> 31
            jm <= 11 -> 30
            else -> 30
        }
        if (jd !in 1..maxDay) return null

        val timeClean = toEnglishDigits(timeInput.trim())
        val (hour, minute) = if (timeClean.isEmpty()) {
            0 to 0
        } else {
            val timeParts = timeClean.split(":").filter { it.isNotBlank() }
            if (timeParts.size != 2) return null
            val h = timeParts[0].toIntOrNull() ?: return null
            val m = timeParts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            h to m
        }

        val (gy, gm, gd) = jalaliToGregorian(jy, jm, jd)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, gy)
            set(Calendar.MONTH, gm - 1)
            set(Calendar.DAY_OF_MONTH, gd)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val normalized = toPersianDigits(
            String.format(Locale.US, "%04d/%02d/%02d - %02d:%02d", jy, jm, jd, hour, minute),
        )
        return cal.timeInMillis to normalized
    }

    fun startOfDaysAgoMillis(daysAgo: Int): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    const val ROOT_FOLDER_NAME = "تیکت_انبار"
    const val PALLET_TICKET_SUBFOLDER = "تیکت_پالت"
    const val CARTONS_PARENT_SUBFOLDER = "کارتن‌ها"

    fun sanitizeFolderSegment(name: String): String {
        val sanitized = name
            .trim()
            .replace(Regex("[/\\\\:*?\"<>|\\r\\n\\t]+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ')
        return sanitized.ifEmpty { "بدون_نام" }
    }

    fun buildWarehouseFolderPath(warehouseName: String): String {
        return "$ROOT_FOLDER_NAME/${sanitizeFolderSegment(warehouseName)}"
    }

    fun buildRowFolderPath(warehouseName: String, rowName: String): String {
        return "${buildWarehouseFolderPath(warehouseName)}/${sanitizeFolderSegment(rowName)}"
    }

    fun buildPalletRootFolderPath(warehouseName: String, rowName: String, palletName: String): String {
        return "${buildRowFolderPath(warehouseName, rowName)}/${sanitizeFolderSegment(palletName)}"
    }

    fun buildTargetTicketFolderPath(
        warehouseName: String,
        rowName: String,
        palletName: String,
        cartonName: String? = null,
    ): String {
        val palletRoot = buildPalletRootFolderPath(warehouseName, rowName, palletName)
        return if (cartonName.isNullOrBlank()) {
            "$palletRoot/$PALLET_TICKET_SUBFOLDER"
        } else {
            "$palletRoot/$CARTONS_PARENT_SUBFOLDER/${sanitizeFolderSegment(cartonName)}"
        }
    }
}
