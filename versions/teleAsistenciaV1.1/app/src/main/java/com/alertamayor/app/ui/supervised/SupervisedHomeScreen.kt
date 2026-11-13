package com.alertamayor.app.ui.supervised

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Surface
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import com.alertamayor.app.R
import com.alertamayor.app.data.network.NetworkStatus
import com.alertamayor.app.domain.model.CallState
import com.alertamayor.app.ui.theme.GreenOnline
import com.alertamayor.app.ui.theme.RedAlert
import com.alertamayor.app.ui.theme.RedAlertDark

@Composable
fun SupervisedHomeScreen(
    viewModel: SupervisedHomeViewModel,
    onCall: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val qrBitmap by viewModel.qrBitmap.collectAsStateWithLifecycle()
    val isLinked by viewModel.isLinked.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val activePeerId by viewModel.activePeerId.collectAsStateWithLifecycle()
    val incomingPeerId by viewModel.incomingPeerId.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()

    // R-002 (ciclo 4): bloquear el botón Back del sistema en modo Supervisado para
    // que el anciano/niño no pueda salir de la pantalla de ALERTA.
    BackHandler {}

    // Permisos cámara/micrófono
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun ensurePermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        ensurePermissions()
        viewModel.generateQr(context)
    }

    // Navega automáticamente a la llamada cuando hay una en curso (auto-respuesta)
    // Usa el par activo/entrante del coordinator, no linkedPeerId (que en modo
    // Supervisado siempre está vacío). — I-002
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

    // La pantalla se mantiene encendida gracias a FLAG_KEEP_SCREEN_ON en MainActivity
    // (R-003 ciclo 4), de modo que el botón rojo de ALERTA esté siempre visible/operativo.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Estado
            StatusBadge(connected = state.connected, linked = isLinked, peerName = state.linkedPeerName)

            Spacer(Modifier.height(40.dp))

            if (!isLinked) {
                // Mostrar QR de vinculación
                Text(
                    text = stringResource(R.string.supervised_no_link),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                qrBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR",
                        modifier = Modifier.size(220.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.generateQr(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAlert)
                ) {
                    Text(stringResource(R.string.supervised_link_qr))
                }
            } else {
                // Botón rojo grande de alerta.
                // M-002 (ciclo 6): sin internet (NONE) el botón se muestra gris con
                // barra diagonal roja y su onClick no inicia ninguna llamada.
                val online = networkStatus != NetworkStatus.NONE
                BigRedButton(
                    onClick = { if (online) viewModel.callLinkedPeer() },
                    enabled = online
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(
                        if (online) R.string.supervised_call_hint
                        else R.string.supervised_no_connection
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Indicador de conexión a internet (arriba a la izquierda) — M-002 (ciclo 6)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = when (networkStatus) {
                    NetworkStatus.WIFI -> Icons.Filled.Wifi
                    NetworkStatus.CELLULAR -> Icons.Filled.SignalCellularAlt
                    NetworkStatus.NONE -> Icons.Filled.WifiOff
                },
                contentDescription = stringResource(
                    when (networkStatus) {
                        NetworkStatus.WIFI -> R.string.network_status_wifi
                        NetworkStatus.CELLULAR -> R.string.network_status_cellular
                        NetworkStatus.NONE -> R.string.network_status_none
                    }
                ),
                tint = when (networkStatus) {
                    NetworkStatus.NONE -> RedAlert
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(28.dp)
            )
        }

        // Botón de configuración
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Configuración")
        }
    }
}

@Composable
private fun StatusBadge(connected: Boolean, linked: Boolean, peerName: String) {
    val statusText = when {
        !connected -> stringResource(R.string.supervised_connecting)
        !linked -> stringResource(R.string.supervised_no_peer)
        else -> stringResource(R.string.supervised_linked_to, peerName)
    }
    val color = when {
        !connected -> Color(0xFFFF9800)
        !linked -> Color(0xFFFF9800)
        else -> GreenOnline
    }
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = statusText,
            color = color,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun BigRedButton(onClick: () -> Unit, enabled: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.92f else 1f)
    val haptic = LocalHapticFeedback.current

    // Animación de campanilla oscilando (swing) para el botón de alerta.
    val bellTransition = rememberInfiniteTransition(label = "bell")
    val bellAngle by bellTransition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bellAngle"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(220.dp)
            .scale(scale)
    ) {
        Button(
            onClick = {
                // Feedback háptico al pulsar — I-010
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            modifier = Modifier
                .fillMaxSize(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                // M-002 (ciclo 6): gris cuando no hay internet, rojo cuando sí.
                containerColor = if (enabled) RedAlert else Color.Gray,
                contentColor = Color.White
            ),
            interactionSource = interactionSource
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = stringResource(R.string.supervised_alert_button),
                modifier = Modifier
                    .size(120.dp)
                    .rotate(bellAngle),
                tint = Color.White
            )
        }

        // M-002 (ciclo 6): barra diagonal roja superpuesta cuando no hay internet,
        // indicando que el botón ALERTA está inhabilitado.
        if (!enabled) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                val stroke = 18.dp.toPx()
                drawLine(
                    color = RedAlert,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                    strokeWidth = stroke,
                )
            }
        }
    }
}
