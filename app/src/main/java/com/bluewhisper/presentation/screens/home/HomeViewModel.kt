package com.bluewhisper.presentation.screens.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluewhisper.bluetooth.BTEvent
import com.bluewhisper.bluetooth.NearbyConnectionsManager
import com.bluewhisper.data.local.UserProfileDataStore
import com.bluewhisper.domain.model.*
import com.bluewhisper.presentation.navigation.BTErrorType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val nearbyDevices: List<NearbyDevice> = emptyList(),
    val isScanning: Boolean = false,
    val isDiscoverable: Boolean = true,
    val userProfile: UserProfile = UserProfile(),
    val connectionState: ConnectionState = ConnectionState.Idle,
    val needsPermissions: Boolean = false,
    val btErrorType: BTErrorType? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nearbyManager: NearbyConnectionsManager,
    private val userProfileDataStore: UserProfileDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val connectionState = nearbyManager.connectionState

    init {
        observeProfile()
        observeNearbyDevices()
        observeConnectionState()
        observeEvents()
    }

    private fun observeProfile() {
        viewModelScope.launch {
            userProfileDataStore.userProfile.collect { profile ->
                _uiState.update { it.copy(
                    userProfile = profile,
                    isDiscoverable = profile.isDiscoverable
                )}
            }
        }
    }

    private fun observeNearbyDevices() {
        viewModelScope.launch {
            nearbyManager.nearbyDevices.collect { devices ->
                _uiState.update { it.copy(nearbyDevices = devices) }
            }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            nearbyManager.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            nearbyManager.events.collect { event ->
                when (event) {
                    is BTEvent.Error -> handleBTError(event.message)
                    else -> Unit
                }
            }
        }
    }

    fun startScanning() {
        // FR-09.1 FIX: scanning is owned by BluetoothForegroundService.
        // HomeViewModel just tells the service to start (idempotent).
        val intent = android.content.Intent(context,
            com.bluewhisper.service.BluetoothForegroundService::class.java).apply {
            action = com.bluewhisper.service.BluetoothForegroundService.ACTION_START_SCANNING
        }
        context.startService(intent)
        _uiState.update { it.copy(isScanning = true) }
    }

    fun stopScanning() {
        // When HomeScreen is disposed (app backgrounded) do NOT stop scanning.
        // The service keeps scanning. Only update local UI state.
        _uiState.update { it.copy(isScanning = false) }
    }

    fun toggleDiscoverability() {
        val newValue = !_uiState.value.isDiscoverable
        _uiState.update { it.copy(isDiscoverable = newValue) }

        viewModelScope.launch {
            userProfileDataStore.updateDiscoverability(newValue)
        }

        // Tier 3 #12 (B-04): the foreground service is the sole owner of
        // startAdvertising/stopAdvertising. HomeViewModel only fires an intent —
        // this eliminates the prior race where toggling Off and the service's
        // post-disconnect restart could leave UI and radio out of sync.
        val intent = android.content.Intent(context,
            com.bluewhisper.service.BluetoothForegroundService::class.java).apply {
            action = if (newValue)
                com.bluewhisper.service.BluetoothForegroundService.ACTION_BECOME_DISCOVERABLE
            else
                com.bluewhisper.service.BluetoothForegroundService.ACTION_BECOME_INVISIBLE
        }
        context.startService(intent)
    }

    fun requestConnection(endpointId: String) {
        val profile = _uiState.value.userProfile
        nearbyManager.requestConnection(
            endpointId,
            nearbyManager.formatAdvertisingName(profile.nickname, profile.avatarId)
        )
    }

    fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkPermission(Manifest.permission.BLUETOOTH_SCAN) &&
            checkPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
            checkPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            checkPermission(Manifest.permission.BLUETOOTH) &&
            checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun checkPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

    private fun handleBTError(message: String) {
        val errorType = when {
            message.contains("BLE", ignoreCase = true) -> BTErrorType.BLE_NOT_SUPPORTED
            message.contains("permission", ignoreCase = true) -> BTErrorType.PERMISSION_DENIED
            else -> BTErrorType.BLUETOOTH_ERROR
        }
        _uiState.update { it.copy(btErrorType = errorType) }
    }

    fun clearBTError() {
        _uiState.update { it.copy(btErrorType = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }
}
