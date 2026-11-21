package com.openscreens.app.data

internal sealed interface NearbyDevice {
    val id: String
    val name: String
    val detail: String

    data class Vnc(val server: NearbyServer) : NearbyDevice {
        override val id = "vnc:${server.id}"
        override val name = server.name
        override val detail = server.address
    }

    data class Iphone(val udid: String, override val name: String, val pairing: String) : NearbyDevice {
        override val id = "iphone:$udid"
        override val detail = pairing
    }
}
