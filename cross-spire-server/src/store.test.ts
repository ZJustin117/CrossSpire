import { describe, it, expect } from 'vitest';
import { createRoom, getRoom, addPlayer, removePlayer } from './store.js';

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
    expect(found!.players).toContain('player_1');
  });

  it('adds multiple players to a room', () => {
    const room = createRoom();
    addPlayer(room.code, 'player_1');
    addPlayer(room.code, 'player_2');
    const found = getRoom(room.code);
    expect(found!.players).toEqual(['player_1', 'player_2']);
  });

  it('removes a player from a room', () => {
    const room = createRoom();
    addPlayer(room.code, 'player_1');
    addPlayer(room.code, 'player_2');
    removePlayer(room.code, 'player_1');
    const found = getRoom(room.code);
    expect(found!.players).toEqual(['player_2']);
  });

  it('deletes an empty room when last player leaves', () => {
    const room = createRoom();
    addPlayer(room.code, 'player_1');
    removePlayer(room.code, 'player_1');
    expect(getRoom(room.code)).toBeUndefined();
  });
});
