export type Player = {
  nickname: string;
  host: boolean;
  ready: boolean;
};

export type RoomConfig = {
  roundCount: number;
  coinPrice: number;
  turnDurationSeconds: number;
};

export type Room = {
  roomName: string;
  phase: string;
  players: Player[];
  config: RoomConfig;
  backgroundIndex: number | null;
  round: number;
  turn: number;
  phaseEndsAt: string | null;
  minPlayers: number;
  maxTurns: number;
};

export type Wallet = { gold: number; coins: number };

export type Role = "THIEF" | "MURDERER" | "CITIZEN";

export type RoomSummary = {
  roomName: string;
  playerCount: number;
  maxPlayers: number;
  passwordRequired: boolean;
};

export type ChatMessage = {
  nickname: string;
  message: string;
  sentAt: string;
};

export type JoinResponse = {
  roomName: string;
  nickname: string;
  sessionToken: string;
};

export type RoomEvent = {
  type: string;
  payload: unknown;
  occurredAt: string;
};

export type ConnectionState =
  "idle" | "connecting" | "connected" | "reconnecting";
