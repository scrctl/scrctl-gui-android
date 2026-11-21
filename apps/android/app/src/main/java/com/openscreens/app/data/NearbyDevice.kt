package com.openscreens.app.data

internal sealed interface NearbyDevice {
    val id: String
    val name: String

    data class Vnc(val server: NearbyServer) : NearbyDevice {
        override val id = "vnc:${server.id}"
        override val name = server.name
    }

    data class Iphone(val udid: String, override val name: String) : NearbyDevice {
        override val id = "iphone:$udid"
    }
}
