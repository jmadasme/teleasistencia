# Datasheet — Alerta Mayor

Documentación de usuario detallada y especificaciones técnicas de la aplicación **Alerta Mayor**.

---

## 1. Ficha técnica del producto

| Campo | Valor |
|-------|-------|
| **Nombre de la app** | Alerta Mayor |
| **Categoría** | Teleasistencia / salud / comunicación |
| **Tipo de aplicación** | Android nativa |
| **Lenguaje** | Kotlin 2.0.21 |
| **Interfaz de usuario** | Jetpack Compose (Material 3) |
| **Package / applicationId** | `com.alertamayor.app` |
| **Namespace** | `com.alertamayor.app` |
| **versionCode** | 1 |
| **versionName** | 0.1.0 |
| **minSdk** | 24 (Android 7.0 Nougat) |
| **targetSdk / compileSdk** | 35 (Android 15) |
| **Build Tools** | 35.0.0 |
| **Orientación** | Retrato (bloqueada en el manifest) |
| **Idioma** | Español |
| **Tema** | Claro, alto contraste, tipografía grande (accesibilidad) |
| **Arquitectura** | MVVM + ViewModel + StateFlow |
| **Inyección de dependencias** | Manual (`AppContainer`) |
| **Transporte de señalización** | WebSocket (OkHttp) → servidor Node.js |
| **Transporte de medios** | WebRTC P2P (audio + video) |

---

## 2. Especificaciones técnicas

### 2.1 Toolchain de compilación

| Componente | Versión | Nota |
|------------|---------|------|
| JDK | 21 (instalado) | `sourceCompatibility`/`jvmTarget` = 17 |
| Gradle | 8.13 | vía wrapper (`./gradlew`) |
| Android Gradle Plugin (AGP) | 8.9.2 | `com.android.application` |
| Kotlin | 2.0.21 | plugins `kotlin.android`, `kotlin.compose`, `kotlin.serialization` |
| Compose BOM | 2025.01.01 | Material 3 |
| compileSdk / targetSdk | 35 | platform `android-35` |
| minSdk | 24 | Android 7.0 |

### 2.2 Dependencias de la app Android

| Librería | Versión | Propósito |
|----------|---------|-----------|
| `androidx.core:core-ktx` | 1.15.0 | API de core AndroidX |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 | Ciclo de vida / corrutinas |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.7 | `collectAsStateWithLifecycle()` |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | `viewModel()` en Compose |
| `androidx.lifecycle:lifecycle-service` | 2.8.7 | `LifecycleService` para `CallService` |
| `androidx.activity:activity-compose` | 1.9.3 | `ComponentActivity` Compose |
| `androidx.compose:compose-bom` | 2025.01.01 | BOM de Compose |
| `androidx.compose.ui:*` | (BOM) | UI declarativa |
| `androidx.compose.material3:material3` | (BOM) | Componentes Material 3 |
| `androidx.compose.material:material-icons-extended` | (BOM) | Iconos (teléfono, cámara, altavoz…) |
| `androidx.navigation:navigation-compose` | 2.8.5 | Navegación |
| `androidx.datastore:datastore-preferences` | 1.1.1 | Persistencia de configuración/vínculo |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | Corrutinas |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.3 | Serialización de mensajes de señalización |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | Cliente WebSocket de señalización |
| `com.infobip:google-webrtc` | 1.0.47617t | WebRTC (fork de terceros, versión fijada) |
| `com.google.zxing:core` | 3.5.3 | Generación del QR (Bitmap) |
| `com.google.mlkit:barcode-scanning` | 17.3.0 | Escaneo de QR (modelo *bundled*, offline) |
| `androidx.camera:camera-camera2` | 1.4.1 | CameraX (escaneo de QR) |
| `androidx.camera:camera-lifecycle` | 1.4.1 | CameraX (ciclo de vida) |
| `androidx.camera:camera-view` | 1.4.1 | `PreviewView` (escaneo de QR) |

