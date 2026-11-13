# Developer Guide — Alerta Mayor

Explicación técnica del código fuente, arquitectura interna, diagramas de flujo y guía de extensión para desarrolladores.

---

## 1. Estructura de paquetes

El código Android se encuentra bajo `app/src/main/java/com/alertamayor/app/`:

```
com.alertamayor.app/
├── AlertaMayorApp.kt              # Clase Application (crea AppContainer + canales)
├── MainActivity.kt                # Activity única (conecta señalización + NavGraph)
│
├── di/
│   └── AppContainer.kt            # DI manual (singletons)
│
├── data/
│   ├── local/
│   │   └── SettingsRepository.kt  # DataStore Preferences
│   └── signaling/
│       ├── SignalingClient.kt     # OkHttp WebSocket + reconexión
│       ├── SignalingMessage.kt    # DTO serializable + tipos de mensaje
│       └── SignalingEvents.kt     # Eventos emitidos (sealed interface)
│
├── domain/
│   ├── model/
│   │   ├── AppMode.kt             # enum SUPERVISED / SUPERVISOR
│   │   ├── CallState.kt           # enum de estado de llamada
│   │   └── DeviceInfo.kt          # modelo de dispositivo vinculado
│   └── QrLinkCodec.kt             # codifica/decodifica el QR de vinculación
│
├── webrtc/
│   ├── CallCoordinator.kt         # orquesta señalización + WebRTC + estado
│   ├── WebRtcManager.kt           # PeerConnection, captura cámara/mic, SDP/ICE
│   └── AudioSpeakerController.kt  # altavoz / modo comunicación
│
├── service/
│   ├── CallService.kt             # Foreground Service de la llamada
│   └── NotificationHelper.kt      # canales + notificaciones
│
└── ui/
    ├── navigation/NavGraph.kt     # grafo de navegación + diálogo de nombre
    ├── AppViewModelFactory.kt     # factory de ViewModels (DI manual)
    ├── theme/ (Color.kt, Theme.kt, Type.kt)
    ├── onboarding/ (ModeSelectionScreen + OnboardingViewModel)
    ├── supervised/ (SupervisedHomeScreen + SupervisedHomeViewModel)
    ├── supervisor/ (SupervisorHomeScreen + SupervisorHomeViewModel)
    ├── qr/ (QrScannerScreen.kt)
    ├── call/ (CallScreen + CallViewModel)
    └── settings/ (SettingsScreen + SettingsViewModel)
```

El servidor de señalización está en `server/index.js` (Node.js + `ws`).

---

## 2. Arquitectura (MVVM + Compose + WebRTC)

```
Compose Screen ──observa──▶ ViewModel (StateFlow) ──delega──▶ CallCoordinator / SettingsRepository
                                                                   │
                                        ┌──────────────────────────┼────────────────────────┐
                                        ▼                          ▼                        ▼
                                   SignalingClient           WebRtcManager          AudioSpeakerController
                                   (OkHttp WS)               (PeerConnection)        (AudioManager)
                                        │                          │
                                        ▼                          ▼
                              Servidor Node.js            Cámara (Camera2) / Micrófono
```

### 2.1 Flujo de datos

- La **UI** solo observa `StateFlow` con `collectAsStateWithLifecycle()` y dispara acciones del ViewModel.
- Los **ViewModels** no manejan lógica de negocio de llamada: delegan en `CallCoordinator`.
- **`CallCoordinator`** es un singleton del `AppContainer` que centraliza la orquestación de la llamada.
- **`WebRtcManager`** y **`AudioSpeakerController`** también son singletons, para sobrevivir a rotación/cierre de pantalla.
- La llamada en segundo plano se mantiene con el **`CallService`** (Foreground Service).

### 2.2 Navegación

Definida en `NavGraph.kt` con Jetpack Navigation Compose:

