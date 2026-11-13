# Despliegue en VPS — Servidor de señalización WebRTC + TURN (Alerta Mayor)

Esta guía describe cómo desplegar en una **VPS con IP/dominio público** el servidor de
señalización WebRTC (Node.js) y el servidor **TURN (coturn)** necesarios para que las
videollamadas de la app **Alerta Mayor** funcionen desde redes móviles (GSM/4G), superando
NAT simétrico/CGNAT.

> El objetivo es exponer la señalización por **`wss://TU-DOMINIO.com`** y el TURN por
> **`turn:TU-DOMINIO.com:3478`**. Sustituye `TU-DOMINIO.com` por tu dominio real.
>
> ⚡ **Atajo**: todo lo descrito en esta guía está automatizado en
> [`server/deploy-vps.sh`](../server/deploy-vps.sh). Cópialo a la VPS junto con `server/index.js`,
> `package.json` y `package-lock.json`, y ejecuta:
>
> ```bash
> sudo bash deploy-vps.sh --dominio midominio.com --turn
> ```

---

## 0. ¿Necesito comprar un dominio?

**Respuesta corta: NO, si usas un hostname gratuito; SÍ, si quieres IP pura con TLS.**

| Componente | ¿Funciona con solo IP fija? | Motivo |
|------------|------------------------------|--------|
| TURN (`turn:IP:3478`) | ✅ Sí | El URI TURN no requiere dominio |
| Señalización `ws://IP:3000` | ⚠️ Solo build **debug** | El build release bloquea cleartext (`network_security_config`) |
| Señalización `wss://` (TLS) | ❌ No | **Let's Encrypt no emite certificados para IPs**, solo nombres |

### Opciones gratuitas para obtener wss:// sin comprar dominio

| Servicio | Hostname resultante | Registro | Certificado Let's Encrypt |
|----------|---------------------|----------|---------------------------|
| **sslip.io** | `203-0-113-10.sslip.io` (IP 203.0.113.10) | **Ninguno** — automático | ✅ Sí |
| **DuckDNS** | `tunombre.duckdns.org` | Gratis en duckdns.org | ✅ Sí |

Con cualquiera de las dos obtienes TLS válido por $0:

```bash
# Ejemplo con sslip.io (cero registro): tu IP 1.2.3.4 → hostname 4-3-2-1.sslip.io
sudo bash deploy-vps.sh --dominio 4-3-2-1.sslip.io --turn

# Ejemplo con DuckDNS: registra el subdominio primero en https://www.duckdns.org
sudo bash deploy-vps.sh --dominio tunombre.duckdns.org --turn
```

> **Nota**: sslip.io convierte cualquier IP en hostname automáticamente invirtiendo el orden:
> `a.b.c.d` → `d-c-b-a.sslip.io`.

---

## 1. Prerrequisitos

- **VPS** con Ubuntu 22.04 / 24.04 (o similar) y acceso `root`/`sudo`.
- Un **dominio** apuntando a la IP pública de la VPS (registro `A`).
- Puertos abiertos tanto en el firewall del sistema (`ufw`) como en el proveedor del VPS
  (ver sección 6).

---

## 2. Instalar Node.js 18+ y desplegar el servidor

Instalar Node.js vía NodeSource:

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
node -v   # >= 18
```

Desplegar el servidor de señalización (el código está en `server/`):

```bash
cd /opt/alerta-mayor
git clone <TU_REPO> .      # o copia la carpeta server/
cd server
npm install
```

Probar en local:

```bash
node index.js
# Servidor de señalización (ws) escuchando en 0.0.0.0:3000
curl http://127.0.0.1:3000/health   # -> ok
```

---

## 3. Instalar y configurar coturn (servidor TURN)

Instalar coturn:

```bash
sudo apt-get install -y coturn
```

Editar `/etc/turnserver.conf`:

```ini
listening-port=3478
tls-listening-port=5349

fingerprint
lt-cred-mech

# Usuario/contraseña estáticos (deben coincidir con los campos TURN de la app)
user=usuario:contraseña

realm=TU-DOMINIO.com

# Rango de puertos de relay (media UDP)
min-port=49152
max-port=65535

# Certificados Let's Encrypt para TURNS (opcional, recomentado)
cert=/etc/letsencrypt/live/TU-DOMINIO.com/fullchain.pem
pkey=/etc/letsencrypt/live/TU-DOMINIO.com/privkey.pem
```

Habilitar y arrancar:

```bash
sudo systemctl enable coturn
sudo systemctl start coturn
sudo systemctl status coturn
```

> La app espera el campo URL TURN como URI completo, p. ej.:
> `turn:TU-DOMINIO.com:3478?transport=tcp` (recomendado para NAT estricto/4G) o
> `turn:TU-DOMINIO.com:3478?transport=udp`.

---

## 4. Nginx (o Caddy) como reverse proxy TLS para `wss://`

