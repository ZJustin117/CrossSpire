import { createServer as createHttp } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import { v4 as uuidv4 } from 'uuid';
import { createRoom, getRoom, addPlayer } from './store.js';

const playerSockets = new Map<string, WebSocket>();
const roomForPlayer = new Map<string, string>();

function broadcast(roomCode: string, message: unknown, excludePlayer?: string) {
  const data = JSON.stringify(message);
  for (const [playerId, socket] of playerSockets) {
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
      if (msg.type === 'join') {
        let room = getRoom(msg.code);
        if (!room) room = createRoom(msg.code);

        broadcast(room.code, { type: 'player_joined', playerId });

        addPlayer(room.code, playerId);
        roomForPlayer.set(playerId, room.code);
        ws.send(JSON.stringify({ type: 'room_state', code: room.code, players: room.players }));
      } else {
        const roomCode = roomForPlayer.get(playerId);
        if (!roomCode) return;
        broadcast(roomCode, msg, playerId);
      }
    });
  });

  http.listen(port);
  return http;
}

const mainFile = process.argv[1] ?? '';
if ((mainFile.endsWith('server.ts') || mainFile.endsWith('server.js')) && !mainFile.includes('.test')) {
  const port = parseInt(process.env.PORT || '9876', 10);
  createServer(port);
  console.log(`CrossSpire relay listening on :${port}`);
}