| Ruta | Pantalla |
|------|----------|
| `onboarding` | `ModeSelectionScreen` |
| `supervisedHome` | `SupervisedHomeScreen` |
| `supervisorHome` | `SupervisorHomeScreen` |
| `qrScanner` | `QrScannerScreen` |
| `call/{role}/{peerId}` | `CallScreen` (parámetros `role` y `peerId`) |
| `settings` | `SettingsScreen` |

El destino inicial es `onboarding` o `supervisedHome` según `onboardingDone`.

### 2.3 Manejo de estado

- `StateFlow` como fuente de verdad; `collectAsStateWithLifecycle()` en la UI.
- `CallState` enum: `IDLE`, `DIALING`, `RINGING`, `CONNECTING`, `ACTIVE`, `ENDED`.
- `CallCoordinator` expone: `connectionStatus`, `callState`, `activePeerId`, `incomingPeerId`, `muted`, `speakerOn`, `peerOnline`, `callInProgress`.
- Eventos de una sola vez (llamada entrante) se manejan mediante el callback `onIncomingCall` y navegación reactiva (`LaunchedEffect`).

---

## 3. Descripción de clases y funciones clave

### 3.1 `AlertaMayorApp` (Application)

```kotlin
class AlertaMayorApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
    }
}
```

Crea el contenedor de dependencias y los canales de notificación al arrancar el proceso.

### 3.2 `MainActivity`

```kotlin
coordinator.connect()   // conecta la señalización al abrir la app
setContent { AlertaMayorTheme { AlertaMayorNavGraph() } }
```

Activity única, orientación retrato, `launchMode="singleTask"`. Al crearse, conecta el `CallCoordinator` al servidor de señalización.

### 3.3 `AppContainer` (DI manual)

```kotlin
val settingsRepository by lazy { SettingsRepository(appContext) }
val webRtcManager by lazy { WebRtcManager(appContext) }
val audioSpeakerController by lazy { AudioSpeakerController(appContext) }
val callCoordinator by lazy { CallCoordinator(...) }
```

Inyecta los singletons y provee callbacks para **arrancar/detener el `CallService`** (`onForegroundStart`/`onForegroundStop`).

### 3.4 `CallCoordinator` (orquestador)

Métodos principales:

| Método | Descripción |
|--------|-------------|
| `connect()` | Lee `deviceId`/`mode`/`linkedPeerId`/URL de DataStore, genera el `deviceId` si falta y arranca la señalización. |
| `setMode(mode)` | Aplica un cambio de modo en caliente (desde Configuración). |
| `setServerUrl(url)` | Reconecta la señalización con una nueva URL. |
| `updateLinkedPeer(id)` | Actualiza el par vinculado en memoria (al vincular por QR). |
| `startOutgoingCall(peerId, name)` | Inicia una llamada saliente (con guardas anti-duplicado). |
| `acceptIncomingCall()` | Acepta una llamada entrante (auto-respuesta o manual). |
| `rejectIncomingCall()` | Rechaza una llamada entrante. |
| `hangUp()` | Cuelga, envía `end-call` y limpia. |
| `toggleMute()` / `toggleSpeaker()` / `switchCamera()` | Controles de la llamada. |
| `handleEvent(event)` | Despacha los eventos de señalización (IncomingCall, Offer, Answer, IceCandidate, EndCall, PeerOnline/Offline, etc.). |
| `setupWebRtc(initiator, onOfferReady)` | Prepara la captura, activa el altavoz si es supervisado, y configura listener/candidatos. |

**Reglas de negocio implementadas:**

- **Auto-respuesta supervisada**: en `handleEvent`, si `mode == SUPERVISED`, llama a `acceptIncomingCall()` directamente.
- **Supervisor**: marca `RINGING` e invoca `onIncomingCall?.invoke(from)` para notificar.
- **Anti-duplicado (I-006)**: `startOutgoingCall` y `acceptIncomingCall` retornan si ya hay una llamada en curso o el estado es `DIALING/RINGING/CONNECTING/ACTIVE`.
- **Altavoz automático** en `setupWebRtc` si `mode == SUPERVISED`.

