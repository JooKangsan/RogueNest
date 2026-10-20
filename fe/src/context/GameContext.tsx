import {
  createContext,
  useCallback,
  useContext,
  useState,
  type PropsWithChildren,
} from "react";
import { useNavigate } from "react-router-dom";
import { createRoomRequest, joinRoomRequest } from "./gameApi";
import { readStoredJoin } from "./gameSession";
import type {
  Alert,
  ChatMessage,
  ConnectionState,
  MyCard,
  MyVote,
  Notice,
  Player,
  Role,
  Room,
  RoundSummary,
  Wallet,
} from "./gameTypes";
import { useRoomConnection } from "./useRoomConnection";

type GameContextValue = {
  nickname: string;
  setNickname: (nickname: string) => void;
  room: Room | null;
  role: Role | null;
  wallet: Wallet | null;
  myVote: MyVote | null;
  // 보유한 인물 카드 (본인만 앎)
  myCard: MyCard | null;
  // 살인마가 이번 밤에 고른 살해 대상 닉네임 (본인만 앎)
  myKillTarget: string | null;
  notices: Notice[];
  dismissNotice: (id: number) => void;
  // 화면 가운데 크게 띄울 알림. 맨 앞 것부터 하나씩 보여준다.
  alerts: Alert[];
  dismissAlert: (id: number) => void;
  hasSession: boolean;
  myPlayer: Player | undefined;
  chat: ChatMessage[];
  connection: ConnectionState;
  isConnected: boolean;
  busy: boolean;
  error: string;
  setError: (error: string) => void;
  createRoom: (roomName: string, password: string) => Promise<boolean>;
  joinRoom: (roomName: string, password: string) => Promise<boolean>;
  publish: (action: string, body: unknown) => void;
  // 마지막 게임의 최종 결과. 대기실로 돌아간 뒤에도 순위 창을 닫기 전까지 남는다.
  finalResult: RoundSummary | null;
  // summary: 마지막 라운드 결과 / ranking: 순위 창 / dismissed: 닫음
  finalStage: FinalStage;
  setFinalStage: (stage: FinalStage) => void;
  leaveRoom: () => Promise<void>;
};

export type FinalStage = "summary" | "ranking" | "dismissed";

const GameContext = createContext<GameContextValue | null>(null);

export function GameProvider({ children }: PropsWithChildren) {
  const navigate = useNavigate();
  const [nickname, setNickname] = useState(
    () => readStoredJoin()?.nickname ?? "",
  );
  const [hasSession, setHasSession] = useState(() => readStoredJoin() !== null);
  const [myNickname, setMyNickname] = useState("");
  const [room, setRoom] = useState<Room | null>(null);
  const [role, setRole] = useState<Role | null>(null);
  const [wallet, setWallet] = useState<Wallet | null>(null);
  const [myVote, setMyVote] = useState<MyVote | null>(null);
  const [myCard, setMyCard] = useState<MyCard | null>(null);
  const [myKillTarget, setMyKillTarget] = useState<string | null>(null);
  const [notices, setNotices] = useState<Notice[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const dismissAlert = useCallback(
    (id: number) =>
      setAlerts((current) => current.filter((alert) => alert.id !== id)),
    [],
  );
  const dismissNotice = useCallback(
    (id: number) =>
      setNotices((current) => current.filter((notice) => notice.id !== id)),
    [],
  );
  const [chat, setChat] = useState<ChatMessage[]>([]);
  const [connection, setConnection] = useState<ConnectionState>("idle");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const {
    connectToRoom,
    publish,
    leaveRoom: disconnectFromRoom,
  } = useRoomConnection({
    navigate,
    setRoom,
    setRole,
    setWallet,
    setMyVote,
    setMyCard,
    setMyKillTarget,
    setNotices,
    setAlerts,
    setChat,
    setConnection,
    setHasSession,
    setNickname,
    setMyNickname,
    setError,
  });

  const createRoom = async (roomName: string, password: string) => {
    setBusy(true);
    setError("");
    try {
      const created = await createRoomRequest(roomName, password);
      const joined = await joinRoomRequest(
        created.roomName,
        nickname,
        password,
      );
      setRoom(null);
      connectToRoom(joined);
      return true;
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "방을 만들지 못했습니다.",
      );
      return false;
    } finally {
      setBusy(false);
    }
  };

  const joinRoom = async (roomName: string, password: string) => {
    setBusy(true);
    setError("");
    try {
      const joined = await joinRoomRequest(roomName, nickname, password);
      setRoom(null);
      connectToRoom(joined);
      return true;
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "방에 입장하지 못했습니다.",
      );
      return false;
    } finally {
      setBusy(false);
    }
  };

  // 게임이 끝나면(GAME_OVER) 최종 결과를 붙잡아 둔다. 서버가 대기실로 돌려도 순위 창을 계속 보여주기 위해서다.
  // 다음 게임이 시작되거나 방을 나가면 지운다.
  const [finalResult, setFinalResult] = useState<RoundSummary | null>(null);
  const [finalStage, setFinalStage] = useState<FinalStage>("summary");
  if (room?.phase === "GAME_OVER" && room.roundSummary && !finalResult) {
    setFinalResult(room.roundSummary);
    setFinalStage(room.rankingShown ? "ranking" : "summary");
  } else if (
    room?.phase === "GAME_OVER" &&
    room.rankingShown &&
    finalStage === "summary"
  ) {
    // 방장이 "다음"을 누르면 모두의 화면이 순위로 넘어간다.
    setFinalStage("ranking");
  } else if (
    finalResult &&
    room?.phase === "WAITING" &&
    finalStage === "summary"
  ) {
    // 방장이 "다음"을 누르기 전에 방이 대기 상태가 되면(자동 진행 등) 바로 순위를 보여준다.
    setFinalStage("ranking");
  } else if (
    finalResult &&
    (!room || (room.phase !== "GAME_OVER" && room.phase !== "WAITING"))
  ) {
    setFinalResult(null);
  }

  const myPlayer = room?.players.find(
    (player) => player.nickname === myNickname,
  );

  return (
    <GameContext.Provider
      value={{
        nickname,
        setNickname,
        room,
        role,
        wallet,
        myVote,
        myCard,
        myKillTarget,
        notices,
        dismissNotice,
        alerts,
        dismissAlert,
        hasSession,
        myPlayer,
        chat,
        connection,
        isConnected: connection === "connected",
        busy,
        error,
        setError,
        createRoom,
        joinRoom,
        publish,
        finalResult,
        finalStage,
        setFinalStage,
        leaveRoom: disconnectFromRoom,
      }}
    >
      {children}
    </GameContext.Provider>
  );
}

// The context hook is intentionally colocated with its provider and context.
// eslint-disable-next-line react-refresh/only-export-components
export function useGame() {
  const context = useContext(GameContext);
  if (!context) throw new Error("useGame must be used within GameProvider");
  return context;
}