> **Nota sobre WebRTC**: se usa el fork `com.infobip:google-webrtc` (versión exacta `1.0.47617t`) en lugar de `org.webrtc:google-webrtc`. El paquete Java sigue siendo `org.webrtc.*`.

### 2.3 Dependencias del servidor de señalización

| Librería | Versión | Propósito |
|----------|---------|-----------|
| `ws` | ^8.18.0 | Servidor WebSocket |
| Node.js | >= 18 | Runtime |

### 2.4 Gestión de dependencias

- **Android**: catálogo de versiones Gradle en `gradle/libs.versions.toml`. Los repositorios son `google()` y `mavenCentral()` (definidos en `settings.gradle.kts`).
- **Servidor**: `npm` con `server/package.json` y `server/package-lock.json`. La carpeta `server/node_modules/` está excluida del control de versiones.

---

## 3. Requisitos del sistema

### 3.1 Dispositivo Android

- **Versión mínima**: Android 7.0 (API 24).
- **Versión objetivo**: Android 15 (API 35).
- **Cámara frontal y trasera** (se usan ambas; la trasera para el QR y por defecto para la llamada).
- **Micrófono**.
- **Conexión WiFi** (para la señalización y la transmisión WebRTC en las pruebas locales).
- **Notificaciones** habilitadas (recomendado en Android 13+).

### 3.2 Entorno de desarrollo

- JDK 17+ (recomendado 21).
- Android SDK con platform `android-35` y Build Tools `35.0.0`.
- Gradle 8.13 (incluido vía wrapper).
- ADB para la instalación.
- Node.js >= 18 y npm para el servidor de señalización.

---

## 4. Permisos solicitados

| Permiso | Uso | Runtime |
|---------|-----|:-------:|
| `INTERNET` | WebSocket de señalización + tráfico WebRTC/STUN | No |
| `ACCESS_NETWORK_STATE` | Detección de conectividad | No |
| `CAMERA` | Video en llamada + escaneo de QR | **Sí** |
| `RECORD_AUDIO` | Micrófono en la llamada | **Sí** |
| `MODIFY_AUDIO_SETTINGS` | Altavoz / modo comunicación | No |
| `WAKE_LOCK` | Mantener despierto en llamada | No |
| `FOREGROUND_SERVICE` | Llamada en segundo plano | No |
| `FOREGROUND_SERVICE_CAMERA` | Foreground service con cámara (Android 14+) | No |
| `FOREGROUND_SERVICE_MICROPHONE` | Foreground service con micrófono (Android 14+) | No |
| `BLUETOOTH` (`maxSdk=30`) | Manos libres legacy | No |
| `BLUETOOTH_CONNECT` | Auriculares Bluetooth (Android 12+) | **Sí** (12+) |
| `VIBRATE` | Feedback háptico del botón rojo | No |
| `POST_NOTIFICATIONS` | Notificación de llamada entrante (Android 13+) | **Sí** (13+) |

---

## 5. Guía de configuración del entorno

### 5.1 Instalar el Android SDK

1. Descarga **Android Studio** o las **command line tools** desde el sitio oficial de Android.
2. Instala los paquetes necesarios:

```bash
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

3. Configura las variables de entorno:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

### 5.2 Instalar el JDK

```bash
# Ejemplo en Ubuntu/Debian
sudo apt install openjdk-21-jdk

# Verificar
java -version   # debe mostrar 17 o superior
```

### 5.3 Gradle

No es necesario instalarlo manualmente: el **wrapper** (`./gradlew`) descarga Gradle 8.13 automáticamente en la primera ejecución.

### 5.4 Instalar Node.js (solo para el servidor)

```bash
# Ejemplo con nvm o apt
sudo apt install nodejs npm
node -v   # >= 18
npm -v    # >= 9
```

### 5.5 Configurar la URL del servidor de señalización

La URL por defecto es `ws://192.168.1.100:3000`. Para cambiarla en tiempo de compilación, añade a `local.properties`:

