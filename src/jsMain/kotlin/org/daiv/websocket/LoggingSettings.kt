package org.daiv.websocket

import mu.*
import kotlin.js.Date

fun date(message: Any?): String {
    val date = Date()
    return "${date.toDateString()} - $message"
}

data class LoggingFilterRule(val loggerName: String, val level: KotlinLoggingLevel) {
    fun isLoggingEnabled(level: KotlinLoggingLevel) = this.level.ordinal <= level.ordinal
}

class LoggingSettings {
    companion object {
        fun setLogging(
            loggerFilterList: List<LoggingFilterRule>,
            globalLevel: KotlinLoggingLevel,
            reformatLoggingName: Boolean = false
        ) {
            KotlinLoggingConfiguration.LOG_LEVEL = globalLevel
            KotlinLoggingConfiguration.APPENDER = object : Appender {
                override fun trace(message: Any?) = if (message != null) console.log(message) else {
                }

                override fun debug(message: Any?) = if (message != null) console.log(message) else {
                }

                override fun info(message: Any?) = if (message != null) console.info(message) else {
                }

                override fun warn(message: Any?) = if (message != null) console.warn(message) else {
                }

                override fun error(message: Any?) = if (message != null) console.error(message) else {
                }
            }

            KotlinLoggingConfiguration.FORMATTER = object : Formatter {
                private fun Throwable?.throwableToString(): String {
                    if (this == null) {
                        return ""
                    }
                    var msg = ""
                    var current = this
                    while (current != null && current.cause != current) {
                        msg += ", Caused by: '${current.message}'"
                        current = current.cause
                    }
                    return msg
                }

                override fun formatMessage(level: KotlinLoggingLevel, loggerName: String, msg: () -> Any?): Any? {
                    return formatMessage(level, loggerName, t = null, msg = msg)
                }

                override fun formatMessage(
                    level: KotlinLoggingLevel,
                    loggerName: String,
                    t: Throwable?,
                    msg: () -> Any?
                ): Any? {
                    return formatMessage(level, loggerName, marker = null, t = null, msg = msg)
                }

                override fun formatMessage(
                    level: KotlinLoggingLevel,
                    loggerName: String,
                    marker: Marker?,
                    msg: () -> Any?
                ): Any? {
                    return formatMessage(level, loggerName, marker = null, t = null, msg = msg)
                }

                private fun String.reformatLoggerName() = split(".").last()

                override fun formatMessage(
                    level: KotlinLoggingLevel,
                    loggerName: String,
                    marker: Marker?,
                    t: Throwable?,
                    msg: () -> Any?
                ): Any? {
                    if (loggerFilterList.any { it.loggerName == loggerName && it.isLoggingEnabled(level) } || loggerFilterList.none { it.loggerName == loggerName }) {
                        val message =
                            "${level.name}: [${if (reformatLoggingName) loggerName.reformatLoggerName() else loggerName}] ${marker?.getName()
                                ?: ""} ${msg()}${t.throwableToString()}"
                        return date(message)
                    }
                    return null
                }

            }
        }
    }
}
