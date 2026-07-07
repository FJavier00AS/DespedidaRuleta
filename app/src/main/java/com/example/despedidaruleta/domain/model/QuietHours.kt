package com.example.despedidaruleta.domain.model

import java.util.Calendar

fun isWithinQuietHours(hour: Int, quietStart: Int, quietEnd: Int): Boolean = when {
    quietStart == quietEnd -> false
    quietStart < quietEnd -> hour in quietStart until quietEnd
    else -> hour >= quietStart || hour < quietEnd
}

fun LocalUserSettings.isQuietHourNow(): Boolean =
    isWithinQuietHours(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), quietHoursStart, quietHoursEnd)
