import { createServer as createHttp } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import { v4 as uuidv4 } from 'uuid';
import { createRoom, getRoom, addPlayer, removePlayer, touchHeartbeat, getExpiredPlayers, getPlayerIds } from './store.js';

const playerSockets = new Map<string, WebSocket>();
const roomForPlayer = new Map<string, string>();

function deliver(roomCode: string, message: unknown, excludePlayer?: string) {
  const data = JSON.stringify(message);
  const target = (message as any).target;
  

  if (target && playerSockets.has(target)) {
    const socket = playerSockets.get(target);
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(data);
    }
    return;
  }

  for (const [playerId, socket] of playerSockets) {
    if (socket.readyState !== WebSocket.OPEN) continue;
    if (roomForPlayer.get(playerId) === roomCode && playerId !== excludePlayer) {
      socket.send(data);
    }
  }
}

export function createServer(port: number) {
  const http = createHttp();
  const wss = new WebSocketServer({ server: http });

  wss.on('connection', (ws: WebSocket) => {
    const playerId = uuidv4();
    playerSockets.set(playerId, ws);
    ws.send(JSON.stringify({ type: 'connected', playerId }));

    ws.on('message', (data) => {
      const msg = JSON.parse(data.toString());
      if (msg.type === 'ping') {
        ws.send(JSON.stringify({ type: 'pong', seq: msg.seq || 0 }));
        const rc = roomForPlayer.get(playerId);
        if (rc) touchHeartbeat(rc, playerId);
        return;
      }

      if (msg.type === 'join') {
        let room = getRoom(msg.code);
        if (!room) room = createRoom(msg.code);

        deliver(room.code, { type: 'player_joined', playerId });

        addPlayer(room.code, playerId);
        roomForPlayer.set(playerId, room.code);
        ws.send(JSON.stringify({ type: 'room_state', code: room.code, host: room.host, players: getPlayerIds(room) }));
      } else {
        const roomCode = roomForPlayer.get(playerId);
        if (!roomCode) return;
        touchHeartbeat(roomCode, playerId);
        deliver(roomCode, msg, playerId);
      }
    });

    ws.on('close', () => {
      const rc = roomForPlayer.get(playerId);
      if (rc) {
        removePlayer(rc, playerId);
        deliver(rc, { type: 'player_left', playerId });
      }
      roomForPlayer.delete(playerId);
      playerSockets.delete(playerId);
    });
  });

  const cleanupInterval = setInterval(() => {
    const expired = getExpiredPlayers(30_000);
    for (const { room, playerId } of expired) {
      removePlayer(room, playerId);
      deliver(room, { type: 'player_left', playerId });
      roomForPlayer.delete(playerId);
      playerSockets.delete(playerId);
    }
  }, 15_000);

  http.on('close', () => clearInterval(cleanupInterval));

  http.listen(port);
  return http;
}

const mainFile = process.argv[1] ?? '';
if ((mainFile.endsWith('server.ts') || mainFile.endsWith('server.js')) && !mainFile.includes('.test')) {
  const port = parseInt(process.env.PORT || '9876', 10);
  const httpServer = createServer(port);
  console.log(`CrossSpire relay listening on :${port}`);
}