Se recomienda exponer la señalización tras un proxy que termine el TLS. El servidor Node
se mantiene en `ws://127.0.0.1:3000` (sin TLS) y el proxy expone `wss://TU-DOMINIO.com`.

### Nginx

```bash
sudo apt-get install -y nginx
```

Crear `/etc/nginx/sites-available/alerta-mayor`:

```nginx
server {
    listen 80;
    server_name TU-DOMINIO.com;
    # Redirigir HTTP -> HTTPS (o dejar para certbot)
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name TU-DOMINIO.com;

    ssl_certificate     /etc/letsencrypt/live/TU-DOMINIO.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/TU-DOMINIO.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/alerta-mayor /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### Alternativa: Caddy (auto-TLS)

Con Caddy no hace falta gestionar certificados manualmente:

```
TU-DOMINIO.com {
    reverse_proxy 127.0.0.1:3000
}
```

```bash
sudo apt-get install -y caddy
sudo systemctl enable caddy && sudo systemctl start caddy
```

---

## 5. Let's Encrypt (certbot)

> Si usas **Caddy**, los certificados se emiten automáticamente (omitir esta sección).

Instalar certbot:

```bash
sudo apt-get install -y certbot python3-certbot-nginx
```

Emitir certificado (con el plugin nginx):

```bash
sudo certbot --nginx -d TU-DOMINIO.com
```

Renovación automática (certbot instala un timer):

```bash
sudo systemctl status certbot.timer
```

---

## 6. Puertos a abrir

Abrir en el firewall del sistema (`ufw`) **y** en el panel del proveedor del VPS:

| Puerto | Protocolo | Uso |
|--------|-----------|-----|
| 80 | TCP | Let's Encrypt / redirección a HTTPS |
| 443 | TCP | `wss://` (señalización) |
| 3478 | TCP + UDP | TURN |
| 5349 | TCP + UDP | TURNS (TURN sobre TLS) |
| 49152–65535 | UDP | Relay TURN (media) |

Ejemplo con `ufw`:

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 3478/tcp
sudo ufw allow 3478/udp
sudo ufw allow 5349/tcp
sudo ufw allow 5349/udp
sudo ufw allow 49152:65535/udp
sudo ufw enable
```

---

## 7. Systemd units (auto-arranque)

### Servidor Node (`/etc/systemd/system/alerta-signaling.service`)

```ini
[Unit]
Description=Alerta Mayor - Servidor de señalización WebRTC
After=network.target

[Service]
WorkingDirectory=/opt/alerta-mayor/server
ExecStart=/usr/bin/node index.js
Restart=always
RestartSec=5
Environment=PORT=3000
# Descomenta si sirves wss:// directo desde Node (alternativa a Nginx/Caddy):
# Environment=TLS_CERT_PATH=/etc/letsencrypt/live/TU-DOMINIO.com/fullchain.pem
# Environment=TLS_KEY_PATH=/etc/letsencrypt/live/TU-DOMINIO.com/privkey.pem

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable alerta-signaling
sudo systemctl start alerta-signaling
```

### coturn (`/etc/systemd/system/coturn.service`)

Normalmente coturn ya trae su unit (`/lib/systemd/system/coturn.service`); actívalo como en
la sección 3. Si no, crea una unit similar a la anterior apuntando a `/usr/bin/turnserver`
con `-c /etc/turnserver.conf`.

---

## 8. Verificación

1. **Health check del servidor** (a través del proxy):
   ```bash
   curl https://TU-DOMINIO.com/health   # -> ok
   ```

2. **Probar el WebSocket seguro** (opcional, con `wscat`):
   ```bash
   npx wscat -c wss://TU-DOMINIO.com
   ```

3. **En la app**:
   - Configuración → URL del servidor: `wss://TU-DOMINIO.com` (por defecto).
   - Configuración → Servidor TURN:
     - URL: `turn:TU-DOMINIO.com:3478?transport=tcp`
     - Usuario / Contraseña: los definidos en `turnserver.conf`.
   - Guarda y haz una llamada entre dos redes móviles (o entre 4G y Wi-Fi) para confirmar
     que el audio/vídeo funciona a través del TURN.

---

## Notas

- **TLS directo desde Node (alternativa):** si no quieres Nginx/Caddy, define
  `TLS_CERT_PATH` y `TLS_KEY_PATH` en el entorno del servicio Node; entonces Node sirve
  `wss://` directamente en `PORT` (p. ej. `8443`). En ese caso la app usaría
  `wss://TU-DOMINIO.com:8443`.
- **Let's Encrypt no emite certificados para IPs públicas.** Si usas una IP (no dominio),
  necesitarías una CA distinta o un certificado autofirmado (fuera de alcance de esta guía).
- **Servidor local intacto:** sin variables TLS, `node index.js` sigue sirviendo
  `ws://0.0.0.0:3000`, por lo que el desarrollo local en la red WiFi no se ve afectado.
