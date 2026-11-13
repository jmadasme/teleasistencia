/**
 * Servidor de señalización WebRTC — Alerta Mayor
 * ----------------------------------------------
 * Node.js + ws (WebSocket).
 *
 * Funciones:
 *  - `register(deviceId, socket)`: mantiene un mapa `Map<deviceId, WebSocket>`.
 *  - Enrutamiento de mensajes (`call`, `offer`, `answer`, `ice-candidate`, `end-call`)
 *    desde el emisor al socket del par de destino (`to`).
 *  - Gestión de desconexiones (limpia el mapa y notifica `peer-offline`/`peer-unavailable`).
 *
 * Levantar:
 *   cd server
 *   npm install
 *   node index.js        # escucha en 0.0.0.0:3000
 */

const WebSocket = require('ws');

const PORT = process.env.PORT || 3000;

// Mapa deviceId -> WebSocket (reemplaza conexiones duplicadas)
const clients = new Map();

const wss = new WebSocket.Server({ port: PORT });

console.log(`Servidor de señalización escuchando en 0.0.0.0:${PORT}`);

function send(ws, message) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(message));
  }
}

function broadcastOnline(deviceId) {
  for (const [, socket] of clients) {
    send(socket, { type: 'peer-online', from: deviceId });
  }
}

function broadcastOffline(deviceId) {
  for (const [, socket] of clients) {
    send(socket, { type: 'peer-offline', from: deviceId });
  }
}

wss.on('connection', (socket) => {
  console.log('Nueva conexión');

  let deviceId = null;

  socket.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch (e) {
      console.warn('Mensaje JSON inválido:', raw.toString());
      return;
    }

    const { type, from, to, payload } = msg;
    console.log(`MSG ${type} from=${from} to=${to || '-'}`);

    // ---- Registro ----
    if (type === 'register') {
      deviceId = (payload && payload.deviceId) || from;
      if (!deviceId) {
        send(socket, { type: 'error', from: 'server', payload: 'deviceId requerido' });
        return;
      }
      // Reemplaza conexión duplicada
      const previous = clients.get(deviceId);
      if (previous && previous !== socket) {
        try { previous.close(); } catch (_) {}
      }
      clients.set(deviceId, socket);
      send(socket, { type: 'registered', from: 'server', payload: deviceId });
      broadcastOnline(deviceId);
      console.log(`Dispositivo registrado: ${deviceId} (${clients.size} conectados)`);
      return;
    }

    // ---- Enrutamiento al destino `to` ----
    if (to) {
      const targetSocket = clients.get(to);
      if (!targetSocket) {
        // El destino no está registrado / conectado
        send(socket, { type: 'peer-unavailable', from: 'server', to: from || deviceId, payload: to });
        return;
      }
      send(targetSocket, { ...msg, from: from || deviceId });
      return;
    }

    // ---- Sin destino: respuesta directa (p. ej. error genérico) ----
    send(socket, { ...msg, from: from || deviceId });
  });

  socket.on('close', () => {
    if (deviceId && clients.get(deviceId) === socket) {
      clients.delete(deviceId);
      broadcastOffline(deviceId);
      console.log(`Dispositivo desconectado: ${deviceId} (${clients.size} conectados)`);
    }
  });

  socket.on('error', (err) => {
    console.error('Error de socket:', err.message);
  });
});
