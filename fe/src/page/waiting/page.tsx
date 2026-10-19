import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import waitingBackground from "../../assets/waiting_page.webp";
import Avatar from "../../components/Avatar";
import { avatarColor } from "../../components/avatarColor";
import ChatPanel from "../../components/ChatPanel";
import { NumberSetting } from "../../components/NumberSetting";
import RoomSettingsModal from "../../components/RoomSettingsModal";
import SquareButton from "../../components/SquareButton";
import { useGame } from "../../context/GameContext";
import type { Player } from "../../context/gameTypes";

const MAX_PLAYERS = 12;

const panelClass =
  "rounded-2xl border-4 border-amber-900/70 bg-amber-50/90 shadow-xl";

function PlayerCard({
  player,
  color,
  isMe,
}: {
  player: Player;
  color: string;
  isMe: boolean;
}) {
  const status = player.host ? "방장" : player.ready ? "준비완료" : "대기 중";
  const statusClass = player.host
    ? "text-amber-700"
    : player.ready
      ? "text-green-700"
      : "text-amber-900/50";

  return (
    <li
      className={`flex min-h-14 items-center gap-3 rounded-lg border-2 bg-amber-100/80 px-3 ${
        isMe ? "border-amber-700" : "border-amber-900/20"
      }`}
    >
      <Avatar color={color} />
      <span className="flex min-w-0 flex-1 items-center gap-1 text-amber-950">
        <span className="truncate">{player.nickname}</span>
        {player.host && <span aria-label="방장">👑</span>}
        {isMe && <span className="text-xs text-amber-900/60">(나)</span>}
      </span>
      <span className={`shrink-0 text-sm ${statusClass}`}>{status}</span>
    </li>
  );
}