```properties
signalingServerUrl=ws://192.168.1.50:3000
```

También se puede cambiar desde la app (Configuración → URL del servidor), con prioridad sobre el valor de compilación.

---

## 6. Guía de usuario detallada

### 6.1 Selección de modo (onboarding)

En el primer arranque, la app muestra dos botones grandes:

- **Modo Supervisado** — "Soy el adulto mayor / necesito ayuda".
- **Modo Supervisor** — "Soy familiar / cuidador".

Al elegir, el modo se guarda en DataStore y la app navega a la pantalla correspondiente. El modo puede cambiarse más tarde en **Configuración**.

### 6.2 Pantalla Supervisada (adulto mayor)

- **Estado**: una insignia en la parte superior indica:
  - "Conectando…" (sin conexión al servidor).
  - "Sin vincular" (conectado pero sin familiar vinculado).
  - "Vinculado con `<nombre>`" (conexión y vínculo activos, en verde).
- **Sin vínculo**: muestra el **código QR** grande para que el familiar lo escanee, con un botón "Vincular con familiar" para regenerar/volver a mostrarlo.
- **Con vínculo**: muestra el **botón rojo gigante "ALERTA"** (220 dp). Al pulsarlo:
  - Se produce un pequeño feedback háptico (vibración).
  - Se inicia la videollamada en altavoz hacia el familiar vinculado.
  - Si ya hay una llamada en curso, los pulsos extra se ignoran (no se duplica).

### 6.3 Pantalla Supervisor (familiar)

- **Escanear QR para vincular**: abre la cámara trasera (CameraX + ML Kit). Al detectar un QR válido, muestra un diálogo para **editar el nombre** del dispositivo y guardarlo.
- **Dispositivos vinculados**: muestra la tarjeta del dispositivo vinculado con su **estado** ("En línea" / "Sin conexión") y un botón **"Llamar"**.
- Si no hay dispositivos, muestra un mensaje explicativo.

### 6.4 Pantalla de llamada

- **Video remoto** a pantalla completa.
- **Video local** en una pequeña ventana (Picture-in-Picture) **arrastrable**.
- **Temporizador** `mm:ss` en la parte superior mientras la llamada está activa.
- **Controles**:
  - **Colgar** (rojo): finaliza la llamada, libera cámara/micrófono y detiene el servicio.
  - **Silenciar micrófono**.
  - **Altavoz** (activado por defecto en modo Supervisado).
  - **Cambiar cámara** (frontal/trasera).
- En **modo Supervisor**, al recibir una llamada aparece un overlay con **Aceptar / Rechazar**.
- En **modo Supervisado**, la llamada entrante se **auto-acepta** (sin interacción).

### 6.5 Configuración

- **Modo de la app**: radio buttons para cambiar entre Supervisado y Supervisor (se aplica en caliente).
- **URL del servidor**: campo editable para la IP del PC de señalización (reconecta al guardar).
- **Vínculo**: muestra el `deviceId` propio y, si existe, el nombre/ID del par vinculado.
- **Restablecer vínculo**: borra el par vinculado y **regenera** el `deviceId` (confirmación mediante diálogo).

---

## 7. Guía de solución de problemas

### 7.1 El dispositivo no aparece en `adb devices`

- Asegúrate de que la **Depuración USB** esté activada (Opciones de desarrollador).
- Revisa el cable USB y prueba otro puerto.
- Autoriza el diálogo de depuración que aparece en el teléfono.
- En Linux, añade una regla `udev` para tu fabricante si el dispositivo no se reconoce.

```bash
adb kill-server
adb start-server
adb devices
```

### 7.2 El build falla

- **JDK incorrecto**: verifica `java -version` (17+). Asegúrate de que `JAVA_HOME` apunte a un JDK válido.
- **SDK no encontrado**: revisa `local.properties` con `sdk.dir=<ruta_del_sdk>`.
- **Falta platform 35**: instala `platforms;android-35` con `sdkmanager`.
- **Caché corrupta**:

