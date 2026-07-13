export interface GameMessage {
  type: string;
  subtype?: string;
  source?: string;
  seq?: number;
  target?: string;
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

export interface PlayerLeftMessage {
  type: 'player_left';
  playerId: string;
}

export interface QueuePacketMessage extends GameMessage {
  type: 'queue_packet';
  packet_id: string;
  sender_id: string;
  owner_id: string;
  card_id: string;
  resource_hash: string;
  target: string;
}

export interface QueueCompleteMessage extends GameMessage {
  type: 'queue_complete';
  packet_id: string;
  card_id?: string;
  effects: EffectDescription[];
  operation_sequence: OperationStep[];
}

export interface EffectDescription {
  kind: string;
  target: string;
  amount: number;
  card_id?: string;
  relic_id?: string;
  potion_id?: string;
  power_id?: string;
  damage_type?: string;
}

export interface OperationStep {
  step: string;
  card_id?: string;
  source?: string;
  target?: string;
  amount?: number;
}

export interface InvokeMessage extends GameMessage {
  type: 'invoke';
  ref_id: string;
  trigger: string;
  args?: string;
}

export interface InvokeResultMessage extends GameMessage {
  type: 'invoke_result';
  ref_id: string;
  effects: EffectDescription[];
  operation_sequence: OperationStep[];
}

export interface RemotePlayerState {
  hp: number;
  max_hp: number;
  block: number;
  energy: number;
  character_class: string;
  powers: string[];
  power_amounts: number[];
  relics: string[];
  potions: string[];
}

export interface PlayerStateMessage extends GameMessage {
  type: 'player_state';
  player: RemotePlayerState;
}

export interface StageSyncMessage extends GameMessage {
  type: 'stage_sync';
  character?: string;
  seed?: string;
  act?: number;
  monster_ids?: string[];
  monster_hps?: number[];
}

export interface MonsterIntentMessage extends GameMessage {
  type: 'monster_intent';
  monster_id: string;
  intent: string;
  damage: number;
  hits: number;
  target_id?: string;
}

export interface CombatResultMessage extends GameMessage {
  type: 'combat_result';
  monster_id: string;
  effects: EffectDescription[];
  operation_sequence: OperationStep[];
}

export interface EventResultMessage extends GameMessage {
  type: 'event_result';
  event_id: string;
  effects: EffectDescription[];
}

export interface PlayerReadyMessage extends GameMessage {
  type: 'player_ready';
  character: string;
}

export interface HelloMessage extends GameMessage {
  type: 'hello';
  ip: string;
  port: number;
  peers: { id: string; ip: string; port: number }[];
}

export interface ResourceRegistryMessage extends GameMessage {
  type: 'resource_registry';
  cards: string[];
  relics: string[];
  powers: string[];
  potions: string[];
  characters: string[];
}

export interface ReferenceMigrateMessage extends GameMessage {
  type: 'reference_migrate';
  ref_id: string;
  resource_type: string;
  resource_id: string;
  resource_hash: string;
}

export type RelayMessage =
  | ConnectedMessage
  | JoinMessage
  | RoomStateMessage
  | PlayerJoinedMessage
  | PlayerLeftMessage
  | GameMessage
  | QueuePacketMessage
  | QueueCompleteMessage
  | InvokeMessage
  | InvokeResultMessage
  | PlayerStateMessage
  | StageSyncMessage
  | MonsterIntentMessage
  | CombatResultMessage
  | EventResultMessage
  | PlayerReadyMessage
  | HelloMessage
  | ResourceRegistryMessage
  | ReferenceMigrateMessage;

export type ClientMessage = RelayMessage;
export type ServerMessage = RelayMessage;