export default function WaitingPage() {
  const {
    room,
    myPlayer,
    chat,
    connection,
    isConnected,
    error,
    busy,
    publish,
    leaveRoom,
    setError,
  } = useGame();
  const [roomInfoOpen, setRoomInfoOpen] = useState(false);

  // 방장이 게임을 시작하면 모두 게임 화면으로 이동한다.
  const navigate = useNavigate();
  const started = Boolean(room && room.phase !== "WAITING");
  useEffect(() => {
    if (started) navigate("/game", { replace: true });
  }, [navigate, started]);

  // 브라우저 뒤로가기로 대기 화면을 벗어나면 나가기 버튼과 같이 퇴장 처리한다.
  useEffect(() => {
    const leaveOnHistoryNavigation = () => void leaveRoom();
    window.addEventListener("popstate", leaveOnHistoryNavigation);
    return () =>
      window.removeEventListener("popstate", leaveOnHistoryNavigation);
  }, [leaveRoom]);

  const closeRoomInfo = useCallback(() => setRoomInfoOpen(false), []);

  if (!room) {
    return (
      <section
        className="relative left-1/2 flex min-h-screen w-screen -translate-x-1/2 items-center justify-center bg-cover bg-center text-amber-950"
        style={{ backgroundImage: `url("${waitingBackground}")` }}
        aria-live="polite"
      >
        <p className={`${panelClass} px-6 py-4`}>
          {connection === "reconnecting"
            ? "대기실에 다시 연결하는 중..."
            : "대기실에 연결하는 중..."}
        </p>
      </section>
    );
  }

  const isHost = Boolean(myPlayer?.host);
  const canStart = room.players.length >= room.minPlayers;
  const emptySlots = MAX_PLAYERS - room.players.length;

  return (
    <section
      className="relative left-1/2 min-h-screen w-screen -translate-x-1/2 bg-cover bg-center bg-no-repeat px-4 py-6 md:flex md:items-center md:py-10"
      style={{ backgroundImage: `url("${waitingBackground}")` }}
    >
      <div className="mx-auto grid w-full max-w-6xl gap-4 motion-safe:animate-page-in md:grid-cols-[minmax(0,1fr)_15rem] md:gap-6">
        {/* 왼쪽: 대기인원 + 채팅 */}
        <div className="flex min-h-0 flex-col gap-4">
          <section
            aria-labelledby="players-title"
            className={`${panelClass} p-4`}
          >
            <div className="mb-3 flex items-baseline justify-between gap-3">
              <span className="font-bold truncate text-4xl text-amber-900/70">
                {room.roomName}
              </span>

              <h1
                id="players-title"
                className="text-2xl font-bold text-amber-950"
              >
                대기인원{" "}
                <span className="text-lg font-normal text-amber-900/70">
                  {room.players.length}/{MAX_PLAYERS}
                </span>
              </h1>
            </div>
            <ul className="grid gap-2 sm:grid-cols-2">
              {room.players.map((player, index) => (
                <PlayerCard
                  key={player.nickname}
                  player={player}
                  color={avatarColor(index)}
                  isMe={player.nickname === myPlayer?.nickname}
                />
              ))}
              {Array.from({ length: emptySlots }, (_, index) => (
                <li
                  key={`empty-${index}`}
                  aria-hidden
                  className="hidden min-h-14 rounded-lg border-2 border-dashed border-amber-900/15 sm:block"
                />
              ))}
            </ul>
          </section>

          <ChatPanel
            chat={chat}
            myNickname={myPlayer?.nickname}
            isConnected={isConnected}
            onSend={(message) => publish("chat", { message })}
            className="h-56 md:h-[28vh] md:min-h-48"
          />
        </div>

        {/* 오른쪽: 설정/나가기, 게임 설정, 준비/시작 */}
        <aside className="flex flex-col gap-4 order-first md:order-0 md:justify-between">
          <div className="grid grid-cols-2 gap-3">
            <SquareButton onClick={() => setRoomInfoOpen(true)}>
              설정
            </SquareButton>
            <SquareButton disabled={busy} onClick={() => void leaveRoom()}>
              나가기
            </SquareButton>
          </div>

          <section
            aria-label="게임 설정"
            className={`${panelClass} flex flex-col items-center gap-4 px-3 py-5`}
          >
            <NumberSetting
              label="라운드"
              value={room.config.roundCount}
              min={3}
              max={11}
              step={1}
              suffix=""
              editable={isHost}
              disabled={!isConnected}
              onCommit={(roundCount) =>
                publish("settings", { config: { ...room.config, roundCount } })
              }
            />
            <NumberSetting
              label="코인가격"
              value={room.config.coinPrice}
              min={25}
              max={75}
              step={5}
              suffix="G"
              editable={isHost}
              disabled={!isConnected}
              onCommit={(coinPrice) =>
                publish("settings", { config: { ...room.config, coinPrice } })
              }
            />
            {!isHost && (
              <p className="text-xs text-amber-900/60">
                방장만 변경할 수 있어요.
              </p>
            )}
          </section>

          <div className="flex flex-col gap-2">
            {error && (
              <p
                className="rounded-md bg-red-50/90 px-3 py-2 text-sm text-red-700"
                role="alert"
              >
                {error}
              </p>
            )}
            {connection !== "connected" && (
              <p
                className="rounded-md bg-amber-50/90 px-3 py-2 text-sm text-amber-900"
                aria-live="polite"
              >
                {connection === "reconnecting"
                  ? "다시 연결하는 중..."
                  : "연결하는 중..."}
              </p>
            )}
            {isHost ? (
              <>
                <SquareButton
                  className="min-h-16 text-xl"
                  disabled={!isConnected || !canStart}
                  onClick={() => publish("start", {})}
                >
                  시작하기
                </SquareButton>
                {!canStart && (
                  <p className="text-center text-sm text-amber-950 [text-shadow:0_0_4px_rgb(255_251_235)]">
                    {room.minPlayers - room.players.length}명이 더 필요해요
                  </p>
                )}
              </>
            ) : (
              <SquareButton
                className={`min-h-16 text-xl ${myPlayer?.ready ? "bg-green-100/90! border-green-800/70!" : ""}`}
                disabled={!isConnected}
                onClick={() => publish("ready", !myPlayer?.ready)}
              >
                {myPlayer?.ready ? "준비 취소" : "준비완료"}
              </SquareButton>
            )}
          </div>
        </aside>
      </div>

      {roomInfoOpen && myPlayer && (
        <RoomSettingsModal
          roomName={room.roomName}
          nickname={myPlayer.nickname}
          isHost={isHost}
          isConnected={isConnected}
          error={error}
          onRename={(roomName) => {
            setError("");
            publish("rename", { roomName });
          }}
          onChangeNickname={(nickname) => {
            setError("");
            publish("nickname", { nickname });
          }}
          onClose={closeRoomInfo}
        />
      )}
    </section>
  );
}
