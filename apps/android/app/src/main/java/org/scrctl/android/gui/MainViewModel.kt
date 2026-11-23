package org.scrctl.android.gui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import org.scrctl.android.gui.data.AppSettings
import org.scrctl.android.gui.data.Connection
import org.scrctl.android.gui.data.matchingEndpoint
import org.scrctl.android.gui.data.SessionPointerMode
import org.scrctl.android.gui.data.ConnectionStore
import org.scrctl.android.gui.data.LibrarySortOrder
import org.scrctl.android.gui.data.NearbyDevice
import org.scrctl.android.gui.data.NearbyDiscovery
import org.scrctl.android.gui.data.NearbyServer
import org.scrctl.android.gui.data.QualityPreset
import org.scrctl.android.gui.data.UpdateChannel
import org.scrctl.android.gui.data.SettingsStore
import org.scrctl.android.gui.data.SessionToolbarItem
import org.scrctl.android.gui.data.SshKey
import org.scrctl.android.gui.data.SshKeyStore
import org.scrctl.android.gui.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val connectionStore = ConnectionStore.shared(application)
    val connections = connectionStore.connections
    val groups = connectionStore.groups
    internal val previews = connectionStore.previews

    private val sshKeyStore = SshKeyStore(application)
    val sshKeys = sshKeyStore.keys

    private val settingsStore = SettingsStore(application)
    private val _settings = MutableStateFlow(settingsStore.load())
    val settings = _settings.asStateFlow()

    private val nearbyDiscovery = NearbyDiscovery(application)
    val nearby: StateFlow<List<NearbyServer>> = nearbyDiscovery.servers

    private val _route = MutableStateFlow<Route>(Route.Library)
    internal val route = _route.asStateFlow()

    init {
        if (_settings.value.discoverNearby) {
            nearbyDiscovery.start()
        }
    }

    override fun onCleared() {
        nearbyDiscovery.stop()
        super.onCleared()
    }

    fun connection(id: String): Connection? = connectionStore.find(id)

    fun quickConnect(host: String, port: Int, username: String?) {
        openEndpoint(Connection(name = host, host = host, port = port, username = username.orEmpty()))
    }

    fun openEndpoint(target: Connection, nearby: Boolean = false, asGuest: Boolean = false) {
        val saved = connections.value.matchingEndpoint(target)
        if (saved == null && !nearby) connectionStore.save(target)
        navigate(Route.Session(saved?.id ?: target.id, if (saved == null && nearby) target else null, asGuest = asGuest))
    }

    fun rememberLogin(connection: Connection): Boolean = connectionStore.rememberLogin(connection)

    fun saveConnection(connection: Connection) {
        connectionStore.save(connection)
    }

    fun deleteConnection(id: String): Boolean = connectionStore.deleteConnection(id)

    fun setToolbarItems(items: List<SessionToolbarItem>) {
        _settings.update { it.copy(toolbarItems = items.distinct()) }
        settingsStore.save(_settings.value)
    }

    fun setShowSessionPreviews(enabled: Boolean) {
        previews.setEnabled(enabled)
        _settings.update { it.copy(showSessionPreviews = enabled) }
        settingsStore.save(_settings.value)
    }

    fun markConnected(id: String) {
        connectionStore.markConnected(id)
    }

    fun addGroup(name: String) {
        connectionStore.addGroup(name)
    }

    fun renameGroup(id: String, name: String) {
        connectionStore.renameGroup(id, name)
    }

    fun deleteGroup(id: String) {
        connectionStore.deleteGroup(id)
    }

    fun moveConnection(id: String, offset: Int, orderedIds: List<String>) {
        connectionStore.moveConnection(id, offset, orderedIds)
    }

    fun setLibrarySort(sort: LibrarySortOrder) {
        _settings.update { it.copy(librarySort = sort) }
        settingsStore.save(_settings.value)
    }

    fun setDiscoverNearby(enabled: Boolean) {
        if (_settings.value.discoverNearby == enabled) return
        _settings.update { it.copy(discoverNearby = enabled) }
        settingsStore.save(_settings.value)
        if (enabled) nearbyDiscovery.start() else nearbyDiscovery.stop()
    }

    fun sshKey(id: String?): SshKey? = sshKeyStore.find(id)

    fun generateSshKey(name: String): Result<SshKey> = runCatching {
        val generated = uniffi.rfb_ffi.generateSshKey()
        SshKey(
            name = name,
            privateKeyPem = generated.privateKeyPem,
            publicKey = generated.publicKeyOpenssh,
            fingerprint = generated.fingerprint,
        ).also(sshKeyStore::add)
    }

    fun importSshKey(name: String, pem: String, passphrase: String): Result<SshKey> = runCatching {
        val pass = passphrase.ifBlank { null }
        val fingerprint = uniffi.rfb_ffi.sshKeyFingerprint(pem, pass)
        val publicKey = uniffi.rfb_ffi.sshPublicKey(pem, pass)
        SshKey(
            name = name,
            privateKeyPem = pem,
            publicKey = publicKey,
            fingerprint = fingerprint,
            passphrase = passphrase,
        ).also(sshKeyStore::add)
    }

    fun deleteSshKey(id: String) {
        sshKeyStore.delete(id)
    }

    fun setThemeMode(mode: ThemeMode) {
        _settings.update { it.copy(themeMode = mode) }
        settingsStore.save(_settings.value)
    }

    fun setConnectionQuality(id: String, quality: QualityPreset) {
        connectionStore.setQuality(id, quality)
    }

    fun cursorMode(connection: Connection): SessionPointerMode = connectionStore.cursorMode(connection)

    fun setCursorMode(connection: Connection, mode: SessionPointerMode) {
        connectionStore.setCursorMode(connection, mode)
    }

    fun setPreferredDisplay(id: String, displayId: Int?) {
        connectionStore.setPreferredDisplay(id, displayId)
    }

    fun setUpdateChannel(channel: UpdateChannel) {
        _settings.update { it.copy(updateChannel = channel) }
        settingsStore.save(_settings.value)
    }

    internal fun navigate(route: Route) {
        _route.value = route
    }

    fun back() {
        _route.value = when (_route.value) {
            Route.SshKeys -> Route.Settings
            else -> Route.Library
        }
    }
}

internal sealed interface Route {
    data object Library : Route
    data class IphoneSession(val device: NearbyDevice.Iphone) : Route
    data object Settings : Route
    data object SshKeys : Route
    data class Editor(val connectionId: String?, val draft: Connection? = null) : Route
    data class Session(
        val connectionId: String,
        val nearby: Connection? = null,
        val observeOnly: Boolean = false,
        val asGuest: Boolean = false,
    ) : Route
}