### 3.5 `WebRtcManager`

Gestiona la sesión WebRTC:

| Método | Descripción |
|--------|-------------|
| `initialize()` | Crea `EglBase`, inicializa `SurfaceViewRenderer` (local/remoto) y `PeerConnectionFactory` con factories de video H264/VP8. |
| `prepareCapture()` | Enumera cámaras con `Camera2Enumerator`, elige trasera (o frontal), crea `VideoSource`/`AudioSource` y tracks, e inicia la captura a 1280×720@30. |
| `startPeerConnection(initiator)` | Crea la `PeerConnection` con el servidor STUN y añade los tracks locales. |
| `createOffer(onSdpReady)` | Genera la oferta SDP (solo iniciador). |
| `setRemoteOffer(sdp)` / `createAnswer(onSdpReady)` | Aplica la oferta entrante y genera la respuesta. |
| `setRemoteAnswer(sdp)` | Aplica la respuesta en el iniciador y drena candidatos pendientes. |
| `addIceCandidate(...)` | Agrega un candidato ICE (o lo encola si aún no hay `remoteDescription`). |
| `switchCamera()` / `setAudioEnabled()` | Controles. |
| `close()` | Cierra la `PeerConnection` y libera captura. |

**Detalles importantes:**

- `STUN_SERVER = "stun:stun.l.google.com:19302"`.
- `SdpSemantics.UNIFIED_PLAN`.
- `onIceConnectionChange` → `CONNECTED` dispara `onConnectionEstablished()`; `DISCONNECTED/FAILED/CLOSED` dispara `onDisconnected()`.
- `onTrack` agrega el video remoto al `remoteVideoView`.

### 3.6 `AudioSpeakerController`

```kotlin
fun enableSpeakerphone()   // MODE_IN_COMMUNICATION + isSpeakerphoneOn = true
fun disableSpeakerphone()  // MODE_IN_COMMUNICATION + isSpeakerphoneOn = false
fun restore()              // MODE_NORMAL + isSpeakerphoneOn = false
```

### 3.7 `SignalingClient` (OkHttp WebSocket)

- Conexión WebSocket con `pingInterval` de 20 s.
- `connect()` crea el WebSocket; `onOpen` envía automáticamente `register`.
- `register()`, `sendCall()`, `sendCallAccepted()`, `sendCallRejected()`, `sendOffer()`, `sendAnswer()`, `sendIceCandidate()`, `sendEndCall()`.
- `handleMessage()` convierte los mensajes JSON en `SignalingEvent` y los emite por un `Channel` (Flow).
- **Reconexión automática** (`scheduleReconnect()`, 3 s) tras `onClosed`/`onFailure`.
- `disconnect()` cancela el scope y cierra el WebSocket.

**Corrección I-001**: el SDP (`sdp`) y el candidato ICE se extraen con `.jsonPrimitive.content` para evitar entrecomillado/escapado incorrecto.

### 3.8 `SignalingMessage` y `SignalingTypes`

`SignalingTypes` define las constantes del protocolo:

```
register, registered, call, call-accepted, call-rejected,
offer, answer, ice-candidate, end-call, peer-unavailable,
error, peer-online, peer-offline
```

`SignalingMessage` es el DTO genérico `@Serializable` con `type`, `from`, `to`, `payload`.

### 3.9 `SignalingEvents`

`sealed interface` con los eventos que el cliente emite a la app: `Registered`, `IncomingCall`, `CallAccepted`, `CallRejected`, `Offer`, `Answer`, `IceCandidate`, `EndCall`, `PeerUnavailable`, `Error`, `PeerOnline`, `PeerOffline`.

### 3.10 `SettingsRepository` (DataStore Preferences)

Claves: `device_id`, `device_name`, `mode`, `linked_peer_id`, `linked_peer_name`, `signaling_server_url`, `onboarding_done`.

