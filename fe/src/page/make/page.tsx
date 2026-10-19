import { useCallback, useEffect, useState } from "react";
import makeBackground from "../../assets/waiting_page.webp";
import HoodCharacter from "../../components/HoodCharacter";
import { avatarColor } from "../../components/avatarColor";
import RoomEntryModal, {
  type RoomEntryMode,
} from "../../components/RoomEntryModal";
import { getRooms } from "../../context/gameApi";
import type { RoomSummary } from "../../context/gameTypes";
import { useGame } from "../../context/GameContext";

const ROOM_LIST_REFRESH_MS = 10_000;

// 카드와 같은 양피지 톤: 진한 갈색 바깥 테두리 + 연갈색 안쪽 테두리
const PARCHMENT_FRAME =
  "rounded-2xl border-4 border-[#5a3a22] bg-[#f6ecda]/95 p-1.5 shadow-2xl";
const INNER_FRAME = "rounded-xl border-2 border-[#dcc6a3]";

function ActionCard({
  title,
  description,
  color,
  onClick,
}: {
  title: string;
  description: string;
  color: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`${PARCHMENT_FRAME} group w-full text-left transition hover:-translate-y-1 hover:shadow-[0_18px_30px_rgba(59,36,19,0.35)] active:scale-[0.98]`}
    >
      <span
        className={`${INNER_FRAME} flex min-h-24 items-center gap-4 px-8 py-3`}
      >
        <HoodCharacter
          pose="head"
          color={color}
          className="w-10 shrink-0 transition group-hover:scale-110"
        />
        <span className="flex flex-col">
          <span className="text-2xl font-bold text-[#3b2413]">{title}</span>
          <span className="text-sm text-[#8a6440]">{description}</span>
        </span>
        <span
          aria-hidden
          className="ml-auto text-2xl text-[#8a6440] transition group-hover:translate-x-1"
        >
          →
        </span>
      </span>
    </button>
  );
}

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
      <div className="flex w-full max-w-5xl motion-safe:animate-page-in flex-col items-center gap-8 md:flex-row md:items-stretch md:justify-center md:gap-[6%]">
        {/* 왼쪽: 방 만들기 / 참여하기 */}
        <div className="flex w-full max-w-sm flex-col justify-center gap-5 md:w-[34%]">
          <ActionCard
            title="방 만들기"
            description="새 방을 열고 친구를 초대해요"
            color={avatarColor(3)}
            onClick={() => openModal("create")}
          />
          <ActionCard
            title="참여하기"
            description="방 이름으로 바로 입장해요"
            color={avatarColor(1)}
            onClick={() => openModal("join")}
          />
          {error && !modal && (
            <p
              className="rounded-lg border-2 border-red-300 bg-red-50/95 px-3 py-2 text-sm text-red-700"
              role="alert"
            >
              {error}
            </p>
          )}
        </div>

        {/* 오른쪽: 방 목록 */}
        <section
          aria-labelledby="room-list-title"
          className={`${PARCHMENT_FRAME} flex h-[64vh] max-h-[560px] min-h-80 w-full max-w-md md:w-[48%]`}
        >
          <div className={`${INNER_FRAME} flex min-h-0 flex-1 flex-col p-4`}>
            <div className="flex items-center gap-3">
              <h2
                id="room-list-title"
                className="flex-1 text-2xl font-bold text-[#3b2413]"
              >
                방 목록
                <span className="ml-2 rounded-full bg-[#5a3a22] px-2.5 py-0.5 align-middle text-sm font-normal text-[#f6ecda]">
                  {rooms.length}
                </span>
              </h2>
              <button
                type="button"
                aria-label="방 목록 새로고침"
                className="flex size-11 items-center justify-center rounded-full border-2 border-[#dcc6a3] bg-[#fffaf0] text-xl text-[#5a3a22] transition hover:bg-white disabled:opacity-60"
                disabled={refreshingRooms}
                onClick={() => void refreshRooms()}
              >
                <span
                  className={refreshingRooms ? "motion-safe:animate-spin" : ""}
                >
                  ↻
                </span>
              </button>
            </div>

            <div className="mt-3 min-h-0 flex-1 overflow-y-auto pr-1">
              {rooms.length === 0 ? (
                <div className="flex h-full flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed border-[#dcc6a3] bg-[#f9f2e5] p-6 text-center">
                  <HoodCharacter
                    pose="head"
                    color="#b8a48a"
                    className="w-16 opacity-70"
                  />
                  <p className="text-lg text-[#5a3a22]">
                    {roomsError || "열려 있는 방이 없어요"}
                  </p>
                  {!roomsError && (
                    <p className="text-sm text-[#8a6440]">
                      방을 만들어 친구들을 불러보세요
                    </p>
                  )}
                </div>
              ) : (
                <ul className="flex flex-col gap-2">
                  {rooms.map((room, index) => {
                    const full = room.playerCount >= room.maxPlayers;
                    return (
                      <li key={room.roomName}>
                        <button
                          type="button"
                          className="group flex min-h-16 w-full items-center gap-3 rounded-xl border-2 border-[#dcc6a3] bg-[#f9f2e5] px-3 py-2 text-left transition enabled:hover:-translate-y-0.5 enabled:hover:border-[#8a6440] enabled:hover:bg-[#fffaf0] enabled:hover:shadow-md disabled:cursor-not-allowed disabled:opacity-55"
                          disabled={busy || full}
                          onClick={() => enterListedRoom(room)}
                        >
                          <HoodCharacter
                            pose="head"
                            color={avatarColor(index)}
                            className="w-10 shrink-0"
                          />
                          <span className="min-w-0 flex-1">
                            <span className="flex items-center gap-1.5">
                              <span className="truncate text-lg font-bold text-[#3b2413]">
                                {room.roomName}
                              </span>
                              {room.passwordRequired && (
                                <span
                                  aria-label="비밀번호 방"
                                  title="비밀번호 방"
                                  className="shrink-0 text-sm"
                                >
                                  🔒
                                </span>
                              )}
                            </span>
                            <span className="text-sm tabular-nums text-[#8a6440]">
                              {full
                                ? "가득 찼어요"
                                : `${room.playerCount} / ${room.maxPlayers}명`}
                            </span>
                          </span>
                          <span className="shrink-0 rounded-full border-2 border-[#5a3a22] px-3 py-1 text-sm font-bold text-[#5a3a22] transition group-enabled:group-hover:bg-[#5a3a22] group-enabled:group-hover:text-[#f6ecda]">
                            {full ? "가득 참" : "입장 →"}
                          </span>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
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
