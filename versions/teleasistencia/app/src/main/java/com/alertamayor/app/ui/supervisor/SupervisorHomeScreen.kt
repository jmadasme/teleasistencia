package com.alertamayor.app.ui.supervisor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alertamayor.app.R
import com.alertamayor.app.domain.model.CallState
import com.alertamayor.app.ui.theme.GreenOnline
import com.alertamayor.app.ui.theme.Primary
import com.alertamayor.app.ui.theme.RedAlert

@Composable
fun SupervisorHomeScreen(
    viewModel: SupervisorHomeViewModel,
    onScanQr: () -> Unit,
    onCall: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val activePeerId by viewModel.activePeerId.collectAsStateWithLifecycle()
    val incomingPeerId by viewModel.incomingPeerId.collectAsStateWithLifecycle()

    // Navega a la llamada cuando hay una en curso
    LaunchedEffect(callState, activePeerId, incomingPeerId) {
        if (callState == CallState.RINGING || callState == CallState.CONNECTING ||
            callState == CallState.ACTIVE
        ) {
            val peerId = activePeerId.ifEmpty { incomingPeerId }
            if (peerId.isNotEmpty()) {
                onCall(peerId)
            }
        }
    }

    // M-004 (ciclo 12): fondo NEGRO también en la pantalla del Supervisor
    // (dispositivos vinculados), coherente con la pantalla supervisada.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            text = stringResource(R.string.mode_supervisor_title),
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White
        )
        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = onScanQr,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Primary, contentColor = Color.White)
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.supervisor_scan_qr))
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.supervisor_linked_devices),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Spacer(Modifier.height(16.dp))

        if (state.linkedPeerId.isEmpty()) {
            Text(
                text = stringResource(R.string.supervisor_no_devices),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.linkedPeerName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (state.peerOnline)
                                stringResource(R.string.supervisor_status_online)
                            else
                                stringResource(R.string.supervisor_status_offline),
                            color = if (state.peerOnline) GreenOnline else Color.Gray
                        )
                    }
                    Button(
                        onClick = { viewModel.callLinkedPeer() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedAlert)
                    ) {
                        Icon(Icons.Filled.Phone, contentDescription = null)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.supervisor_call))
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Configuración")
        }
    }
}