Métodos: `saveDeviceId`, `saveDeviceName`, `saveMode`, `saveLinkedPeer`, `saveServerUrl`, `setOnboardingDone`, `resetLinkAndDeviceId` (borra vínculo y regenera `deviceId`).

> La URL del servidor usa como fallback `BuildConfig.SIGNALING_SERVER_URL` (inyectada desde `local.properties` en `build.gradle.kts`).

### 3.11 `CallService` (Foreground Service)

```kotlin
class CallService : LifecycleService() {
    override fun onCreate() {
        ServiceCompat.startForeground(this, NOTIFICATION_ID_CALL,
            NotificationHelper.buildCallNotification(this),
            FOREGROUND_SERVICE_TYPE_CAMERA or FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }
}
```

Mantiene viva la llamada en segundo plano y muestra la notificación persistente "Llamada en curso". Usa `ServiceCompat.startForeground` con el tipo explícito (corrección I-008).

### 3.12 `NotificationHelper`

- `createChannels()`: crea el canal `calls` (`IMPORTANCE_HIGH`).
- `buildCallNotification()`: notificación persistente de llamada en curso.
- `buildIncomingCallNotification()`: notificación de llamada entrante (modo Supervisor).

### 3.13 `QrLinkCodec`

- `encode(deviceId)`: genera `alertamayor:link?deviceId=<uuid>`.
- `decode(raw)`: extrae el `deviceId` del esquema propio o, por retrocompatibilidad, de JSON plano `{"deviceId":"..."}`.

### 3.14 ViewModels

| ViewModel | Estado expuesto | Acciones |
|-----------|-----------------|----------|
| `OnboardingViewModel` | `onboardingDone` | `selectMode(mode)` |
| `SupervisedHomeViewModel` | `state`, `qrBitmap`, `isLinked`, `callState`, `activePeerId`, `incomingPeerId` | `generateQr(ctx)`, `callLinkedPeer()` |
| `SupervisorHomeViewModel` | `state`, `callState`, `peerOnline`, `activePeerId`, `incomingPeerId` | `linkPeer()`, `unlinkPeer()`, `callLinkedPeer()` |
| `CallViewModel` | `callState`, `muted`, `speakerOn`, vistas local/remota | `hangUp()`, `toggleMute()`, `toggleSpeaker()`, `switchCamera()`, `acceptIncomingCall()`, `rejectIncomingCall()` |
| `SettingsViewModel` | `state` | `saveMode()`, `saveServerUrl()`, `resetLinkAndDeviceId()` |

### 3.15 Pantallas destacadas

- **`SupervisedHomeScreen`**: solicita permisos al entrar (`LaunchedEffect`), genera el QR, y navega reactivamente a la llamada cuando `callState` es `RINGING/CONNECTING/ACTIVE` usando `activePeerId.ifEmpty { incomingPeerId }` (corrección I-002). El `BigRedButton` usa escala animada + feedback háptico (I-010).
- **`SupervisorHomeScreen`**: navega a la llamada en curso y muestra la tarjeta del dispositivo con estado en línea/fuera de línea.
- **`QrScannerScreen`**: CameraX (`Preview` + `ImageAnalysis`) con `ProcessCameraProvider`, análisis ML Kit en un `ExecutorService` dedicado, desbloquea con `QrLinkCodec.decode` y llama `onScanned(deviceId)` una sola vez (`processed` flag).
- **`CallScreen`**: `AndroidView` para los `SurfaceViewRenderer` remoto/local; video local arrastrable con `detectDragGestures` (I-012); temporizador `mm:ss` (I-011); overlay Aceptar/Rechazar cuando `RINGING` y `role == "supervisor"` (I-003).

---

## 4. Diagrama de flujo de la lógica principal

### 4.1 Llamada saliente (Supervisado → Supervisor)

