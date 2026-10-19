import {
  useCallback,
  useEffect,
  useRef,
  type Dispatch,
  type SetStateAction,
} from "react";
import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import type {
  ChatMessage,
  ConnectionState,
  JoinResponse,
  Role,
  Room,
  RoomEvent,
  Wallet,
} from "./gameTypes";
import { clearStoredJoin, readStoredJoin, saveStoredJoin } from "./gameSession";

type RoomConnectionOptions = {
  navigate: (path: string) => void;
  setRoom: Dispatch<SetStateAction<Room | null>>;
  setRole: Dispatch<SetStateAction<Role | null>>;
  setWallet: Dispatch<SetStateAction<Wallet | null>>;
  setChat: Dispatch<SetStateAction<ChatMessage[]>>;
  setConnection: Dispatch<SetStateAction<ConnectionState>>;
  setHasSession: Dispatch<SetStateAction<boolean>>;
  setNickname: Dispatch<SetStateAction<string>>;
  setMyNickname: Dispatch<SetStateAction<string>>;
  setError: Dispatch<SetStateAction<string>>;
};

export function useRoomConnection({
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
}: RoomConnectionOptions) {
  const clientRef = useRef<Client | null>(null);
  const identityRef = useRef<JoinResponse | null>(null);
  const restoredRef = useRef(false);
  const roomSubscriptionRef = useRef<StompSubscription | null>(null);
  // 방 이름이 바뀌면 이벤트 핸들러 안에서 다시 구독해야 하므로 ref를 거쳐 자기 자신을 참조한다.
  const handleRoomEventRef = useRef<(frame: IMessage) => void>(() => {});
  const onFrame = useCallback(
    (frame: IMessage) => handleRoomEventRef.current(frame),
    [],
  );

  const subscribeRoom = useCallback(
    (client: Client, roomName: string) => {
      roomSubscriptionRef.current?.unsubscribe();
      roomSubscriptionRef.current = client.subscribe(
        `/topic/room/${roomName}`,
        onFrame,
      );
      client.publish({
        destination: `/app/rooms/${roomName}/snapshot`,
        body: "{}",
      });
    },
    [onFrame],
  );

  const updateIdentity = useCallback((changes: Partial<JoinResponse>) => {
    const current = identityRef.current;
    if (!current) return null;
    const next = { ...current, ...changes };
    identityRef.current = next;
    saveStoredJoin(next);
    return next;
  }, []);

  const handleRoomEvent = useCallback(
    (frame: IMessage) => {
      const event = JSON.parse(frame.body) as RoomEvent;
      if (event.type === "roomRenamed") {
        const { roomName } = event.payload as { roomName: string };
        const client = clientRef.current;
        if (updateIdentity({ roomName }) && client?.connected) {
          subscribeRoom(client, roomName);
        }
      } else if (event.type === "nicknameChanged") {
        const { nickname } = event.payload as { nickname: string };
        updateIdentity({ nickname });
        setNickname(nickname);
        setMyNickname(nickname);
      } else if (event.type === "wallet") {
        setWallet(event.payload as Wallet);
      } else if (event.type === "role") {
        setRole((event.payload as { role: Role }).role);
      } else if (event.type === "error") {
        setError((event.payload as { message: string }).message);
      } else if (event.type === "room") {
        setRoom(event.payload as Room);
      } else if (event.type === "chat") {
        setChat((messages) => [
          ...messages.slice(-99),
          event.payload as ChatMessage,
        ]);
      } else if (event.type === "snapshot") {
        const snapshot = event.payload as {
          room: Room;
          role: Role | null;
          wallet: Wallet | null;
        };
        setRoom(snapshot.room);
        setRole(snapshot.role);
        setWallet(snapshot.wallet);
      }
    },
    [
      setChat,
      setError,
      setMyNickname,
      setNickname,
      setRole,
      setWallet,
      setRoom,
      subscribeRoom,
      updateIdentity,
    ],
  );

  useEffect(() => {
    handleRoomEventRef.current = handleRoomEvent;
  }, [handleRoomEvent]);

  const connectToRoom = useCallback(
    (join: JoinResponse) => {
      // 연결은 항상 하나만 유지한다. 남아 있으면 채팅 등 이벤트를 중복으로 받는다.
      void clientRef.current?.deactivate();
      restoredRef.current = true;
      saveStoredJoin(join);
      identityRef.current = join;
      setHasSession(true);
      setNickname(join.nickname);
      setMyNickname(join.nickname);
      setError("");
      setChat([]);
      setRole(null);
      setWallet(null);
      setConnection("connecting");
      navigate("/waiting");
      const scheme = window.location.protocol === "https:" ? "wss" : "ws";
      const client = new Client({
        brokerURL: `${scheme}://${window.location.host}/ws`,
        connectHeaders: { "x-session-token": join.sessionToken },
        heartbeatIncoming: 10_000,
        heartbeatOutgoing: 10_000,
        reconnectDelay: 3_000,
        onConnect: () => {
          setConnection("connected");
          setError("");
          // 이전 연결의 구독은 연결과 함께 사라졌으므로 버린다.
          roomSubscriptionRef.current = null;
          client.subscribe("/user/queue/private", onFrame);
          // 재연결 시에도 방 이름이 바뀌었을 수 있으므로 최신 정보로 구독한다.
          subscribeRoom(client, identityRef.current?.roomName ?? join.roomName);
        },
        onWebSocketClose: () => {
          setConnection((current) =>
            current === "idle" ? "idle" : "reconnecting",
          );
        },
        onStompError: (frame) => {
          const message =
            frame.headers.message ??
            "WebSocket 요청 처리 중 오류가 발생했습니다.";
          if (
            frame.body.includes("INVALID_SESSION") ||
            frame.body.includes("ROOM_NOT_FOUND")
          ) {
            clearStoredJoin();
            identityRef.current = null;
            setHasSession(false);
            setRoom(null);
            setRole(null);
            setWallet(null);
            setConnection("idle");
            setError(
              "재접속할 수 있는 시간이 지났거나 세션이 만료됐습니다. 다시 입장해 주세요.",
            );
            navigate("/make");
            return;
          }
          setError(message);
        },
        onWebSocketError: () => {
          setError(
            "WebSocket 연결에 실패했습니다. 백엔드 서버가 실행 중인지 확인해 주세요.",
          );
        },
      });
      clientRef.current = client;
      client.activate();
    },
    [
      navigate,
      onFrame,
      setChat,
      setConnection,
      setRole,
      setWallet,
      setError,
      setHasSession,
      setMyNickname,
      setNickname,
      setRoom,
      subscribeRoom,
    ],
  );

  // 새로고침 시 저장된 세션으로 한 번만 재접속한다.
  // connectToRoom은 navigate가 바뀔 때마다 새로 만들어지므로 ref로 재실행을 막는다.
  useEffect(() => {
    if (restoredRef.current) return;
    const storedJoin = readStoredJoin();
    if (!storedJoin) {
      restoredRef.current = true;
      return;
    }
    const timeout = window.setTimeout(() => {
      connectToRoom(storedJoin);
    }, 0);
    return () => window.clearTimeout(timeout);
  }, [connectToRoom]);

  useEffect(
    () => () => {
      void clientRef.current?.deactivate();
    },
    [],
  );

  const publish = useCallback(
    (action: string, body: unknown) => {
      const current = identityRef.current;
      const client = clientRef.current;
      if (!current || !client?.connected) {
        setError("WebSocket에 연결된 뒤 다시 시도해 주세요.");
        return;
      }
      client.publish({
        destination: `/app/rooms/${current.roomName}/${action}`,
        body: JSON.stringify(body),
      });
    },
    [setError],
  );

  const leaveRoom = useCallback(async () => {
    const client = clientRef.current;
    const current = identityRef.current;
    if (client?.connected && current) {
      client.publish({
        destination: `/app/rooms/${current.roomName}/leave`,
        body: "{}",
      });
    }
    clientRef.current = null;
    identityRef.current = null;
    clearStoredJoin();
    // 소켓을 닫은 뒤 화면을 전환해야 방 목록에 내가 나간 결과가 반영된다.
    if (client) await client.deactivate();
    setHasSession(false);
    setRoom(null);
    setRole(null);
    setWallet(null);
    setChat([]);
    setConnection("idle");
    setError("");
    if (window.location.pathname !== "/make") navigate("/make");
  }, [
    navigate,
    setChat,
    setConnection,
    setError,
    setHasSession,
    setRole,
    setRoom,
    setWallet,
  ]);

  return { connectToRoom, publish, leaveRoom };
}
