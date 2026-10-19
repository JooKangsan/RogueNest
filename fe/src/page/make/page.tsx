import { useCallback, useEffect, useState } from "react";
import makeBackground from "../../assets/waiting_page.webp";
import RoomEntryModal, {
  type RoomEntryMode,
} from "../../components/RoomEntryModal";
import { getRooms } from "../../context/gameApi";
import type { RoomSummary } from "../../context/gameTypes";
import { useGame } from "../../context/GameContext";

const ROOM_LIST_REFRESH_MS = 10_000;

export default function MakePage() {
  const { hasSession, createRoom, joinRoom, busy, error, setError } = useGame();
  const [modal, setModal] = useState<{
    mode: RoomEntryMode;
    roomName: string;
  } | null>(null);
  const [rooms, setRooms] = useState<RoomSummary[]>([]);
  const [roomsError, setRoomsError] = useState("");
  const [refreshingRooms, setRefreshingRooms] = useState(false);

  const refreshRooms = useCallback(async () => {
    setRefreshingRooms(true);
    try {
      setRooms(await getRooms());
      setRoomsError("");
    } catch (cause) {
      setRoomsError(
        cause instanceof Error
          ? cause.message
          : "방 목록을 불러오지 못했습니다.",
      );
    } finally {
      setRefreshingRooms(false);
    }
  }, []);

  useEffect(() => {
    // 뒤로가기로 방을 나오는 중이면 퇴장이 끝난 뒤에 목록을 불러온다.
    if (hasSession) return;
    const initialRefresh = window.setTimeout(() => void refreshRooms(), 0);
    const interval = window.setInterval(
      () => void refreshRooms(),
      ROOM_LIST_REFRESH_MS,
    );
    return () => {
      window.clearTimeout(initialRefresh);
      window.clearInterval(interval);
    };
  }, [hasSession, refreshRooms]);

  const openModal = (mode: RoomEntryMode, roomName = "") => {
    setError("");
    setModal({ mode, roomName });
  };

  const closeModal = useCallback(() => setModal(null), []);

  const enterListedRoom = (room: RoomSummary) => {
    if (room.passwordRequired) {
      openModal("join", room.roomName);
      return;
    }
    setError("");
    void joinRoom(room.roomName, "");
  };

  return (
    <section
      className="relative left-1/2 flex min-h-screen w-screen -translate-x-1/2 items-center justify-center bg-cover bg-center bg-no-repeat px-4 py-10"
      style={{ backgroundImage: `url("${makeBackground}")` }}
    >
      <div className="flex w-full max-w-5xl motion-safe:animate-page-in flex-col items-center gap-8 md:flex-row md:items-center md:justify-center md:gap-[8%]">
        <div className="flex w-[70%] max-w-60 flex-col items-center gap-10 md:w-[32%]">
          <button
            type="button"
            className="h-30 w-full rounded-4xl border-4 border-amber-900/70 bg-white text-xl font-bold text-amber-950 shadow-lg transition hover:bg-amber-100 active:scale-95"
            onClick={() => openModal("create")}
          >
            방 만들기
          </button>
          <button
            type="button"
            className="h-30 w-full rounded-4xl border-4 border-amber-900/70 bg-white text-xl font-bold text-amber-950 shadow-lg transition hover:bg-amber-100 active:scale-95"
            onClick={() => openModal("join")}
          >
            참여하기
          </button>
          {error && !modal && (
            <p
              className="rounded-md bg-red-50/90 px-3 py-2 text-sm text-red-700"
              role="alert"
            >
              {error}
            </p>
          )}
        </div>

        <section
          aria-labelledby="room-list-title"
          className="flex h-[60vh] max-h-140 min-h-80 w-full max-w-md flex-col rounded-2xl border-4 border-amber-900/70 bg-amber-50/90 p-4 shadow-xl md:w-[45%]"
        >
          <div className="flex items-center justify-between gap-3">
            <h2
              id="room-list-title"
              className="flex-1 text-center text-2xl font-bold text-amber-950"
            >
              방 목록
            </h2>
            <button
              type="button"
              aria-label="방 목록 새로고침"
              className="flex size-11 items-center justify-center rounded-md text-xl text-amber-900 hover:bg-amber-100 disabled:opacity-40"
              disabled={refreshingRooms}
              onClick={() => void refreshRooms()}
            >
              ↻
            </button>
          </div>

          <div className="mt-3 flex-1 overflow-y-auto rounded-xl border-2 border-amber-900/40 bg-white/70">
            {rooms.length === 0 ? (
              <p className="flex h-full items-center justify-center p-4 text-center text-sm text-amber-900/60">
                {roomsError || "열려 있는 방이 없습니다."}
              </p>
            ) : (
              <ul className="divide-y divide-amber-900/15">
                {rooms.map((room) => {
                  const full = room.playerCount >= room.maxPlayers;
                  return (
                    <li key={room.roomName}>
                      <button
                        type="button"
                        className="flex min-h-14 w-full items-center gap-3 px-4 py-2 text-left transition hover:bg-amber-100/70 disabled:cursor-not-allowed disabled:opacity-50"
                        disabled={busy || full}
                        onClick={() => enterListedRoom(room)}
                      >
                        <span className="min-w-0 flex-1 truncate font-semibold text-amber-950">
                          {room.roomName}
                        </span>
                        {room.passwordRequired && (
                          <span aria-label="비밀번호 방" title="비밀번호 방">
                            🔒
                          </span>
                        )}
                        <span className="text-sm tabular-nums text-amber-900/70">
                          {full
                            ? "가득 참"
                            : `${room.playerCount}/${room.maxPlayers}`}
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </section>
      </div>

      {modal && (
        <RoomEntryModal
          mode={modal.mode}
          initialRoomName={modal.roomName}
          busy={busy}
          error={error}
          onSubmit={modal.mode === "create" ? createRoom : joinRoom}
          onClose={closeModal}
        />
      )}
    </section>
  );
}
