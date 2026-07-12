import { v4 as uuidv4 } from 'uuid';

interface PlayerMeta {
  playerId: string;
  lastHeartbeat: number;
}

interface Room {
  code: string;
  players: PlayerMeta[];
}

const rooms = new Map<string, Room>();

export function createRoom(code?: string): Room {
  code = code ?? uuidv4().slice(0, 6).toUpperCase();
  const room = { code, players: [] };
  rooms.set(code, room);
  return room;
}

export function getRoom(code: string): Room | undefined {
  return rooms.get(code);
}

export function addPlayer(code: string, playerId: string) {
  const room = rooms.get(code);
  if (room) room.players.push({ playerId, lastHeartbeat: Date.now() });
}

export function removePlayer(code: string, playerId: string) {
  const room = rooms.get(code);
  if (!room) return;
  const idx = room.players.findIndex(p => p.playerId === playerId);
  if (idx !== -1) room.players.splice(idx, 1);
  if (room.players.length === 0) rooms.delete(code);
}

export function touchHeartbeat(code: string, playerId: string) {
  const room = rooms.get(code);
  if (!room) return;
  const player = room.players.find(p => p.playerId === playerId);
  if (player) player.lastHeartbeat = Date.now();
}

export function getExpiredPlayers(timeoutMs: number): { room: string; playerId: string }[] {
  const now = Date.now();
  const expired: { room: string; playerId: string }[] = [];
  for (const [code, room] of rooms) {
    for (const p of room.players) {
      if (now - p.lastHeartbeat > timeoutMs) {
        expired.push({ room: code, playerId: p.playerId });
      }
    }
  }
  return expired;
}

export function getPlayerIds(room: Room): string[] {
  return room.players.map(p => p.playerId);
}
