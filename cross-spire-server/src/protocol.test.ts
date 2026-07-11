import { describe, it, expect } from 'vitest';
import type { PlayCardRequest, RemotePlayerSync } from './protocol.js';

describe('protocol types', () => {
  it('validates a play_card request message shape', () => {
    const msg: PlayCardRequest = {
      type: 'request',
      subtype: 'play_card',
      source: 'player_a',
      seq: 42,
      card_id: 'Strike_R',
      target: 'monster_0',
      cost_paid: 1,
    };

    expect(msg.type).toBe('request');
    expect(msg.subtype).toBe('play_card');
    expect(msg.seq).toBe(42);
    expect(msg.card_id).toBe('Strike_R');
    expect(msg.target).toBe('monster_0');
    expect(msg.cost_paid).toBe(1);
  });

  it('validates a remote_player state_sync message shape', () => {
    const msg: RemotePlayerSync = {
      type: 'state_sync',
      subtype: 'remote_player',
      source: 'player_a',
      seq: 48,
      player: {
        hp: 65,
        max_hp: 80,
        block: 12,
        relics: ['Burning Blood'],
        potions: ['Fire Potion', null],
      },
    };

    expect(msg.type).toBe('state_sync');
    expect(msg.subtype).toBe('remote_player');
    expect(msg.player.hp).toBe(65);
    expect(msg.player.relics).toContain('Burning Blood');
  });
});
