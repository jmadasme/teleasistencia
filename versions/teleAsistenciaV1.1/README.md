# Alerta Mayor

**Aplicación Android de teleasistencia para adultos mayores, con videollamada WebRTC en tiempo real y vinculación por código QR.**

`Alerta Mayor` es una aplicación Android nativa diseñada para que una persona mayor pueda contactar con su familiar o cuidador con **un solo toque de un botón rojo**. La app inicia automáticamente una **videollamada en altavoz** (manos libres) hacia el teléfono del hijo/familiar, sin necesidad de que el adulto mayor toque nada más: la llamada entrante se responde sola.

La misma app incluye **dos modos de uso** intercambiables:

| Modo | Rol | Qué hace |
|------|-----|----------|
| **Modo Supervisado** | Adulto mayor (padre/madre) | Muestra un gran **botón rojo de ALERTA** que inicia la videollamada en altavoz. Muestra un **código QR de vinculación** para que el familiar se enlace. Responde automáticamente las llamadas entrantes. |
| **Modo Supervisor** | Familiar / cuidador (hijo) | **Escanea el QR** del teléfono supervisado para vincularse. Puede llamar en cualquier momento. Recibe **notificación de llamada entrante** con botones **Aceptar / Rechazar**. |

La comunicación de audio y video punto a punto se realiza con **WebRTC**. El intercambio inicial de señales (SDP e ICE) se hace a través de un **servidor de señalización Node.js** (`server/`) que se ejecuta en un PC Linux local para las pruebas.

> **Contexto técnico resumido**
> - **Plataforma**: Android (nativo)
> - **Lenguaje**: Kotlin 2.0.21
> - **UI**: Jetpack Compose + Material 3 (Compose BOM 2025.01.01)
> - **minSdk**: 24 (Android 7.0) · **targetSdk / compileSdk**: 35 (Android 15)
> - **Package / applicationId**: `com.alertamayor.app`
> - **Nombre de la app**: `Alerta Mayor`
> - **Idioma**: español
> - **Arquitectura**: MVVM + ViewModel + StateFlow, inyección de dependencias manual (`AppContainer`)

---

## Tabla de contenidos

