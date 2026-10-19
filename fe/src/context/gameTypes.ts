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
    | "THIEF_KILLED"
    // 늑대인간이 도적을 암살했다
    | "THIEF_ASSASSINATED"
    // 마녀가 라운드를 끝냈다
    | "WITCH";
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
  // 장마로 강탈과 살인마 행동이 막힌 남은 턴 수
  rainTurns: number;
  // 발동 중인 시너지와 참여자 닉네임. 효과는 공개하지 않는다.
  // 발동한 시너지만 지금 단계의 효과(effect)가 온다.
  synergies: { name: string; effect: string; nicknames: string[] }[];
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
  // 카드 UI 하단 사용 버튼 이름. 사용할 수 없는 카드면 null
  actionLabel: string | null;
  // 사용할 때 대상을 골라야 하는지
  actionTargeted: boolean;
  // 무료 다시 뽑기로 나올 등급. 없으면 null
  freeRerollTier: "일반" | "희귀" | "전설" | null;
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

// 본인 보유 인물 카드. activated: 이번 라운드에 카드 사용 효과를 썼는지
// bonusRerollTier: 시너지(통모)로 받은 무료 다시 뽑기 등급, freeDraw: 다음 뽑기 1회 무료(과거인)
export type MyCard = {
  cardId: string | null;
  activated: boolean;
  bonusRerollTier: "일반" | "희귀" | "전설" | null;
  freeDraw: boolean;
};

// 화면 가운데 크게 띄우는 알림. 밤 사이 개인 소식(강탈 등)과 방 전체 공지(장마, 시너지 발동 등)
// tone: LOSS(강탈당함) / GAIN(강탈함) / KILL(살해함) / DEATH(살해당함) / INFO(막음·실패) / NOTICE(전체 공지)
export type AlertTone = "LOSS" | "GAIN" | "KILL" | "DEATH" | "INFO" | "NOTICE";
export type Alert = {
  id: number;
  tone: AlertTone;
  title: string;
  lines: string[];
};

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
