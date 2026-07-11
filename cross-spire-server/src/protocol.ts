export interface GameMessage {
  type: 'request' | 'state_sync';
  subtype: string;
  source: string;
  seq: number;
}

export interface PlayCardRequest extends GameMessage {
  type: 'request';
  subtype: 'play_card';
  card_id: string;
  target: string;
  cost_paid: number;
}

export interface EndTurnRequest extends GameMessage {
  type: 'request';
  subtype: 'end_turn';
}

export interface RemotePlayerState {
  hp: number;
  max_hp: number;
  block: number;
  relics: string[];
  potions: (string | null)[];
}

export interface RemotePlayerSync extends GameMessage {
  type: 'state_sync';
  subtype: 'remote_player';
  player: RemotePlayerState;
}

export interface ConnectedMessage {
  type: 'connected';
  playerId: string;
}

export interface JoinMessage {
  type: 'join';
  code: string;
}

export interface RoomStateMessage {
  type: 'room_state';
  code: string;
  players: string[];
}

export interface PlayerJoinedMessage {
  type: 'player_joined';
  playerId: string;
}

export type ClientMessage = GameMessage | JoinMessage;
export type ServerMessage = ConnectedMessage | RoomStateMessage | PlayerJoinedMessage | GameMessage;