1. [¿Qué es Alerta Mayor?](#qué-es-alerta-mayor)
2. [Principio de funcionamiento](#principio-de-funcionamiento)
3. [Pantallas y flujo principal](#pantallas-y-flujo-principal)
4. [Forma de operar (guía de uso)](#forma-de-operar-guía-de-uso)
5. [Requisitos](#requisitos)
6. [Permisos solicitados](#permisos-solicitados)
7. [Arquitectura](#arquitectura)
8. [Instrucciones de compilación e instalación](#instrucciones-de-compilación-e-instalación)
9. [Servidor de señalización](#servidor-de-señalización)
10. [Ejemplos de uso realistas](#ejemplos-de-uso-realistas)
11. [Advertencias y precauciones](#advertencias-y-precauciones)
12. [Documentación adicional](#documentación-adicional)

---

## ¿Qué es Alerta Mayor?

Alerta Mayor es una solución de **teleasistencia** que aprovecha el teléfono que el adulto mayor ya posee para conectarlo con su red de apoyo. A diferencia de los dispositivos de teleasistencia tradicionales (botón de pánico con central receptora), Alerta Mayor establece una **videollamada directa entre dos teléfonos**, permitiendo al familiar **ver y oír** a la persona mayor al instante.

Características principales:

- **Botón rojo gigante** (220 dp) con texto "ALERTA", pensado para dedos con movilidad reducida o visión disminuida.
- **Auto-respuesta en altavoz**: cuando el hijo llama, el teléfono del adulto mayor contesta solo, activa el altavoz y enciende la cámara, sin que la persona mayor toque nada.
- **Vinculación por QR**: sin cuentas, sin contraseñas, sin servidores de autenticación. El hijo escanea el QR que muestra el teléfono del padre y quedan enlazados.
- **Videollamada P2P (WebRTC)**: una vez establecida la señalización, el audio y el video viajan **directamente de teléfono a teléfono**, con baja latencia.
- **Notificación de llamada entrante** en el modo supervisor, con botones de aceptar/rechazar.
- **Configuración simple**: URL del servidor de señalización editable, cambio de modo, y restablecimiento del vínculo.

---

## Principio de funcionamiento

El sistema se compone de **dos partes** que cooperan:

```
┌─────────────────────┐        WebSocket (JSON)         ┌──────────────────────┐
│  Teléfono A         │  ────────────────────────────▶  │  Servidor de         │
│  (Supervisado)      │   register / call / SDP / ICE   │  señalización (PC)   │
│                     │  ◀────────────────────────────  │  Node.js + ws :3000  │
└─────────────────────┘                                 └──────────────────────┘
        │                                                        │
        │             WebRTC (audio/video P2P)                   │
        └────────────────────────────────────────────────────────┤
                                                                 │
┌─────────────────────┐                                          │
│  Teléfono B         │  ◀───────────────────────────────────────┘
│  (Supervisor)       │   WebSocket (JSON)
└─────────────────────┘
```

### 1. Vinculación por QR

1. El teléfono **supervisado** genera un `deviceId` (UUID) persistido en DataStore en la primera ejecución.
2. Ese `deviceId` se codifica en un **código QR** con un esquema propio: `alertamayor:link?deviceId=<uuid>` (y acepta retrocompatibilidad con JSON plano `{"deviceId":"..."}`).
3. El teléfono **supervisor** escanea el QR (CameraX + ML Kit Barcode Scanning, modelo *bundled* que funciona sin conexión ni Play Services).
4. El supervisor guarda el `deviceId` como "par vinculado" (con un nombre editable). A partir de ese momento ambos pueden llamarse.

> El vínculo es **unidireccional en datos**: el supervisor guarda el ID del supervisado. La llamada, una vez establecida, es **bidireccional** (audio y video en ambos sentidos).

### 2. Señalización (WebSocket)

Ambos teléfonos se conectan al servidor de señalización Node.js mediante **WebSocket** (cliente OkHttp). El protocolo JSON intercambia mensajes del tipo `register`, `call`, `call-accepted`, `call-rejected`, `offer`, `answer`, `ice-candidate`, `end-call`, etc. El servidor **solo enruta mensajes**: no ve ni transporta el audio/video.

### 3. Videollamada (WebRTC)

Una vez que el destinatario acepta (o auto-acepta), los dos teléfonos negocian la sesión:

1. El **iniciador** crea una `PeerConnection`, genera una **oferta SDP** (`offer`) y la envía por señalización, junto con candidatos **ICE** en modo *trickle*.
2. El **receptor** aplica la oferta remota, genera una **respuesta SDP** (`answer`) y la devuelve.
3. Cuando el estado ICE llega a `CONNECTED`, comienza la transmisión de audio y video **directamente entre los teléfonos**.
4. Para descubrir direcciones públicas/NAT se usa un servidor **STUN** (`stun:stun.l.google.com:19302`). En la misma red WiFi no suele ser necesario un **TURN**.

### 4. Auto-respuesta y notificaciones

- En **modo Supervisado**, una llamada entrante se **auto-acepta**: se abre la pantalla de llamada, se activa el **altavoz** y se enciende la cámara sin interacción.
- En **modo Supervisor**, una llamada entrante marca el estado `RINGING`, emite una **notificación** de alta prioridad y muestra una pantalla con **Aceptar / Rechazar**.

---

## Pantallas y flujo principal

| Pantalla | Ruta de navegación | Descripción |
|----------|--------------------|-------------|
| **Onboarding / Selección de modo** | `onboarding` | Primera ejecución: elige entre "Modo Supervisado" y "Modo Supervisor". El modo se guarda y no vuelve a preguntar (cambiable en Configuración). |
| **Pantalla Supervisada** | `supervisedHome` | Botón rojo gigante "ALERTA" (si hay vínculo) o código QR de vinculación (si no lo hay). Indicador de estado (conectado/sin vincular/vinculado con `<nombre>`). |
| **Pantalla Supervisor** | `supervisorHome` | Botón "Escanear QR para vincular", lista de dispositivos vinculados con estado en línea/fuera de línea y botón "Llamar". |
| **Escáner QR** | `qrScanner` | Cámara trasera con análisis de códigos de barras (ML Kit). Al detectar un QR válido devuelve el `deviceId`. |
| **Pantalla de llamada** | `call/{role}/{peerId}` | Video remoto a pantalla completa, video local en Picture-in-Picture arrastrable, temporizador `mm:ss` y controles (colgar, silenciar, altavoz, cambiar cámara). En modo supervisor, overlay de Aceptar/Rechazar al sonar. |
| **Configuración** | `settings` | Cambiar modo, editar URL del servidor, ver ID del dispositivo, ver/restablecer vínculo. |

El grafo de navegación está definido en `ui/navigation/NavGraph.kt` con **Jetpack Navigation Compose**.

---

## Forma de operar (guía de uso)

### Preparación (una sola vez)

1. Enciende un **PC Linux** en la misma red WiFi que los dos teléfonos y levanta el servidor de señalización (ver [Servidor de señalización](#servidor-de-señalización)).
2. Anota la **IP del PC** (por ejemplo `192.168.1.100`).

### Configurar el teléfono del adulto mayor (Modo Supervisado)

1. Instala la app en el teléfono del adulto mayor.
2. Al abrir por primera vez, pulsa **"Modo Supervisado"** ("Soy el adulto mayor / necesito ayuda").
3. La app solicita permisos de **cámara, micrófono y notificaciones**. Acéptalos (son necesarios para la videollamada).
4. Si el servidor no está en `192.168.1.100`, ve a **Configuración → URL del servidor** e introduce `ws://<IP_DEL_PC>:3000`.
5. La pantalla muestra un **código QR grande**. Déjalo visible para el siguiente paso.

### Configurar el teléfono del familiar (Modo Supervisor)

1. Instala la app en el teléfono del familiar.
2. Al abrir por primera vez, pulsa **"Modo Supervisor"** ("Soy familiar / cuidador").
3. Pulsa **"Escanear QR para vincular"** y apunta la cámara al QR del teléfono del adulto mayor.
4. Escribe un **nombre** para el dispositivo (por ejemplo "Mamá") y pulsa **Guardar**.
5. El dispositivo aparece en la lista con el estado **En línea** (si el otro teléfono está conectado).

### Hacer una llamada

- **Desde el adulto mayor**: pulsa el **botón rojo "ALERTA"**. La videollamada se inicia en altavoz automáticamente.
- **Desde el familiar**: pulsa el botón **"Llamar"** en la tarjeta del dispositivo vinculado. El teléfono del adulto mayor **contestará solo**, en altavoz, sin que toque nada.

### Recibir una llamada

- **Adulto mayor (supervisado)**: la llamada se **responde sola** y se abre en pantalla completa con altavoz.
- **Familiar (supervisor)**: suena el teléfono, aparece una **notificación de llamada entrante** y la pantalla muestra **Aceptar / Rechazar**.

### Durante la llamada

- **Colgar**: botón rojo con el icono de teléfono.
- **Silenciar micrófono**: icono de micrófono.
- **Altavoz**: icono de volumen (activado por defecto en el modo supervisado).
- **Cambiar cámara** (frontal/trasera): icono de cámara.
- El **video local** (tu propia imagen) se puede **arrastrar** por la pantalla.

### Cambiar de modo o restablecer el vínculo

Ve a **Configuración** (icono de engranaje):
- **Modo de la app**: cambia entre Supervisado y Supervisor.
- **URL del servidor**: edita la IP del PC de señalización.
- **Restablecer vínculo**: borra el par vinculado y regenera el `deviceId` (requiere volver a escanear el QR).

---

## Requisitos

### Para compilar y ejecutar la app Android

| Componente | Versión mínima | Notas |
|------------|----------------|-------|
| **JDK** | 17+ (recomendado 21) | El proyecto compila con `sourceCompatibility`/`jvmTarget` = 17. Con JDK 21 funciona sin cambios. |
| **Android SDK** | `compileSdk 35` instalado | Platform `android-35` y Build Tools `35.0.0`. |
| **Gradle** | 8.13 | Se usa el **wrapper** (`./gradlew`), no hace falta instalar Gradle global. |
| **AGP** | 8.9.2 | Se descarga automáticamente vía Gradle. |
| **Kotlin** | 2.0.21 | Incluido en el build. |
| **Teléfono de prueba** | Android 7.0+ (API 24+) | Con **Depuración USB** activada para instalar por `adb`. |

### Para el servidor de señalización

| Componente | Versión mínima | Notas |
|------------|----------------|-------|
| **Node.js** | >= 18 | Solo para `server/`. |
| **npm** | >= 9 | Incluido con Node.js. |

> **Regla de compatibilidad validada**: `AGP 8.9.2 ↔ Gradle 8.13 ↔ Kotlin 2.0.21 ↔ compileSdk 35 ↔ JDK 17+`. Esta combinación está probada en el entorno de desarrollo.

---

## Permisos solicitados

La app declara los siguientes permisos en `app/src/main/AndroidManifest.xml`. El pedido **en tiempo de ejecución** se hace justo antes de la primera acción que lo requiere (entrar a la llamada o escanear el QR), con una explicación amable.

| Permiso | Uso en la app | ¿Runtime? |
|---------|---------------|:---------:|
| `android.permission.INTERNET` | Conexión WebSocket al servidor de señalización y tráfico WebRTC/STUN. | No |
| `android.permission.ACCESS_NETWORK_STATE` | Detectar conectividad y cambios de red. | No |
| `android.permission.CAMERA` | Captura de video en la videollamada y escaneo del código QR. | **Sí** |
| `android.permission.RECORD_AUDIO` | Captura del micrófono en la videollamada. | **Sí** |
| `android.permission.MODIFY_AUDIO_SETTINGS` | Activar el modo comunicación y el altavoz (`AudioManager`). | No (normal) |
| `android.permission.WAKE_LOCK` | Mantener el dispositivo despierto durante la llamada. | No (normal) |
| `android.permission.FOREGROUND_SERVICE` | Mantener la llamada WebRTC activa en segundo plano. | No (normal) |
| `android.permission.FOREGROUND_SERVICE_CAMERA` | Requerido en Android 14+ para el servicio en primer plano con cámara. | No (normal) |
| `android.permission.FOREGROUND_SERVICE_MICROPHONE` | Requerido en Android 14+ para el servicio en primer plano con micrófono. | No (normal) |
| `android.permission.BLUETOOTH` (`maxSdkVersion=30`) | Compatibilidad con manos libres/auriculares (legacy). | No |
| `android.permission.BLUETOOTH_CONNECT` | Conexión a auriculares Bluetooth (Android 12+). | **Sí** (Android 12+) |
| `android.permission.VIBRATE` | Feedback háptico al pulsar el botón rojo. | No (normal) |
| `android.permission.POST_NOTIFICATIONS` | Notificación de llamada entrante (Android 13+). | **Sí** (Android 13+) |

> **Nota sobre `BLUETOOTH_CONNECT`**: está declarado para el enrutamiento de audio a auriculares/manos libres, pero en esta fase no se solicita activamente en runtime; el flujo principal de altavoz no depende de él.

---

## Arquitectura

La app sigue **MVVM** (Model-View-ViewModel) con **UI declarativa en Jetpack Compose**. La inyección de dependencias es **manual** mediante un `AppContainer` (plan de menor riesgo frente a Hilt/KSP).

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

### Componentes principales

| Componente | Paquete | Responsabilidad |
|------------|---------|-----------------|
| `AlertaMayorApp` | `com.alertamayor.app` | Clase `Application`. Crea el `AppContainer` y los canales de notificación. |
| `MainActivity` | `com.alertamayor.app` | Activity única. Conecta la señalización y monta el grafo Compose. |
| `AppContainer` | `di` | Contenedor de DI manual. Expone `settingsRepository`, `webRtcManager`, `audioSpeakerController` y `callCoordinator`. |
| `CallCoordinator` | `webrtc` | Singleton que coordina la señalización y la sesión WebRTC. Expone `StateFlow` de estado de conexión, llamada, silencio, altavoz, presencia. |
| `WebRtcManager` | `webrtc` | Gestiona `PeerConnectionFactory`, captura de cámara (Camera2) y micrófono, generación de offer/answer y candidatos ICE. |
| `AudioSpeakerController` | `webrtc` | Activa/restaura el modo comunicación y el altavoz mediante `AudioManager`. |
| `SignalingClient` | `data.signaling` | Cliente WebSocket (OkHttp) con reconexión automática y serialización JSON (kotlinx.serialization). |
| `SettingsRepository` | `data.local` | Persistencia con DataStore Preferences (deviceId, modo, vínculo, URL del servidor). |
| `CallService` | `service` | `LifecycleService` en primer plano (`camera|microphone`) que mantiene viva la llamada en segundo plano. |
| `NotificationHelper` | `service` | Crea el canal de notificación y las notificaciones de llamada en curso / entrante. |
| `QrLinkCodec` | `domain` | Codifica/decodifica el contenido del QR de vinculación. |

### Manejo de estado

- **`StateFlow`** como fuente única de verdad, recolectado con `collectAsStateWithLifecycle()`.
- **`CallState`** enum: `IDLE`, `DIALING`, `RINGING`, `CONNECTING`, `ACTIVE`, `ENDED`.
- Los singletons (`CallCoordinator`, `WebRtcManager`) sobreviven a la rotación y al cierre de pantalla; la llamada continúa en un **Foreground Service**.

### Persistencia (DataStore Preferences)

Claves guardadas en `alertamayor_settings`: `device_id`, `device_name`, `mode`, `linked_peer_id`, `linked_peer_name`, `signaling_server_url`, `onboarding_done`.

---

## Instrucciones de compilación e instalación

### 1. Configurar las variables de entorno

```bash
# Variables de entorno (ajusta la ruta si tu SDK está en otro sitio)
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Verifica que adb está disponible
adb version
```

### 2. Compilar el APK de depuración

```bash
# Desde la raíz del proyecto (PROYECTO_DIR)
./gradlew assembleDebug
```

El APK se genera en:

```
app/build/outputs/apk/debug/app-debug.apk
```

### 3. Instalar en un teléfono (Depuración USB activada)

```bash
# Instalar (sobrescribe si ya está instalado)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Lanzar la app
adb shell am start -n com.alertamayor.app/com.alertamayor.app.MainActivity
```

### 4. Instalar en DOS teléfonos (padre e hijo)

Conecta ambos dispositivos por USB y usa el **número de serie** de cada uno:

```bash
# Listar dispositivos conectados y sus seriales
adb devices

# Instalar en cada teléfono por su serial
adb -s <SERIAL_TEL_1> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <SERIAL_TEL_2> install -r app/build/outputs/apk/debug/app-debug.apk
```

> Ejemplo con los dispositivos del entorno de pruebas:
> - Huawei STK-LX3 → modo **Supervisor** (hijo)
> - Samsung SM-A715F → modo **Supervisado** (padre)

### 5. Configurar la URL del servidor (si el PC no está en `192.168.1.100`)

La URL por defecto es `ws://192.168.1.100:3000`. Puedes cambiarla de dos formas:

- **Desde la app**: Configuración → URL del servidor.
- **En tiempo de compilación**: añade a `local.properties`:

```properties
signalingServerUrl=ws://192.168.1.50:3000
```

---

## Servidor de señalización

El servidor es un **WebSocket minimalista en Node.js + `ws`** ubicado en `server/`. Registra dispositivos por `deviceId` y enruta los mensajes SDP/ICE entre pares vinculados.

### Levantar el servidor

```bash
# Desde el directorio server/ del proyecto
cd server
npm install
node index.js
```

Por defecto escucha en `0.0.0.0:3000`. Puedes cambiar el puerto con la variable de entorno `PORT`:

```bash
PORT=8080 node index.js
```

### Requisitos de red

- El **PC Linux** y los **dos teléfonos** deben estar en la **misma red WiFi**.
- El **firewall** del PC debe permitir conexiones entrantes en el puerto 3000 (TCP). Ejemplo con `ufw`:

```bash
sudo ufw allow 3000/tcp
```

### Protocolo JSON (resumen)

Cada mensaje es un objeto JSON con la forma `{ "type", "from", "to", "payload" }`. Tipos principales: `register`, `registered`, `call`, `call-accepted`, `call-rejected`, `offer`, `answer`, `ice-candidate`, `end-call`, `peer-unavailable`, `peer-online`, `peer-offline`, `error`. El detalle completo está en `server/README.md`.

---

## Ejemplos de uso realistas

### Ejemplo 1 — Primer arranque y vinculación

> **María (82 años)** recibe un teléfono con Alerta Mayor. Su hijo **Javier** instala la app en su propio teléfono.
>
> 1. María abre la app y pulsa **"Modo Supervisado"**. Acepta los permisos.
> 2. El teléfono de María muestra un **QR grande** en pantalla.
> 3. Javier abre la app, pulsa **"Modo Supervisor"**, luego **"Escanear QR para vincular"** y apunta al QR de María.
> 4. Javier escribe el nombre **"Mamá"** y pulsa **Guardar**. En su lista aparece "Mamá — En línea".

### Ejemplo 2 — Alerta desde el adulto mayor

> María se siente mal por la noche. Pulsa el **botón rojo "ALERTA"** de su teléfono.
>
> 1. El teléfono de María vibra ligeramente (feedback háptico) y muestra "Llamando…".
> 2. El teléfono de Javier suena, muestra la **notificación de llamada entrante** y abre la pantalla con **Aceptar / Rechazar**.
> 3. Javier pulsa **Aceptar** y en pocos segundos ve y oye a María a pantalla completa.

### Ejemplo 3 — Llamada del familiar (auto-respuesta)

> Javier quiere comprobar que su madre está bien. Abre la app y pulsa **"Llamar"**.
>
> 1. El teléfono de María **contesta automáticamente**: se abre la pantalla de llamada, se enciende la cámara y el **altavoz**.
> 2. María oye a su hijo sin tocar nada y puede hablar con él en manos libres.

### Ejemplo 4 — Fin de la llamada

> Cualquiera de los dos pulsa el **botón rojo de colgar**. La llamada finaliza, se libera la cámara y el micrófono, y se detiene el servicio en primer plano. Ambos vuelven a su pantalla principal.

### Ejemplo 5 — Cambiar el servidor

> Javier cambia de router y el PC de señalización ahora tiene la IP `192.168.1.50`. En ambos teléfonos va a **Configuración → URL del servidor**, escribe `ws://192.168.1.50:3000` y pulsa **Guardar**. La app reconecta automáticamente.

---

## Advertencias y precauciones

- **Sin FCM (push)**: esta fase **no** integra Firebase Cloud Messaging. Para recibir una llamada entrante, la app debe estar **abierta o en primer plano** (o con el Foreground Service activo). En producción, si se requiere recibir llamadas con la app cerrada, es necesario integrar **FCM**.
- **Misma red WiFi**: las pruebas asumen que ambos teléfonos y el PC de señalización están en la **misma red local**. Para llamadas entre redes distintas o tras NAT estricto se necesita un servidor **TURN** (p. ej. `coturn` en un VPS). Documentado, no implementado en esta fase.
- **`ws://` en lugar de `wss://`**: la señalización usa WebSocket **sin cifrar** porque el servidor es local. En producción debe usarse `wss://` (TLS) y, preferiblemente, un dominio.
- **APK debug vs release**: el APK generado con `assembleDebug` está **firmado con la clave de depuración**. No lo distribuyas públicamente. Para producción se debe generar un APK/AAB firmado con un *keystore* propio (nunca lo subas al repositorio; usa `keystore.properties`/`secrets.properties`).
- **Secretos**: **nunca** incluyas API keys, keystores o contraseñas en el código ni en el repositorio. Usa `local.properties`, `keystore.properties` y `secrets.properties` (todos ignorados por `.gitignore`).
- **Permisos en runtime**: si el usuario deniega cámara/micrófono, la videollamada no podrá transmitir audio/video. El flujo de solicitud no produce cierres (crash), pero conviene revisar los permisos en Configuración.
- **Depuración USB**: para instalar con `adb`, el teléfono debe tener **Depuración USB** activada (Opciones de desarrollador). Además, confirma el diálogo de autorización al conectar.
- **`networkSecurityConfig`**: el tráfico en claro (`ws://`) solo está permitido para hosts locales específicos (`192.168.1.100`, `192.168.1.101`, `10.0.2.2`, `localhost`). Si cambias la IP del servidor, deberás ajustar `res/xml/network_security_config.xml` o usar `https`.
- **Un solo par vinculado**: esta fase soporta un único dispositivo supervisado por supervisor (lista de uno). La arquitectura permite escalar a N pares.
- **Suplantación de `from`**: el servidor no valida que el campo `from` coincida con el `deviceId` registrado. En una fase posterior se debería añadir un token/secret al QR.

---

## Documentación adicional

- **`docs/datasheet.md`** — Documentación de usuario detallada y especificaciones técnicas.
- **`docs/developer.md`** — Explicación del código fuente, arquitectura interna y guía de extensión.
- **`server/README.md`** — Cómo levantar el servidor de señalización y el protocolo JSON.
- **`PLAN.md`** — Plan de implementación completo (requerimientos, diseño técnico, casos de prueba).
- **`AUDIT_REPORT.md`** — Informe de auditoría del ciclo 1 (estado de las incidencias corregidas).

---

## Funciones adicionales (ciclo 6-7)

### Selector de tema (claro / oscuro / sistema)

La pantalla de **Configuración** (engrane) incluye una sección **Tema** con tres opciones:

- **Sistema**: sigue el tema del teléfono (claro u oscuro según la configuración del sistema).
- **Claro**: fuerza el tema claro.
- **Oscuro**: fuerza el tema oscuro (fondo oscuro de alto contraste, útil de noche).

El cambio se aplica **en caliente** (sin reiniciar la app) y queda guardado. La pantalla de alerta y la configuración respetan el tema seleccionado.

### Indicador de conexión a internet en modo alerta

En la pantalla supervisada (modo alerta), arriba a la izquierda se muestra un icono que indica el estado de conexión a internet:

| Estado | Icono | Descripción |
|---|---|---|
| Wi-Fi conectado | 📶 | "Conectado por Wi-Fi" |
| Datos móviles | 📱 | "Conectado por datos móviles" |
| Sin internet | 🚫 | "Sin conexión a internet" |

La app **verifica la conexión continuamente** (cada ~4 segundos + callbacks de red). Si en **cualquier momento** se pierde internet (por Wi-Fi o por datos móviles):

- El icono cambia a "Sin conexión a internet".
- La **campanilla de ALERTA se pone gris con una barra diagonal roja**, indicando que está **inhabilitada** (no se puede llamar sin conexión).
- Si se pulsa el botón en ese estado, **no se inicia ninguna llamada**.

Cuando la conexión se recupera (Wi-Fi o datos móviles), la campanilla vuelve a **roja y funcional** automáticamente.

> **Advertencia**: la llamada de emergencia requiere conexión a internet (Wi-Fi o datos móviles). Si el teléfono pierde toda conexión, la campanilla se desactiva y **no se puede realizar la llamada** hasta recuperar internet. Para un uso de emergencia crítico, asegúrate de que el teléfono tenga al menos una vía de conexión disponible.
