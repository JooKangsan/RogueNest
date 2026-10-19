import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import ChatPanel from "../../components/ChatPanel";
import { useGame } from "../../context/GameContext";
import type { Room } from "../../context/gameTypes";
import { getGameBackground } from "./backgrounds";
import { useIsDesktop, useRemainingSeconds } from "./hooks";
import CardsPanel from "./panels/CardsPanel";
import MyInfoPanel from "./panels/MyInfoPanel";
import ProgressPanel from "./panels/ProgressPanel";
import TurnHeader from "./panels/TurnHeader";
import VotePanel from "./panels/VotePanel";
import { ROLE_INFO } from "./roles";

type MobileTab = "vote" | "cards" | "shop" | "chat";

const MOBILE_TABS: { id: MobileTab; label: string }[] = [
  { id: "vote", label: "투표" },
  { id: "cards", label: "내 카드" },
  { id: "shop", label: "상점" },
  { id: "chat", label: "채팅" },
];

function AnnouncementBox({ children }: { children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/30 p-4">
      <div className="flex aspect-5/4 w-[80%] max-w-sm items-center justify-center border-[6px] border-stone-900 bg-stone-100/95 p-6 text-center text-3xl leading-relaxed text-stone-900 shadow-2xl motion-safe:animate-page-in sm:w-[38%]">
        <p>{children}</p>
      </div>
    </div>
  );
}

export default function GamePage() {
  const navigate = useNavigate();
  const {
    room,
    role,
    wallet,
    chat,
    myPlayer,
    connection,
    isConnected,
    error,
    publish,
    leaveRoom,
  } = useGame();
  const remainingSeconds = useRemainingSeconds(room?.phaseEndsAt ?? null);
  const isDesktop = useIsDesktop();
  const [mobileTab, setMobileTab] = useState<MobileTab>("vote");

  // 아직 대기 중인 방이면 대기 화면으로 돌려보낸다. (새로고침 후 재접속 등)
  const waiting = room?.phase === "WAITING";
  useEffect(() => {
    if (waiting) navigate("/waiting", { replace: true });
  }, [navigate, waiting]);

  // 브라우저 뒤로가기로 게임 화면을 벗어나면 퇴장 처리한다.
  useEffect(() => {
    const leaveOnHistoryNavigation = () => void leaveRoom();
    window.addEventListener("popstate", leaveOnHistoryNavigation);
    return () =>
      window.removeEventListener("popstate", leaveOnHistoryNavigation);
  }, [leaveRoom]);

  const background = getGameBackground(room?.backgroundIndex ?? null);

  if (!room) {
    return (
      <section
        className="relative left-1/2 flex min-h-screen w-screen -translate-x-1/2 items-center justify-center bg-cover bg-center"
        style={{ backgroundImage: `url("${background}")` }}
      >
        <p className="rounded-2xl border-4 border-amber-900/70 bg-amber-50/90 px-6 py-4 text-amber-950">
          {connection === "reconnecting"
            ? "게임에 다시 연결하는 중..."
            : "게임에 연결하는 중..."}
        </p>
      </section>
    );
  }

  const panels = renderPanels(room);

  return (
    <section
      className="relative left-1/2 h-dvh w-screen -translate-x-1/2 overflow-hidden bg-cover bg-center bg-no-repeat"
      style={{ backgroundImage: `url("${background}")` }}
    >
      {isDesktop ? (
        // PC: 마을 그림 주변에 패널 배치
        <>
          <div className="absolute left-[1.5%] top-[2%] w-[38%] max-w-xl">
            {panels.vote}
          </div>
          <div className="absolute left-1/2 top-[2%] -translate-x-1/2">
            {panels.header}
          </div>
          <div className="absolute right-[1.5%] top-[2%] w-[24%] max-w-xs">
            {panels.cards}
          </div>
          <div className="absolute bottom-[2%] left-[1.5%] max-h-[70%] w-[22%] max-w-xs overflow-y-auto rounded-xl">
            {panels.shop}
          </div>
          <div className="absolute bottom-[2%] right-[1.5%] flex w-[24%] max-w-xs flex-col gap-3">
            {panels.progress}
            {panels.chat("h-[28vh] min-h-56")}
          </div>
        </>
      ) : (
        // 모바일: 상단 라운드/턴/타이머, 중앙 마을, 하단 탭
        <div className="flex h-full flex-col">
          <div className="flex justify-center p-2">{panels.header}</div>
          <div className="min-h-0 flex-1" />
          <div className="max-h-[60%] overflow-y-auto px-2 pb-2">
            {mobileTab === "vote" && (
              <div className="flex flex-col gap-2">
                {panels.vote}
                {panels.progress}
              </div>
            )}
            {mobileTab === "cards" && panels.cards}
            {mobileTab === "shop" && panels.shop}
            {mobileTab === "chat" && panels.chat("h-[45vh]")}
          </div>
          <nav
            aria-label="게임 메뉴"
            className="grid grid-cols-4 border-t-2 border-amber-900/60 bg-stone-900/90"
          >
            {MOBILE_TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                aria-current={mobileTab === tab.id ? "page" : undefined}
                className={`min-h-14 text-sm ${
                  mobileTab === tab.id
                    ? "font-bold text-amber-400"
                    : "text-amber-50/70"
                }`}
                onClick={() => setMobileTab(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </nav>
        </div>
      )}

      {error && (
        <p
          className="absolute bottom-[18%] left-1/2 z-30 -translate-x-1/2 rounded-md bg-red-50/95 px-4 py-2 text-sm text-red-700 shadow-lg md:bottom-[3%]"
          role="alert"
        >
          {error}
        </p>
      )}

      {room.phase === "PREPARE" && (
        <AnnouncementBox key="role">
          당신의 역할은
          <br />
          {role ? (
            <strong className={ROLE_INFO[role].className}>
              {ROLE_INFO[role].name}
            </strong>
          ) : (
            "..."
          )}
          입니다.
        </AnnouncementBox>
      )}

      {room.phase === "ROUND_START" && (
        <AnnouncementBox key="round-start">
          {room.round}라운드가
          <br />
          시작됩니다!
        </AnnouncementBox>
      )}
    </section>
  );

  function renderPanels(currentRoom: Room) {
    return {
      header: (
        <TurnHeader room={currentRoom} remainingSeconds={remainingSeconds} />
      ),
      vote: (
        <VotePanel
          players={currentRoom.players}
          myNickname={myPlayer?.nickname}
        />
      ),
      cards: <CardsPanel role={role} />,
      shop: (
        <MyInfoPanel
          wallet={wallet}
          coinPrice={currentRoom.config.coinPrice}
          isConnected={isConnected}
          onExchangeCoin={() => publish("exchange", {})}
        />
      ),
      progress: <ProgressPanel phase={currentRoom.phase} />,
      chat: (className: string) => (
        <ChatPanel
          chat={chat}
          myNickname={myPlayer?.nickname}
          isConnected={isConnected}
          onSend={(message) => publish("chat", { message })}
          tone="dark"
          title="채팅 창"
          emptyText="아직 대화가 없어요."
          className={className}
        />
      ),
    };
  }
}