```
[Usuario pulsa botón ALERTA]
        │  SupervisedHomeViewModel.callLinkedPeer()
        ▼
[CallCoordinator.startOutgoingCall(peerId, name)]
        │  guarda anti-duplicado → callState = DIALING → onForegroundStart() (CallService)
        │  signaling.sendCall(peerId)
        ▼
[Servidor enruta "call" al supervisor]
        │
        ▼
[Supervisor: handleEvent(IncomingCall)]
        │  callState = RINGING → onIncomingCall() → notificación
        │  UI navega a CallScreen → overlay Aceptar/Rechazar
        ▼
[Supervisor pulsa Aceptar → acceptIncomingCall()]
        │  signaling.sendCallAccepted(from)
        ▼
[Supervisado: handleEvent(CallAccepted)]
        │  startWebRtcAsInitiator() → createOffer() → sendOffer(peerId, sdp)
        ▼
[Supervisor: handleEvent(Offer)]
        │  startWebRtcAsReceiver() → setRemoteOffer(sdp) → createAnswer() → sendAnswer()
        ▼
[Ambos: intercambio de ice-candidate (trickle)]
        │
        ▼
[onIceConnectionChange == CONNECTED → callState = ACTIVE]
        │
        ▼
[Videollamada en curso (video + audio P2P)]
```

### 4.2 Llamada entrante auto-aceptada (Supervisor → Supervisado)

```
[Supervisor pulsa "Llamar"]
        │  startOutgoingCall() → sendCall()
        ▼
[Supervisado: handleEvent(IncomingCall)]
        │  mode == SUPERVISED → acceptIncomingCall() (auto-respuesta)
        │  onForegroundStart() → sendCallAccepted(from)
        ▼
[Supervisor: handleEvent(CallAccepted)]
        │  startWebRtcAsInitiator() → createOffer() → sendOffer()
        ▼
[Supervisado: handleEvent(Offer)]
        │  startWebRtcAsReceiver() → setRemoteOffer() → createAnswer() → sendAnswer()
        ▼
[Intercambio ICE → CONNECTED → ACTIVE]  (altavoz activado en supervisado)
```

### 4.3 Fin de llamada

```
[Cualquiera pulsa Colgar]
        │  CallViewModel.hangUp() → CallCoordinator.hangUp()
        │  signaling.sendEndCall(peerId) + cleanupCall()
        ▼
[cleanupCall()]
        │  webRtc.close() (cierra PeerConnection, libera cámara/mic)
        │  audio.restore()
        │  _callInProgress = false, _incomingPeerId/_activePeerId = ""
        │  onForegroundStop() (detiene CallService)
        ▼
[El otro teléfono: handleEvent(EndCall) → cleanupCall()]
```

### 4.4 Vinculación por QR

```
[Supervisado sin vínculo]
        │  SupervisedHomeViewModel.generateQr() → QrLinkCodec.encode(deviceId) → Bitmap (ZXing)
        ▼
[QR mostrado en pantalla]
        │
        ▼
[Supervisor: "Escanear QR" → QrScannerScreen (CameraX + ML Kit)]
        │  QrLinkCodec.decode(raw) → deviceId
        ▼
[Diálogo de nombre editable → supervisorVm.linkPeer(deviceId, name)]
        │  settings.saveLinkedPeer() + coordinator.updateLinkedPeer()
        ▼
[Vínculo persistido → ambos pueden llamarse]
```

---

## 5. Servidor Node.js (`server/index.js`)

### 5.1 Estructura

```js
const clients = new Map();            // deviceId -> WebSocket
const wss = new WebSocket.Server({ port: PORT });  // PORT = env.PORT || 3000
```

### 5.2 Flujo de mensajes

1. **`register`**: guarda `deviceId` (de `payload.deviceId` o `from`), reemplaza conexiones duplicadas, responde `registered` y difunde `peer-online`.
2. **Enrutamiento** (`to`): reenvía el mensaje al socket del destino. Si no existe, responde `peer-unavailable`.
3. **Sin destino**: devuelve el mensaje al propio emisor (errores genéricos).
4. **`close`**: elimina el `deviceId` del mapa y difunde `peer-offline`.

