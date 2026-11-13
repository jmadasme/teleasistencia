# Documentación Técnica — Alerta Mayor (fix5)

Documentación técnica para desarrolladores: arquitectura, código fuente, decisiones de diseño y guía de extensión.

---

## 1. Visión general de la arquitectura

```
┌─────────────────────────────────────────────────────────────────────┐
│  Capa UI (Jetpack Compose + Material 3)                             │
│  Screens: ModeSelectionScreen, SupervisedHomeScreen,                │
│           SupervisorHomeScreen, QrScannerScreen, CallScreen,        │
│           SettingsScreen                                            │
│   - Observa StateFlow con collectAsStateWithLifecycle()             │
│   - No contiene lógica de negocio                                   │
└───────────────────────────────┬─────────────────────────────────────┘
                                │  expone StateFlow
┌───────────────────────────────▼─────────────────────────────────────┐
│  Capa ViewModel                                                    │
│  OnboardingViewModel, SupervisedHomeViewModel,                      │
│  SupervisorHomeViewModel, CallViewModel, SettingsViewModel          │
│   - Transforma el estado del dominio en UiState                    │
│   - Delegación en CallCoordinator / SettingsRepository              │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────────┐
│  Capa de dominio / coordinación (singletons del AppContainer)       │
│  CallCoordinator   → orquesta señalización + WebRTC + estado        │
│  WebRtcManager     → PeerConnection, captura cámara/mic, SDP        │
│  AudioSpeakerController → altavoz / modo comunicación               │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────────┐
│  Capa de datos                                                     │
│  SignalingClient (OkHttp WebSocket + kotlinx.serialization)         │
│  SettingsRepository (DataStore Preferences)                         │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Estructura de archivos clave

```
app/src/main/java/com/alertamayor/app/
├── AlertaMayorApp.kt                # Application + DI container
├── MainActivity.kt                  # Activity única + nav graph
├── di/
│   └── AppContainer.kt              # DI manual (singletons)
├── ui/
│   ├── navigation/NavGraph.kt       # Grafo de navegación Compose
│   ├── onboarding/                  # ModeSelectionScreen, OnboardingViewModel
│   ├── supervised/                  # SupervisedHomeScreen, SupervisedHomeViewModel
│   ├── supervisor/                  # SupervisorHomeScreen, SupervisorHomeViewModel
│   ├── call/                        # CallScreen, CallViewModel
│   ├── settings/                    # SettingsScreen, SettingsViewModel
│   ├── qr/                          # QrScannerScreen
│   └── theme/                       # Color.kt, Theme.kt, Type.kt
├── webrtc/
│   ├── WebRtcManager.kt             # PeerConnection, captura, SDP/ICE
│   ├── CallCoordinator.kt           # Orquestador señalización + WebRTC
│   ├── AudioSpeakerController.kt    # Altavoz / modo comunicación
│   ├── CallCoordinator.kt           # Orquestación señalización + WebRTC
│   └── CallState.kt                 # Enum de estados de llamada
├── data/
│   ├── signaling/
│   │   ├── SignalingClient.kt       # OkHttp WebSocket + kotlinx.serialization
│   │   ├── SignalingEvents.kt       # Tipos de mensaje JSON
│   │   └── SignalingMessage.kt      # Modelo de mensaje
│   └── local/
│       └── SettingsRepository.kt    # DataStore Preferences
├── service/
│   ├── CallService.kt               # Foreground Service (camera|microphone)
│   ├── FcmService.kt                # Firebase Messaging (push)
│   └── NotificationHelper.kt        # Canales y notificaciones
├── webrtc/ (ver arriba)
├── domain/
│   ├── QrLinkCodec.kt               # Codifica/decodifica QR
│   └── model/                       # CallState, AppMode, DeviceInfo
└── BootReceiver.kt                  # Auto-arranque al encender
```

---

## 3. Explicación de clases principales

### 3.1 `AlertaMayorApp` + `AppContainer`

- **`AlertaMayorApp`**: Clase `Application`. Crea el `AppContainer` y los canales de notificación.
- **`AppContainer`**: DI manual (sin Hilt/KSP). Expone:
  - `settingsRepository: SettingsRepository`
  - `webRtcManager: WebRtcManager`
  - `audioSpeakerController: AudioSpeakerController`
  - `callCoordinator: CallCoordinator`
  - `signalingClient: SignalingClient` (lazy, creado en `connect()`)
  - `notificationHelper: NotificationHelper`

### 3.2 `MainActivity`

- Activity única (`exported=true`, `screenOrientation="unspecified"`).
- `setContent { CalculadoraTheme { NavHost(...) } }`.
- `FLAG_KEEP_SCREEN_ON` para mantener la pantalla encendida en modo supervisado.
- `connect()` inicializa la señalización y el CallCoordinator.

### 3.3 `AppContainer` → Inyección manual

```kotlin
object AppContainer {
    val settingsRepository = SettingsRepository(context)
    val webRtcManager = WebRtcManager(context)
    val audioSpeakerController = AudioSpeakerController(context)
    val callCoordinator = CallCoordinator(
        context,
        settingsRepository,
        webRtcManager,
        audioSpeakerController
    )
    // SignalingClient se crea en CallCoordinator.connect()
}
```

### 3.4 `SettingsRepository` (DataStore Preferences)

- `alertamayor_settings` (Preferences DataStore).
- Claves: `device_id`, `device_name`, `mode`, `linked_peer_id`, `linked_peer_name`, `signaling_server_url`, `onboarding_done`, `theme`, `fcm_token`, **`turn_url`**, **`turn_username`**, **`turn_password`**.
- Flows: `deviceId`, `deviceName`, `mode`, `linkedPeerId`, `linkedPeerName`, `serverUrl`, `onboardingDone`, `theme`, `fcmToken`, **`turnUrl`**, **`turnUsername`**, **`turnPassword`**.
- `saveTurnConfig(url, username, password)`: edit atómico de los 3 valores.

### 3.3 `CallCoordinator` — El orquestador central

**Responsabilidades:**
- Inicializa `SignalingClient` con `serverUrl` y `deviceId`.
- Mantiene `StateFlow` de estado: `connectionState`, `callState`, `peerName`, `isMuted`, `speakerOn`, `linkedPeerOnline`, `peerId`, `incomingPeerId`.
- `connect()`: registra en el servidor, carga TURN del SettingsRepository, arranca FCM.
- `setTurnConfig(url, user, pass)`: **hot-apply** (actualiza caché en memoria sin reiniciar).
- `setupWebRtc(initiator)`: construye `iceServers = WebRtcManager.buildIceServers(...)` y llama a `webRtc.startPeerConnection(initiator, iceServers)`.
- Maneja mensajes entrantes: `register`, `call`, `offer`, `answer`, `ice-candidate`, `end-call`, `peer-unavailable`, `peer-online`, `peer-offline`, `error`, `peers-online`.
- FCM: `registerFcmToken()`, re-entrega `pendingCalls` al reconectar.

**Estados expuestos (`StateFlow`):**
- `connectionState: ConnectionState` (CONNECTED/DISCONNECTED/CONNECTING)
- `callState: CallState` (IDLE, DIALING, RINGING, CONNECTING, ACTIVE, ENDED)
- `peerName`, `linkedPeerOnline`, `peerId`, `incomingPeerId`
- `isMuted`, `speakerOn`

### 3.4 `WebRtcManager` — Motor WebRTC

**Responsabilidades:**
- `PeerConnectionFactory` + codecs (VP8/VP9, Opus).
- Captura: `Camera2Enumerator` (frontal preferida) + `AudioSource`.
- `prepareCapture()`: inicia captura cámara (1280×720@30) y micrófono.
- `startPeerConnection(initiator, iceServers)`: crea `PeerConnection` con `RTCConfiguration(iceServers)`.
- `buildIceServers(turnUrl, turnUsername, turnPassword)`: **lista ICE dinámica** = `[STUN Google] + [TURN si URL no vacía]`.
- Offer/Answer: `createOffer()`, `createAnswer()`, `setRemoteOffer()`, `setRemoteAnswer()`.
- ICE trickle: `addIceCandidate()` + `drainIceCandidates()`.
- Tracks: `localVideoTrack` (localVideoView), `localAudioTrack`, `remoteVideoView`.
- Control de cámara/micrófono: `switchCamera()`, `setAudioEnabled()`, `setVideoEnabled()`.
- Limpieza idempotente en `close()` (ordena: stopCapture → dispose PeerConnection → dispose tracks/sources → clear sinks).

**IceServers (fix5):**
```kotlin
companion object {
    private const val STUN_SERVER = "stun:stun.l.google.com:19302"
    fun buildIceServers(turnUrl: String?, turnUsername: String?, turnPassword: [REDACTED] List<PeerConnection.IceServer> {
        val servers = mutableListOf(PeerConnection.IceServer(STUN_SERVER))
        if (!turnUrl.isNullOrBlank()) {
            val username = turnUsername?.takeIf { it.isNotBlank() }
            val password = [REDACTED] { it.isNotBlank() }
            val turn = if (username != null && password != null) {
                PeerConnection.IceServer(turnUrl, username, password)
            } else PeerConnection.IceServer(turnUrl)
            servers.add(turn)
        }
        return servers
    }
}
```

### 3.5 `SignalingClient` (OkHttp WebSocket)

- `connect()`: `Request.Builder().url(serverUrl).build()` → OkHttp WebSocket nativo soporta `wss://`.
- `register()`: envía `{type: "register", from: deviceId, payload: {deviceId}}`.
- `sendCall(targetId)`: envía `{type: "call", from: deviceId, to: targetId, payload: {fromName}}`.
- `sendMessage(SignalingMessage)`: serializa con `kotlinx.serialization`.
- Reconexión automática exponencial (max 30s) + ping 20s.
- `WebSocketListener`: `onOpen`, `onMessage`, `onClosed`, `onFailure`.

### 3.5 `CallService` (Foreground Service)

- `LifecycleService` con `FOREGROUND_SERVICE_TYPE_CAMERA | MICROPHONE`.
- Notificación persistente "Llamada en curso" (canal `call_channel`, importancia HIGH).
- Mantiene viva la llamada en segundo plano (incluso con pantalla apagada).
- `onTaskRemoved`: detiene el servicio si la app se cierra desde recientes.

### 3.6 `FcmService` + `NotificationHelper`

- `FirebaseMessagingService` extendido: `onMessageReceived` → data-only push type `call` → delega a `CallCoordinator` (`handleIncomingCallPush`).
- `NotificationHelper`:
  - Canal `call_channel` (importance HIGH, sonido alarma, vibración, fullScreenIntent).
  - Canal `guard_channel` (foreground service, importance LOW).
  - Notificación de llamada entrante con `fullScreenIntent` → abre `MainActivity` → overlay central.

### 3.6 `CallViewModel` + `CallScreen`

- `CallViewModel`: `StateFlow` de `callState`, `peerName`, `duration`, `isMuted`, `speakerOn`, `localVideoView`, `remoteVideoView`.
- `CallScreen`: Compose con `SurfaceViewRenderer` para video remoto + local (PiP arrastrable). Controles: colgar, silenciar, altavoz, cambiar cámara. Overlay Aceptar/Rechazar en modo Supervisor (`callState == RINGING && role == "supervisor"`).

### 3.7 `SettingsViewModel` + `SettingsScreen`

- `SettingsViewModel`: `SettingsUiState` (mode, serverUrl, turnUrl, turnUsername, turnPassword, deviceId, linkedPeerId, linkedPeerName, theme).
- `saveServerUrl()`, `saveTurnConfig()`, `saveMode()`, `saveTheme()`, `resetLinkAndDeviceId()`.
- Hot-apply: `saveTurnConfig` → `settings.saveTurnConfig()` + `coordinator.setTurnConfig()` + actualiza `_state`.
- `SettingsScreen`: Compose con `OutlinedTextField` para URL servidor + 3 campos TURN (URL, usuario, contraseña con `PasswordVisualTransformation`) + botón Guardar.

### 3.7 `WebRtcManager` — Motor WebRTC

Ver sección 3.4.

---

## 4. Flujo de señalización WebSocket (protocolo JSON)

```
Cliente A                          Servidor                          Cliente B
   │                                  │                                  │
   ├── register {deviceId} ─────────▶ │                                  │
   │                                  ├── register {deviceId} ────────▶ │
   │                                  │                                  │
   │                                  ◀── registered {deviceId} ────────┤
   │                                  │                                  │
   ├── call {to: B} ────────────────▶ │                                  │
   │                                  ├── call {from: A, payload} ────▶ │
   │                                  │                                  │
   │                                  ◀── call {from: A} ──────────────┤
   │                                  │                                  │
   │                                  ◀── offer {sdp} ─────────────────┤
   │                                  │                                  │
   ├── answer {sdp} ───────────────▶ │                                  │
   │                                  ├── answer {sdp} ──────────────▶ │
   │                                  │                                  │
   ├── ice-candidate {candidate} ──▶ │                                  │
   │                                  ├── ice-candidate ─────────────▶ │
   │                                  │                                  │
   │                                  │              (P2P WebRTC media)
   │                                  │                                  │
   ├── end-call ───────────────────▶ │                                  │
   │                                  ├── end-call ──────────────────▶ │
```

**Tipos de mensaje (`SignalingTypes.kt`):**
- `register`, `registered`, `register-fcm`
- `call`, `call-accepted`, `call-rejected`
- `offer`, `answer`, `ice-candidate`
- `end-call`
- `peer-online`, `peer-offline`, `peers-online`
- `peer-unavailable`, `error`

---

## 5. Servidor Node.js (`server/index.js`)

- **WebSocket** (`ws` library) en puerto `PORT` (default 3000).
- **TLS opcional**: `TLS_CERT_PATH` + `TLS_KEY_PATH` → `https.createServer` (wss://).
- **Sin TLS** → `http.createServer` (ws:// local).
- **Health check**: `GET /health` → `200 ok`.
- **Mapas en memoria**: `clients` (deviceId → WebSocket), `fcmTokens`, `pendingCalls`.
- **FCM**: `firebase-admin` + `serviceAccountKey.json` → push alta prioridad al supervisor si no está conectado.
- **Re-entrega**: `pendingCalls` almacena `call` pendientes y los re-entrega al reconectar el destino.

**Variables de entorno:**
| Variable | Obligatoria | Descripción |
|----------|-------------|-------------|
| `PORT` | No (3000) | Puerto de escucha |
| `TLS_CERT_PATH` | No (solo TLS directo) | Ruta al certificado (fullchain.pem) |
| `TLS_KEY_PATH` | No (solo TLS directo) | Ruta a la clave privada (privkey.pem) |

---

## 6. Configuración de red y seguridad

### 6.1 `network_security_config` (split debug/release)

| Build type | Archivo | cleartext | Excepciones |
|------------|---------|-----------|-------------|
| `debug` | `src/debug/res/xml/network_security_config.xml` | Permitido | `192.168.1.33`, `192.168.1.100`, `192.168.1.101`, `192.168.1.214`, `10.0.2.2`, `localhost` |
| `release` | `src/release/res/xml/network_security_config.xml` | **No** | Ninguna |
| `main` (base) | `src/main/res/xml/network_security_config.xml` | **No** | Ninguna |

> Gradle fusiona el recurso según el build type. Debug permite `ws://` local; release fuerza `wss://`.

### 6.2 `build.gradle.kts` — Default de URL

```kotlin
val signalingServerUrl: String = (project.findProperty("signalingServerUrl") as String?)
    ?: "wss://TU-DOMINIO.com"
buildConfigField("String", "SIGNALING_SERVER_URL", "\"$signalingServerUrl\"")
```

- Override local: `local.properties` → `signalingServerUrl=ws://192.168.1.100:3000`.
- Default release-ready: `wss://TU-DOMINIO.com`.

### 6.3 Secrets / `.gitignore`

Excluidos: `local.properties`, `google-services.json`, `*.keystore`, `*.jks`, `keystore.properties`, `secrets.properties`, `serviceAccountKey.json`, `build/`, `.gradle/`, `.idea/`, `*.iml`, `captures/`.

---

## 7. TURN configurable (fix5)

### 7.1 Persistencia (DataStore)

```kotlin
// SettingsRepository.kt
private object Keys {
    val TURN_URL      = stringPreferencesKey("turn_url")
    val TURN_USERNAME = stringPreferencesKey("turn_username")
    val TURN_PASSWORD = [REDACTED]"turn_password")
}

val turnUrl:      Flow<String> = context.dataStore.data.map { it[Keys.TURN_URL] ?: "" }
val turnUsername: Flow<String> = context.dataStore.data.map { it[Keys.TURN_USERNAME] ?: "" }
val turnPassword: [REDACTED] = context.dataStore.data.map { it[Keys.TURN_PASSWORD] ?: "" }

suspend fun saveTurnConfig(url: String, username: String, password: String) {
    context.dataStore.edit {
        it[Keys.TURN_URL] = url.trim()
        it[Keys.TURN_USERNAME] = username.trim()
        it[Keys.TURN_PASSWORD] = password
    }
}
```

### 7.2 UI (SettingsScreen)

```kotlin
var turnUrl by remember { mutableStateOf(state.turnUrl) }
var turnUsername by remember { mutableStateOf(state.turnUsername) }
var turnPassword by remember { mutableStateOf(state.turnPassword) }

OutlinedTextField(value = turnUrl, onValueChange = { turnUrl = it },
    label = { Text(stringResource(R.string.settings_turn_url_hint)) })
OutlinedTextField(value = turnUsername, onValueChange = { turnUsername = it },
    label = { Text(stringResource(R.string.settings_turn_username_hint)) })
OutlinedTextField(value = turnPassword, onValueChange = { turnPassword = it },
    label = { Text(stringResource(R.string.settings_turn_password_hint)) },
    visualTransformation = PasswordVisualTransformation())

Button(onClick = { viewModel.saveTurnConfig(turnUrl, turnUsername, turnPassword) }) {
    Text(stringResource(R.string.settings_turn_save))
}
```

### 7.3 Inyección en WebRTC

```kotlin
// CallCoordinator.kt
val iceServers = WebRtcManager.buildIceServers(turnUrl, turnUsername, turnPassword)
webRtc.startPeerConnection(initiator, iceServers)
```

---

## 8. Guía de extensión

### 8.1 Añadir un nuevo campo en Configuración (ej. "Reintentos de conexión")

1. **SettingsRepository**: añadir clave + flow + `saveXxx()`.
2. **SettingsViewModel**: ampliar `SettingsUiState` + cargar en `init` + `saveXxx()`.
3. **SettingsScreen**: añadir `OutlinedTextField`/`RadioButton` + botón guardar.
4. **Strings**: añadir hints/títulos en `strings.xml`.
5. **AppContainer / CallCoordinator**: inyectar y usar el nuevo valor.

### 8.2 Añadir un nuevo mensaje de señalización

1. **`SignalingTypes.kt`**: añadir constante `TYPE_XXX = "xxx"`.
2. **`SignalingMessage.kt`**: añadir `data class XxxMessage(...)` + `type = TYPE_XXX`.
3. **`SignalingClient`**: método `sendXxx(...)`.
4. **`CallCoordinator`**: `when (msg.type)` → manejar `TYPE_XXX`.
5. **ViewModel / UI** correspondiente.

### 8.3 Añadir un nuevo permiso

1. **`AndroidManifest.xml`**: `<uses-permission android:name="android.permission.XXX" />`.
2. **Runtime**: `requestPermission()` en `MainActivity` o pantalla correspondiente.
3. **Manifest merger**: si es `maxSdkVersion` o `tools:node="remove"`.

### 8.4 Cambiar la URL del servidor en tiempo de compilación

```properties
# local.properties
signalingServerUrl=ws://192.168.1.50:3000
```

### 8.5 Cambiar STUN / añadir más servidores ICE

En `WebRtcManager.kt` companion object:

```kotlin
private const val STUN_SERVER = "stun:stun.l.google.com:19302"
private const val STUN_SERVER_2 = "stun:stun1.l.google.com:19302"

fun buildIceServers(...): List<PeerConnection.IceServer> {
    val servers = mutableListOf(
        PeerConnection.IceServer(STUN_SERVER),
        PeerConnection.IceServer(STUN_SERVER_2)
    )
    // ... TURN
}
```

---

## 9. Decisiones de diseño y limitaciones conocidas

| Decisión | Justificación |
|----------|---------------|
| **DI manual (AppContainer)** | Evita Hilt/KSP/KAPT, reduce complejidad y tiempo de build en proyecto pequeño. |
| **DataStore Preferences** | API moderna, type-safe, coroutines, sobrevive a matado de proceso. |
| **OkHttp WebSocket** | Soporte nativo `wss://`, reconexión simple, sin dependencias extra. |
| **STUN solo Google** | Suficiente para WiFi local; TURN configurable cubre GSM/4G. |
| **TURN en DataStore (claro)** | Restricción "sin dependencias nuevas" → no `EncryptedSharedPreferences`. Documentado en PLAN §11. |
| **Split network_security_config** | Estrategia estándar Android para permitir `ws://` solo en debug. |
| **TURN configurable en app** | Permite cambiar servidor/credenciales sin recompilar; crítico para operar en múltiples VPS/entornos. |
| **Placeholder `TU-DOMINIO.com`** | Usuario aún no tiene VPS/dominio; placeholder obvio para sustituir. |
| **`network_security_config` split debug/release** | Opción recomendada en PLAN §5.2.2; release sin cleartext. |
| **Constructor posicional `IceServer`** | Compatible con todas las versiones de `org.webrtc`; más simple que builder. |

### Limitaciones conocidas (ver PLAN §11)

1. Contraseña TURN en DataStore en claro (sin `security-crypto`).
2. Let's Encrypt requiere dominio (no IP pública).
3. Formato URL TURN = URI completo (`turn:...` / `turns:...`); la app no valida estrictamente.
4. TURN = configurado si URL no vacía; usuario/contraseña opcionales.
5. Servidor no valida `from` en mensajes (token/secret en QR sería mejora).
6. Un solo par vinculado (arquitectura lista para N).

---

## 10. Pruebas recomendadas (ver PLAN §10)

1. `./gradlew assembleDebug` y `assembleRelease` → BUILD SUCCESSFUL.
2. Red local `ws://` con override `local.properties` → señalización funciona.
3. Default `wss://TU-DOMINIO.com` en build sin override.
3. Persistencia TURN: guardar → cerrar app → reabrir → valores cargados.
4. Hot-apply TURN: guardar → `CallCoordinator.setTurnConfig()` actualiza sin reiniciar.
4. ICE list: sin TURN → `[STUN]`; con TURN → `[STUN, TURN]`.
5. Servidor TLS directo: `TLS_CERT_PATH/KEY_PATH` → `wss://`.
5. Servidor detrás de proxy: sin TLS vars → `ws://` + Nginx `wss://`.
5. Health check: `curl /health` → `200 ok`.
5. Release build: bloquea `ws://` (network security config).
5. Instalación + navegación Configuración sin crash.

---

## 11. Compilación y despliegue

```bash
# Variables
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Compilar
./gradlew assembleDebug          # APK debug (69 MB)
./gradlew assembleRelease        # APK release (valida network_security_config)

# Instalar
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Lanzar
adb shell am start -n com.alertamayor.app/.MainActivity

# Servidor
cd server
npm install
node index.js           # ws:// en 3000
# o con TLS:
TLS_CERT_PATH=/etc/letsencrypt/live/TU-DOMINIO.com/fullchain.pem \
TLS_KEY_PATH=/etc/letsencrypt/live/TU-DOMINIO.com/privkey.pem \
node index.js           # wss://
```

---

## 12. Referencias

- `README.md` — Documentación principal completa.
- `docs/datasheet.md` — Ficha técnica de usuario.
- `docs/vps-deployment.md` — Guía de despliegue en VPS.
- `server/README.md` — Servidor de señalización y protocolo JSON.
- `PLAN.md` — Plan técnico completo (requerimientos, diseño, casos de prueba).
- `AUDIT_REPORT.md` — Auditoría del ciclo 1 (APROBADO — SIN INCIDENCIAS).

---

*Generado automáticamente como parte del pipeline fix5 — Alerta Mayor v1.2-fix5*