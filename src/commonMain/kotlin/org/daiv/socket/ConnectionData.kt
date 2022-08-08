package org.daiv.socket

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionData(val ip: String, val port: Int)
