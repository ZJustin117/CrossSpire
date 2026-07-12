import { describe, it, expect, beforeEach } from 'vitest';
import { createRoom, getRoom, addPlayer, removePlayer, touchHeartbeat, getExpiredPlayers } from './store.js';

describe('store', () => {
  it('creates a room and returns a unique room code', () => {
    const room = createRoom();
    expect(room.code).toBeTruthy();
    expect(typeof room.code).toBe('string');
    expect(room.code.length).toBeGreaterThan(0);
  });

  it('creates a room with a specified code', () => {
    const room = createRoom('MYROOM');
    expect(room.code).toBe('MYROOM');
    expect(getRoom('MYROOM')).toBe(room);
  });

  it('retrieves a room by code', () => {
    const room = createRoom();
    const found = getRoom(room.code);
    expect(found).toBe(room);
  });

  it('returns undefined for a non-existent room code', () => {
    const found = getRoom('NOSUCH');
    expect(found).toBeUndefined();
  });

  it('adds a player to a room', () => {
    const room = createRoom();
    addPlayer(room.code, 'player_1');
    const found = getRoom(room.code);
    expect(found!.players.some(p => p.playerId === 'player_1')).toBe(true);
  });

  it('adds multiple players to a room', () => {
    const room = createRoom();
    addPlayer(room.code, 'player_1');
    addPlayer(room.code, 'player_2');
    const found = getRoom(room.code);
    expect(found!.players.map(p => p.playerId)).toEqual(['player_1', 'player_2']);
  });

  it('removes a player from a room', () => {
    const room = createRoom();
    addPlayer(room.code, 'player_1');
    addPlayer(room.code, 'player_2');
    removePlayer(room.code, 'player_1');
    const found = getRoom(room.code);
    expect(found!.players.map(p => p.playerId)).toEqual(['player_2']);
  });

  it('deletes an empty room when last player leaves', () => {
    const room = createRoom();
    addPlayer(room.code, 'player_1');
    removePlayer(room.code, 'player_1');
    expect(getRoom(room.code)).toBeUndefined();
  });

  it('updates heartbeat timestamp for a player', () => {
    const room = createRoom('HBROOM');
    addPlayer(room.code, 'p1');
    touchHeartbeat(room.code, 'p1');
    const found = getRoom('HBROOM');
    const p = found!.players.find(x => x.playerId === 'p1')!;
    expect(p.lastHeartbeat).toBeGreaterThan(0);
  });

  it('returns expired players with staleness > timeout', async () => {
    const room = createRoom('EXPIRE');
    addPlayer(room.code, 'stale');

    const player = getRoom('EXPIRE')!.players[0];
    player.lastHeartbeat = 0; // force ancient timestamp

    const expired = getExpiredPlayers(5000);
    expect(expired.length).toBe(1);
    expect(expired[0].playerId).toBe('stale');
    expect(expired[0].room).toBe('EXPIRE');
  });

  it('does not return recently touched players', () => {
    const room = createRoom('FRESH');
    addPlayer(room.code, 'active');
    touchHeartbeat(room.code, 'active');

    const expired = getExpiredPlayers(5000);
    const hasActive = expired.some(e => e.room === 'FRESH' && e.playerId === 'active');
    expect(hasActive).toBe(false);
  });
});
