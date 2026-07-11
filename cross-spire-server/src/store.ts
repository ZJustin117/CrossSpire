import { v4 as uuidv4 } from 'uuid';

const rooms = new Map<string, { code: string; players: string[] }>();

export function createRoom(code?: string) {
  code = code ?? uuidv4().slice(0, 6).toUpperCase();
  const room = { code, players: [] };
  rooms.set(code, room);
  return room;
}

export function getRoom(code: string) {
  return rooms.get(code);
}

export function addPlayer(code: string, playerId: string) {
  const room = rooms.get(code);
  if (room) room.players.push(playerId);
}

export function removePlayer(code: string, playerId: string) {
  const room = rooms.get(code);
  if (!room) return;
  const idx = room.players.indexOf(playerId);
  if (idx !== -1) room.players.splice(idx, 1);
  if (room.players.length === 0) rooms.delete(code);
}
