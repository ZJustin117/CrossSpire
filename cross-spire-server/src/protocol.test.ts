import { describe, it, expect } from 'vitest';
import type { QueuePacketMessage, PlayerStateMessage } from './protocol.js';

describe('protocol types', () => {
  it('validates a queue_packet message shape', () => {
    const msg: QueuePacketMessage = {
      type: 'queue_packet',
      source: 'player_a',
      seq: 42,
      packet_id: 'player_a/abc12345',
      sender_id: 'player_a',
      owner_id: 'player_a',
      card_id: 'Strike_R',
      resource_hash: 'a1b2c3d4',
      target: 'monster_0',
    };

    expect(msg.type).toBe('queue_packet');
    expect(msg.seq).toBe(42);
    expect(msg.card_id).toBe('Strike_R');
    expect(msg.target).toBe('monster_0');
  });

  it('validates a player_state message shape', () => {
    const msg: PlayerStateMessage = {
      type: 'player_state',
      source: 'player_a',
      seq: 48,
      player: {
        hp: 65,
        max_hp: 80,
        block: 12,
        energy: 3,
        character_class: 'IRONCLAD',
        powers: ['Vulnerable'],
        power_amounts: [2],
        relics: ['Burning Blood'],
        potions: ['Fire Potion'],
      },
    };

    expect(msg.type).toBe('player_state');
    expect(msg.player.hp).toBe(65);
    expect(msg.player.energy).toBe(3);
    expect(msg.player.relics).toContain('Burning Blood');
  });
});
