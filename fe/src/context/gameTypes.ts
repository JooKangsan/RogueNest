export type Player = {
  nickname: string;
  host: boolean;
  ready: boolean;
  // 이번 턴 투표(기권 포함)를 마쳤는지
  voted: boolean;
  // 이번 턴 받은 표 수
  votes: number;
  alive: boolean;
  // 최종 투표에서 광장 중앙에 섰는지
  onTrial: boolean;
  // 공개한 인물 카드 id. 비공개면 null
  revealedCardId: string | null;
};

// 투표 결과 (RESULT 단계에서만 온다)
export type VoteResult = {
  kind:
    | "THIEF_CAUGHT"
    | "MURDERER_CAUGHT"
    | "CITIZEN_DIED"
    | "NO_ONE"
    // 라운드 중 도적이 나가면 검거로 처리한다
    | "THIEF_LEFT"
    // 살인마가 밤에 도적을 처치했다
    | "THIEF_KILLED";
  nicknames: string[];
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
  voteResult: VoteResult | null;
  roundSummary: RoundSummary | null;
  // 지난밤 살인마에게 살해된 플레이어. 다음 턴에 공개한다.
  nightVictim: string | null;
};

// 라운드 종료 결과 (ROUND_END / GAME_OVER 단계에서만 온다)
export type RoundSummary = {
  round: number;
  thiefCaught: boolean;
  finalRound: boolean;
  rows: { nickname: string; role: Role; gold: number; coins: number }[];
};

export type Wallet = { gold: number; coins: number };

// cards.json 의 인물 카드 정의 (GET /api/meta/cards)
export type CardDefinition = {
  id: string;
  name: string;
  tier: "일반" | "희귀" | "전설";
  price: number;
  effect: string;
  description: string | null;
  image: string | null;
  active: boolean;
  synergyTags: string[];
};

export type SynergyDefinition = {
  id: string;
  name: string;
  cards: string[];
  requirement: string;
};

export type CardCatalog = {
  cards: CardDefinition[];
  synergies: SynergyDefinition[];
};

// 본인 투표. target=대상 닉네임, abstain=기권
export type MyVote = { target: string | null; abstain: boolean };

// 본인에게만 오는 알림 (강탈 결과 등)
export type Notice = { id: number; message: string };

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
