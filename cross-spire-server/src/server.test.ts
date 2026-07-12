import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { createServer } from './server.js';
import { WebSocket } from 'ws';

let server: ReturnType<typeof createServer>;
let port: number;

beforeAll(async () => {
  server = createServer(0);
  port = (server.address() as any).port;
});

afterAll(() => {
  server.close();
});

function connectAndJoin(code: string): Promise<{ ws: WebSocket; playerId: string }> {
  return new Promise((resolve) => {
    const ws = new WebSocket(`ws://localhost:${port}`);
    let playerId: string | null = null;
    ws.on('message', (data) => {
      const msg = JSON.parse(data.toString());
      if (msg.type === 'connected') {
        playerId = msg.playerId;
        ws.send(JSON.stringify({ type: 'join', code }));
      } else if (msg.type === 'room_state') {
        resolve({ ws, playerId: playerId! });
      }
    });
  });
}

function nextMessage(ws: WebSocket): Promise<any> {
  return new Promise((resolve) => {
    ws.on('message', (data) => { resolve(JSON.parse(data.toString())); });
  });
}

describe('server', () => {
  it('connects and receives connected handshake', async () => {
    const { ws, playerId } = await connectAndJoin('T01');
    expect(typeof playerId).toBe('string');
    ws.close();
  });

  it('existing player receives player_joined', async () => {
    const p1 = await connectAndJoin('R01');
    const p1Listen = nextMessage(p1.ws);
    await connectAndJoin('R01');
    const n = await p1Listen;
    expect(n.type).toBe('player_joined');
    p1.ws.close();
  });

  it('relays game messages between players', async () => {
    const p1 = await connectAndJoin('R02');
    const p1Listen = nextMessage(p1.ws);
    const p2 = await connectAndJoin('R02');
    await p1Listen;
    const gm = { type: 'request', subtype: 'play_card', source: p1.playerId, seq: 1, card_id: 'Strike_R' };
    p1.ws.send(JSON.stringify(gm));
    const relayed = await nextMessage(p2.ws);
    expect(relayed.card_id).toBe('Strike_R');
    p1.ws.close(); p2.ws.close();
  });

  it('responds to ping with pong', async () => {
    const { ws } = await connectAndJoin('HB');
    ws.send(JSON.stringify({ type: 'ping', seq: 7 }));
    const resp = await nextMessage(ws);
    expect(resp.type).toBe('pong');
    expect(resp.seq).toBe(7);
    ws.close();
  });

  it('delivers directed messages to target only', async () => {
    const p1 = await connectAndJoin('DIR');
    const p1Listen = nextMessage(p1.ws);
    const p2 = await connectAndJoin('DIR');
    await p1Listen;
    const directed = { type: 'invoke', target: p2.playerId, source: p1.playerId, seq: 1, ref_id: 'x', trigger: 'test' };
    p1.ws.send(JSON.stringify(directed));
    const msg = await nextMessage(p2.ws);
    expect(msg.type).toBe('invoke');
    expect(msg.target).toBe(p2.playerId);
    p1.ws.close(); p2.ws.close();
  });

  it('notifies when a player disconnects', async () => {
    const p1 = await connectAndJoin('DC');
    const p1Listen = nextMessage(p1.ws);
    const p2 = await connectAndJoin('DC');
    await p1Listen;
    p2.ws.close();
    const leftMsg = await nextMessage(p1.ws);
    expect(leftMsg.type).toBe('player_left');
    expect(leftMsg.playerId).toBe(p2.playerId);
    p1.ws.close();
  });
});
