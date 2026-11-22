package org.scrctl.android.gui.ios

import androidx.compose.runtime.Composable
import org.scrctl.android.gui.data.NearbyDevice

internal data class IphoneDiscoveryState(
    val devices: List<NearbyDevice.Iphone> = emptyList(),
    val busy: Boolean = false,
    val canCancel: Boolean = false,
    val message: String? = null,
    val pin: String? = null,
)

internal data class IphoneIntegration(
    val discovery: IphoneDiscoveryState,
    val refresh: () -> Unit,
    val pair: () -> Unit,
    val prepare: (NearbyDevice.Iphone) -> Unit,
    val cancel: () -> Unit,
    val session: @Composable (NearbyDevice.Iphone, () -> Unit) -> Unit,
)