```bash
./gradlew clean
./gradlew assembleDebug
```

### 7.3 La app no se conecta al servidor (estado "Conectando…")

- Verifica que el servidor esté corriendo (`node index.js` en `server/`).
- Confirma que los teléfonos y el PC estén en la **misma WiFi**.
- Comprueba la **IP** configurada en Configuración (debe ser la IP del PC, no `localhost`).
- Abre el puerto 3000 en el firewall: `sudo ufw allow 3000/tcp`.
- Si cambiaste la IP, verifica `res/xml/network_security_config.xml` (solo permite hosts locales listados).

### 7.4 El escaneo de QR no detecta nada

- Concede el permiso de **cámara**.
- Apunta al QR con buena iluminación y a una distancia razonable.
- Asegúrate de que el QR sea el de Alerta Mayor (esquema `alertamayor:link` o JSON `{"deviceId":...}`).
- Reinicia el escáner volviendo a pulsar "Escanear QR para vincular".

### 7.5 La llamada no conecta (se queda en "Conectando…")

- Verifica que ambos teléfonos estén **registrados** en el servidor (la consola del servidor debe mostrar "Dispositivo registrado: …").
- Confirma que el destino esté **en línea**.
- En redes con NAT estricto o redes distintas, es necesario un servidor **TURN** (no implementado en esta fase).
- Revisa que los permisos de **cámara y micrófono** estén concedidos.

### 7.6 La app se cierra (crash) al llamar

- Verifica los permisos de cámara/micrófono/notificaciones.
- Observa el logcat para el stack trace:

```bash
adb logcat -d | grep -i "alertamayor\|AndroidRuntime"
```

- En Android 14+, el Foreground Service debe declarar el tipo `camera|microphone` (ya está configurado en el manifest).

### 7.7 No llegan notificaciones de llamada entrante

- En Android 13+, concede el permiso **POST_NOTIFICATIONS**.
- Recuerda que **sin FCM**, la app debe estar en primer plano o con el servicio activo para recibir la llamada.

### 7.8 Firma del APK (producción)

- El APK de depuración usa la clave de depuración. Para producción, genera un *keystore* propio:

```bash
keytool -genkey -v -keystore release.keystore -alias alertamayor -keyalg RSA -keysize 2048 -validity 10000
```

- Configura la firma en `build.gradle.kts` usando `keystore.properties` (que **no** debe versionarse).

---

## 8. Tabla de compatibilidad

| Versión de Android | API | ¿Soportada? | Notas |
|--------------------|-----|:-----------:|-------|
| Android 7.0 – 7.1 (Nougat) | 24–25 | **Sí** | Versión mínima (minSdk 24) |
| Android 8.0 – 8.1 (Oreo) | 26–27 | **Sí** | |
| Android 9 (Pie) | 28 | **Sí** | |
| Android 10 | 29 | **Sí** | |
| Android 11 | 30 | **Sí** | |
| Android 12 / 12L | 31–32 | **Sí** | `BLUETOOTH_CONNECT` es runtime (12+) |
| Android 13 | 33 | **Sí** | Requiere `POST_NOTIFICATIONS` |
| Android 14 | 34 | **Sí** | Foreground Service con tipo `camera|microphone` |
| Android 15 | 35 | **Sí** | targetSdk 35 |

---

## 9. Limitaciones conocidas

- **Sin FCM**: no se reciben llamadas con la app completamente cerrada.
- **Misma red WiFi**: necesario para pruebas sin TURN.
- **Un solo par vinculado** por supervisor en esta fase.
- **Señalización sin cifrar** (`ws://`) al ser un servidor local.
- **Sin validación de identidad** en el servidor (el campo `from` no se verifica).
- **`BLUETOOTH_CONNECT` declarado pero no usado** activamente en el flujo principal.