### 5.3 Protocolo JSON completo

Todos los mensajes tienen la forma `{ type, from, to, payload }`:

| type | Dirección | Propósito |
|------|-----------|-----------|
| `register` | cliente → servidor | Registrar `deviceId` |
| `registered` | servidor → cliente | Confirmación de registro |
| `call` | cliente → destino | Solicitud de llamada |
| `call-accepted` | destino → emisor | Aceptar llamada |
| `call-rejected` | destino → emisor | Rechazar llamada |
| `offer` | emisor → destino | SDP offer |
| `answer` | destino → emisor | SDP answer |
| `ice-candidate` | cliente → destino | Candidato ICE |
| `end-call` | cliente → destino | Colgar |
| `peer-unavailable` | servidor → emisor | Destino no registrado |
| `peer-online` | servidor → todos | Dispositivo registrado |
| `peer-offline` | servidor → todos | Dispositivo desconectado |
| `error` | servidor → cliente | Error genérico |

---

## 6. Guía de modificación y extensión

### 6.1 Agregar una pantalla nueva

1. Crea el composable en `ui/<feature>/<Pantalla>Screen.kt`.
2. Crea su `ViewModel` (heredando de `ViewModel`) y regístralo en `AppViewModelFactory`.
3. Añade la ruta en `Routes` y un bloque `composable(...)` en `NavGraph.kt`.
4. Añade los textos en `res/values/strings.xml` (la app es monolingüe en español).

### 6.2 Agregar un permiso nuevo

1. Declara el permiso en `AndroidManifest.xml`.
2. Si es de runtime, solicítalo con `rememberLauncherForActivityResult` en la pantalla correspondiente (ver patrón en `SupervisedHomeScreen` y `QrScannerScreen`).
3. Documenta el permiso en `README.md` y `docs/datasheet.md` (tabla de permisos).

### 6.3 Agregar una dependencia

1. Añade la versión y la librería en `gradle/libs.versions.toml`.
2. Referénciala en `app/build.gradle.kts` con `implementation(libs.<alias>)`.
3. Para dependencias del servidor, edita `server/package.json` y ejecuta `npm install`.

### 6.4 Cambiar el backend / servidor de señalización

1. El cliente se comunica solo a través de `SignalingClient`. Para cambiar el protocolo o el backend, modifica:
   - `SignalingTypes` (tipos de mensaje).
   - `SignalingMessage` (estructura del DTO).
   - `handleMessage()` en `SignalingClient` (mapeo JSON → `SignalingEvent`).
2. Actualiza el servidor en `server/index.js` para mantener el protocolo consistente.
3. Actualiza el puerto/IP por defecto en `app/build.gradle.kts` (`signalingServerUrl`) y en `res/xml/network_security_config.xml` si usas `ws://` con otra IP.

### 6.5 Cambiar la URL del servidor por defecto

- En `app/build.gradle.kts`:
  ```kotlin
  val signalingServerUrl = (project.findProperty("signalingServerUrl") as String?)
      ?: "ws://192.168.1.100:3000"
  ```
- O en `local.properties`: `signalingServerUrl=ws://<IP>:3000`.
- Si cambias la IP, recuerda actualizar `res/xml/network_security_config.xml`.

### 6.6 Añadir FCM (producción)

1. Añade la dependencia de Firebase Messaging.
2. Genera e incluye `google-services.json` (sin versionarlo; usa `secrets.properties`/`local.properties` para los secretos).
3. Implementa un servicio que despierte la app y dispare `CallCoordinator.handleEvent(IncomingCall)`.
4. Considera que el servidor deberá enviar un push (o integrar un gateway) cuando llegue un `call`.

### 6.7 Añadir TURN (producción)

