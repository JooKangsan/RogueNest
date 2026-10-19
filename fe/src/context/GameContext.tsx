import {
  createContext,
  useContext,
  useState,
  type PropsWithChildren,
} from "react";
import { useNavigate } from "react-router-dom";
import { createRoomRequest, joinRoomRequest } from "./gameApi";
import { readStoredJoin } from "./gameSession";
import type {
  ChatMessage,
  ConnectionState,
  Player,
  Role,
  Room,
  Wallet,
} from "./gameTypes";
import { useRoomConnection } from "./useRoomConnection";

type GameContextValue = {
  nickname: string;
  setNickname: (nickname: string) => void;
  room: Room | null;
  role: Role | null;
  wallet: Wallet | null;
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
  leaveRoom: () => Promise<void>;
};

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
