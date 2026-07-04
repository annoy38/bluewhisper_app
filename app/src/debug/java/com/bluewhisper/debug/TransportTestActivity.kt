package com.bluewhisper.debug

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * DEBUG-ONLY launcher activity for validating [com.bluewhisper.bluetooth.BluetoothTransport]
 * on two physical devices. Appears as a separate "BW Transport Test" icon in debug builds.
 */
@AndroidEntryPoint
class TransportTestActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* proceed regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(requiredPermissions())
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { TransportTestScreen(::requestDiscoverable) } } }
    }

    private fun requestDiscoverable() {
        startActivity(
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        )
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
}

@Composable
private fun TransportTestScreen(
    onRequestDiscoverable: () -> Unit,
    vm: TransportTestViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsState()
    var nick by remember { mutableStateOf(vm.nickname) }
    var msg by remember { mutableStateOf("hello") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.sendFile(it) }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("BlueWhisper transport test — state: ${ui.state}${if (ui.secure) " 🔒" else ""}", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = nick,
            onValueChange = { nick = it; vm.nickname = it },
            label = { Text("Nickname") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onRequestDiscoverable(); vm.advertise() }) { Text("Advertise") }
            Button(onClick = { vm.discover() }) { Text("Discover") }
            Button(onClick = { vm.stop() }) { Text("Stop") }
        }

        // Discovered peers → tap to connect
        if (ui.devices.isNotEmpty()) {
            Text("Nearby (${ui.devices.size}):", style = MaterialTheme.typography.labelLarge)
            ui.devices.forEach { d ->
                Card(Modifier.fillMaxWidth().clickable { vm.connect(d.endpointId) }) {
                    Text("${d.nickname}  ·  ${d.signalStrength}  ·  ${d.endpointId}", Modifier.padding(8.dp))
                }
            }
        }

        // Incoming request → Accept / Decline
        ui.incoming?.let { req ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Incoming: ${req.requesterNickname}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.accept() }) { Text("Accept") }
                        Button(onClick = { vm.decline() }) { Text("Decline") }
                    }
                }
            }
        }

        // SAS numeric confirmation
        ui.sasCode?.let { code ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Confirm code matches on both phones: $code", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.confirmSas(true) }) { Text("Match") }
                        Button(onClick = { vm.confirmSas(false) }) { Text("No match") }
                    }
                }
            }
        }

        // Send message / file
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = msg, onValueChange = { msg = it }, label = { Text("Message") }, modifier = Modifier.weight(1f))
            Button(onClick = { vm.sendMessage(msg) }) { Text("Send") }
        }
        Button(onClick = { filePicker.launch("*/*") }) { Text("Send file") }

        Text("Log:", style = MaterialTheme.typography.labelLarge)
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(ui.log.reversed()) { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
