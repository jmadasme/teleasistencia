# Ficha Técnica de Usuario — Alerta Mayor

## 1. Qué es Alerta Mayor

Alerta Mayor es una **aplicación de teleasistencia** diseñada para que un adulto mayor pueda contactar con su familiar o cuidador de forma inmediata, sin complicaciones y sin necesidad de dispositivos adicionales. Convierte el teléfono del adulto mayor en un **botón de emergencia inteligente** que establece una **videollamada directa en manos libres** con su familiar.

A diferencia de los sistemas tradicionales de teleasistencia (botón de pánico con central receptora), Alerta Mayor **conecta directamente dos teléfonos** mediante videollamada WebRTC, permitiendo al familiar **ver y oír** a la persona mayor al instante.

---

## 2. Especificaciones Técnicas

| Característica | Valor |
|----------------|-------|
| **Nombre de la app** | Alerta Mayor |
| **Package / ID** | `com.alertamayor.app` |
| **Versión** | 1.2-fix5 |
| **Plataforma** | Android nativo (Kotlin + Jetpack Compose) |
| **minSdk / targetSdk** | 24 / 35 (Android 7.0 – 15) |
| **Idioma** | Español |
| **Arquitectura** | MVVM + ViewModel + StateFlow + DI manual |
| **Señalización** | WebSocket (ws:// local / wss:// en producción) |
| **Videollamada** | WebRTC (STUN Google + TURN configurable) |
| **Notificaciones push** | Firebase Cloud Messaging (FCM) |
| **Auto-arranque** | Sí (al encender el teléfono) |
| **Tema** | Sistema / Claro / Oscuro (configurable) |
| **Idioma** | Español |

---

## 3. Cómo instalar

### En el teléfono del adulto mayor (Modo Supervisado)

1. Instala la app (APK o Play Store futura).
2. Al abrir por primera vez, pulsa **"Modo Supervisado"** (*Soy el adulto mayor / necesito ayuda*).
3. Acepta los permisos de **cámara, micrófono y notificaciones**.
4. La app mostrará un **código QR grande** en pantalla. Déjalo visible.

### En el teléfono del familiar (Modo Supervisor)

1. Instala la misma app en tu teléfono.
2. Al abrir, pulsa **"Modo Supervisor"** (*Soy familiar / cuidador*).
3. Pulsa **"Escanear QR para vincular"** y apunta la cámara al QR del teléfono del adulto mayor.
5. Escribe un nombre (ej. "Mamá") y pulsa **Guardar**.
6. Verás el dispositivo en la lista con estado **En línea**.

> **Importante**: Ambos teléfonos deben estar en la misma red WiFi y el servidor de señalización debe estar encendido en un PC de la misma red (ver sección *Servidor de señalización*).

---

## 4. Cómo se usa

### Hacer una alerta (desde el adulto mayor)

1. Pulsa el **botón rojo "ALERTA"** (grande, centrado, con texto "ALERTA" y SOS).
2. El teléfono vibra ligeramente (feedback háptico) y muestra "Llamando…".
3. La llamada se inicia en **altavoz manos libres** automáticamente.

### Recibir la alerta (desde el familiar)

1. Suena el teléfono con una **alarma de emergencia** y vibración.
3. Aparece una **notificación de llamada entrante** con botones **Aceptar / Rechazar**.
3. Pulsa **Aceptar** → se abre la videollamada a pantalla completa.

### Llamar desde el familiar (auto-respuesta)

1. En la pantalla principal, pulsa **"Llamar"** en la tarjeta del dispositivo vinculado.
2. El teléfono del adulto mayor **contesta automáticamente**: se abre la videollamada en altavoz, sin que la persona mayor toque nada.

### Durante la llamada

| Control | Función |
|---------|---------|
| Botón rojo (teléfono) | Colgar |
| Micrófono | Silenciar / activar micrófono |
| Altavoz | Activar / desactivar altavoz (ON por defecto en supervisado) |
| Cámara | Cambiar cámara frontal / trasera |
| Video local | Arrastrar por la pantalla (Picture-in-Picture) |

### Colgar

Cualquiera de los dos pulsa el botón rojo de colgar. La llamada finaliza, se liberan cámara y micrófono, y ambos vuelven a su pantalla principal.

---

## 5. Configuración (icono ⚙️ en la esquina superior)

| Opción | Qué hace |
|--------|----------|
| **Modo de la app** | Cambiar entre *Supervisado* (adulto mayor) y *Supervisor* (familiar). |
| **URL del servidor** | IP y puerto del servidor de señalización (ej. `ws://192.168.1.100:3000` o `wss://TU-DOMINIO.com`). |
| **Servidor TURN** | URL, usuario y contraseña del servidor TURN (para llamadas en redes móviles / 4G). |
| **ID del dispositivo** | Tu identificador único (se genera solo al instalar). |
| **Vinculado con** | Nombre e ID del dispositivo vinculado. |
| **Restablecer vínculo** | Borra el vínculo y regenera el ID (requiere volver a escanear QR). |
| **Tema** | Sistema / Claro / Oscuro. |

---

## 6. Indicador de conexión a internet (esquina superior izquierda)

| Icono | Estado | Qué significa |
|-------|--------|---------------|
| 📶 | Wi-Fi | Conectado por Wi-Fi |
| 📱 | Datos móviles | Conectado por datos móviles (4G/5G) |
| 🚫 | Sin internet | Sin conexión a internet |

**Importante**: Si se pierde internet (Wi-Fi y datos), la campanilla ALERTA se pone **gris con una barra roja diagonal** y **no se puede llamar**. Al recuperar la conexión, vuelve a ponerse roja y funcional.

---

## 7. Auto-arranque al encender

Al encender el teléfono, la app **se abre automáticamente** en la pantalla de la campanilla (modo supervisado) o en la lista de dispositivos (modo supervisor). No hace falta abrirla manualmente.

---

## 8. Tema visual (Configuración → Tema)

| Opción | Efecto |
|--------|--------|
| **Sistema** | Sigue el tema del teléfono (claro u oscuro según Android). |
| **Claro** | Fuerza tema claro. |
| **Oscuro** | Fuerza tema oscuro (fondo negro, alto contraste, ideal de noche). |

---

## 9. Servidor de señalización

La app necesita un **servidor de señalización Node.js** corriendo en un PC de la misma red WiFi.

### En el PC Linux (misma red WiFi):

```bash
cd server
npm install
node index.js
# Servidor escuchando en 0.0.0.0:3000
```

Por defecto escucha en `ws://192.168.1.100:3000`. Cambia la IP en Configuración → URL del servidor si tu PC tiene otra IP.

### Abrir el firewall (PC Linux):

```bash
sudo ufw allow 3000/tcp
```

---

## 10. TURN (para redes móviles / 4G)

**¿Qué es?** Un servidor TURN (ej. `coturn`) actúa como **relevo del audio/video** cuando los teléfonos no pueden conectarse directamente (NAT simétrico, CGNAT de operadores móviles). **Es imprescindible** para que las videollamadas funcionen en redes 4G/5G fuera de casa.

### Configuración en la app (Configuración → Servidor TURN):

| Campo | Ejemplo |
|-------|---------|
| **URL TURN** | `turn:TU-DOMINIO.com:3478?transport=tcp` |
| **Usuario TURN** | `usuario` |
| **Contraseña TURN** | `contraseña` |

> **Importante**: Si dejas los campos vacíos, la app usa solo STUN (funciona en WiFi local, **no** en 4G/5G con CGNAT).

---

## 11. Preguntas frecuentes (FAQ)

| Pregunta | Respuesta |
|----------|-----------|
| **¿Necesito internet para que funcione?** | Sí. La videollamada requiere conexión a internet (Wi-Fi o datos móviles). Si se pierde internet, la campanilla se desactiva (gris con barra roja). |
| **¿Funciona si el adulto mayor cierra la app?** | La app se abre automáticamente al encender el teléfono. Si el usuario la cierra manualmente (deslizando en "apps recientes"), el servicio en primer plano se detiene y no recibirá llamadas hasta volver a abrir la app. |
| **¿Funciona si el familiar tiene el teléfono en silencio?** | La notificación de emergencia usa **prioridad alta** y suena incluso en modo silencio/No molestar (Android 8+). |
| **¿Puedo usarlo solo en WiFi sin servidor TURN?** | Sí. En la misma red WiFi, el STUN de Google suele ser suficiente. El TURN es necesario solo para llamadas desde/hacia redes móviles (4G/5G) o redes distintas. |
| **¿Puedo usarlo sin servidor de señalización?** | No. El servidor de señalización es obligatorio para el handshake WebRTC (SDP/ICE). |
| **¿Puedo usar IP pública en vez de dominio?** | Para `wss://` (TLS) se recomienda un **dominio** (Let's Encrypt no emite certificados para IPs públicas). |
| **¿Puedo usar la app sin FCM (push)?** | Sí, pero el familiar solo recibirá la llamada si la app está abierta o en primer plano. Sin FCM, si el sistema suspende la app, no recibirá la alerta. |
| **¿Cómo restablezco el vínculo si me equivoco?** | Configuración → Restablecer vínculo. Borra el vínculo y regenera el ID. Vuelve a escanear el QR. |

---

## 12. Limitaciones conocidas

- **Un solo par vinculado**: esta versión soporta un único dispositivo supervisado por supervisor.
- **Sin FCM en builds sin `google-services.json`**: si falta el archivo, el servidor avisa pero arranca sin push.
- **Certificados autofirmados no soportados**: usa Let's Encrypt con dominio real para `wss://`.
- **Un solo dispositivo supervisor por supervisado** (arquitectura lista para escalar a N).
- **El servidor no valida `from`**: en producción se recomienda añadir token/secret al QR.

---

## 13. Solución de problemas

| Síntoma | Causa probable | Solución |
|---------|----------------|----------|
| No aparece "En línea" | Servidor caído o IP incorrecta | Verifica que `node index.js` corra en el PC y que la IP en Configuración coincida. |
| Llamada no se oye/ve en 4G | Falta servidor TURN | Configura los 3 campos TURN en Configuración. |
| "No se puede conectar" en release | URL `ws://` en build release | Usa `wss://TU-DOMINIO.com` o compila en debug. |
| No suena la notificación | App cerrada forzadamente | Abre la app al menos una vez tras reinstalar/actualizar. |
| No aparece el QR | Permiso de cámara denegado | Configuración → Apps → Alerta Mayor → Permisos → Cámara → Permitir. |
| Error "peer-unavailable" | El otro dispositivo no está conectado | Verifica que ambos teléfonos tengan internet y el servidor corriendo. |

---

## 14. Advertencias importantes

- **Uso en emergencia crítica**: si el teléfono pierde **toda** conexión a internet (ni WiFi ni datos), la campanilla se desactiva y **no se puede llamar**. Asegúrate de que el teléfono tenga al menos una vía de conexión.
- **APK debug**: el APK generado con `assembleDebug` está firmado con la clave de depuración. No lo distribuyas públicamente. Para producción, genera un APK/AAB firmado con tu propio *keystore*.
- **Secretos**: nunca subas al repositorio `google-services.json`, `serviceAccountKey.json`, `local.properties`, *keystores* ni contraseñas. Usa `.gitignore`.
- **VPS y dominio**: para producción, migra el servidor a una VPS con dominio + Let's Encrypt + coturn (ver `docs/vps-deployment.md`).

---

## 15. Documentación relacionada

| Documento | Descripción |
|-----------|-------------|
| `README.md` | Documentación principal completa (incluye arquitectura, ejemplos, advertencias). |
| `docs/developer.md` | Documentación técnica del código (arquitectura, clases, guía de extensión). |
| `docs/vps-deployment.md` | Guía completa de despliegue en VPS (Node, coturn, Nginx, Let's Encrypt). |
| `server/README.md` | Documentación del servidor de señalización y protocolo JSON. |
| `PLAN.md` | Plan de implementación técnico (requerimientos, diseño, casos de prueba). |
| `AUDIT_REPORT.md` | Informe de auditoría (fix5 aprobado sin incidencias). |