1. Levanta un servidor TURN (p. ej. `coturn`) en un VPS.
2. Añade las credenciales TURN a la configuración ICE en `WebRtcManager.startPeerConnection()`:
   ```kotlin
   PeerConnection.IceServer("turn:turn.example.com:3478", "user", "pass")
   ```
3. Guarda las credenciales en `keystore.properties`/`secrets.properties` (nunca en el código).

### 6.8 Soporte para múltiples pares vinculados

1. Cambia `SettingsRepository` para persistir una lista de pares (o introduce Room).
2. Sustituye `linkedPeerId` por una colección en `CallCoordinator` y en los ViewModels de supervisor.
3. Actualiza `SupervisorHomeScreen` para renderizar la lista con un `LazyColumn`.

### 6.9 Reforzar la seguridad del vínculo

- Añade un token/secret al contenido del QR (en `QrLinkCodec`) y valídalo en el servidor (`server/index.js`) verificando que `from` coincida con el `deviceId` registrado.

---

## 7. Referencia rápida de componentes

| Componente | Ruta | Tipo | Rol |
|------------|------|------|-----|
| `AlertaMayorApp` | `AlertaMayorApp.kt` | `Application` | Arranque + DI + canales |
| `MainActivity` | `MainActivity.kt` | `ComponentActivity` | Entry point + NavGraph |
| `AppContainer` | `di/AppContainer.kt` | Clase | DI manual |
| `SettingsRepository` | `data/local/` | Repositorio | DataStore Preferences |
| `SignalingClient` | `data/signaling/` | Cliente WS | Señalización + reconexión |
| `SignalingMessage` | `data/signaling/` | DTO | Mensaje JSON |
| `SignalingTypes` | `data/signaling/` | Constantes | Tipos de mensaje |
| `SignalingEvent` | `data/signaling/` | sealed interface | Eventos emitidos |
| `AppMode` | `domain/model/` | enum | SUPERVISED / SUPERVISOR |
| `CallState` | `domain/model/` | enum | Estado de llamada |
| `DeviceInfo` | `domain/model/` | data class | Dispositivo vinculado |
| `QrLinkCodec` | `domain/` | object | Codifica/decodifica QR |
| `CallCoordinator` | `webrtc/` | Singleton | Orquestación |
| `WebRtcManager` | `webrtc/` | Singleton | Sesión WebRTC |
| `AudioSpeakerController` | `webrtc/` | Singleton | Altavoz |
| `CallService` | `service/` | `LifecycleService` | Foreground Service |
| `NotificationHelper` | `service/` | object | Notificaciones |
| `AlertaMayorNavGraph` | `ui/navigation/` | Composable | Grafo de navegación |
| `AppViewModelFactory` | `ui/` | Factory | DI de ViewModels |
| ViewModels | `ui/*/` | `ViewModel` | Estado + acciones |
| Screens | `ui/*/` | Composable | UI |
| `server/index.js` | `server/` | Node.js | Servidor de señalización |

---

## 8. Convenciones y notas para el desarrollador

- **Idioma**: todos los textos de UI en `res/values/strings.xml` (español). No usar literales en los composables.
- **Color/theme**: paleta en `ui/theme/Color.kt` y `res/values/colors.xml` (alto contraste, tipografía grande).
- **Corrutinas**: `viewModelScope` en ViewModels; `CallCoordinator` usa su propio scope con `SupervisorJob + Dispatchers.Main`.
- **No bloquear el hilo principal**: el QR se genera en `Dispatchers.Default`; la red usa OkHttp/corutinas.
- **Secretos**: nunca hardcodear; usar `local.properties`, `keystore.properties`, `secrets.properties` (ignorados por git).
- **WebRTC**: fork `com.infobip:google-webrtc:1.0.47617t` (paquete `org.webrtc.*`), versión fijada.
- **Requisito de red local**: `networkSecurityConfig` permite `ws://` solo en hosts locales (`192.168.1.100`, `192.168.1.101`, `10.0.2.2`, `localhost`).
