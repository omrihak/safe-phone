package com.safephone.policy

enum class ModePreset {
    DEEP_WORK,
    WORK_HOURS,
    ON_CALL,
    SLEEP,
    CUSTOM,
    ;

    companion object {
        fun fromStorage(s: String): ModePreset = entries.find { it.name == s } ?: WORK_HOURS
    }
}
