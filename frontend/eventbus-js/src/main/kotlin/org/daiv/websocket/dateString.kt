package org.daiv.websocket

import kotlin.js.Date

internal actual fun dateString() = Date().toISOString()