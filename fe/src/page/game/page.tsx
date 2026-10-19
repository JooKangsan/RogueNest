import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import ChatPanel from "../../components/ChatPanel";
import { useGame } from "../../context/GameContext";
import type { Player, Room, VoteResult } from "../../context/gameTypes";
import { getGameBackground } from "./backgrounds";
import { useIsDesktop, useRemainingSeconds } from "./hooks";
import CardsPanel from "./panels/CardsPanel";
import MyInfoPanel from "./panels/MyInfoPanel";
import { useCardCatalog } from "./useCardCatalog";
import PlazaRing, { type PlazaLayout } from "./panels/PlazaRing";
import ProgressPanel from "./panels/ProgressPanel";
import RevealBox from "./panels/RevealBox";
import RoundSummaryPanel from "./panels/RoundSummaryPanel";
import TurnHeader from "./panels/TurnHeader";
import VotePanel from "./panels/VotePanel";
import { ROLE_INFO, getRoleImage } from "./roles";

const CARD_SHOP_PHASES = [
  "PREPARE",
  "ROUND_START",
  "DISCUSSION",
  "VOTE",
  "RESULT",
  "NIGHT",
];

type MobileTab = "vote" | "cards" | "shop" | "chat";

// 배경 그림 크기. 광장 레이어를 배경과 똑같이 "cover"로 맞추는 데 쓴다.
const BACKGROUND_WIDTH = 1536;
const BACKGROUND_HEIGHT = 864;

// 배경 그림 기준 광장(우물) 위치. 모든 배경이 같은 구도다.
const DESKTOP_PLAZA: PlazaLayout = {
  centerX: 50,
  centerY: 62,
  radiusX: 15,
  radiusY: 13,
  characterWidth: "3.6%",
};

// 모바일은 배경 좌우가 크게 잘리므로 보이는 영역 기준으로 배치한다.
const MOBILE_PLAZA: PlazaLayout = {
  centerX: 50,
  centerY: 50,
  radiusX: 38,
  radiusY: 30,
  characterWidth: "13%",
};

const MOBILE_TABS: { id: MobileTab; label: string }[] = [
  { id: "vote", label: "투표" },
  { id: "cards", label: "내 카드" },
  { id: "shop", label: "상점" },
  { id: "chat", label: "채팅" },
];

// 투표 결과 안내. 투표로 죽은 시민은 직업을 공개하지 않는다(CLAUDE.md).
function voteResultMessage(result: VoteResult) {
  const names = result.nicknames.join(", ");
  switch (result.kind) {
    case "THIEF_CAUGHT":
      return `도적 ${names} 검거!`;
    case "MURDERER_CAUGHT":
      return "살인마가 검거되었습니다";
    case "CITIZEN_DIED":
      return `${names}님이 광장에서 끌려나갔습니다`;
    case "THIEF_KILLED":
      return "살인마가 도적을 처치했습니다";
    case "THIEF_LEFT":
      return `도적 ${names}이(가) 도망쳐 검거 처리되었습니다`;
    case "NO_ONE":
      return "아무도 검거되지 않았습니다";
  }
}

function AnnouncementBox({ children }: { children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/30 p-4">
      <div className="flex aspect-5/4 w-[80%] max-w-sm items-center justify-center border-[6px] border-stone-900 bg-stone-100/95 p-6 text-center text-3xl leading-relaxed text-stone-900 shadow-2xl motion-safe:animate-page-in sm:w-[38%]">
        <p>{children}</p>
      </div>
    </div>
  );
}

// 본인에게만 온 알림(강탈 결과 등). 몇 초 뒤 저절로 사라진다.
const NOTICE_MS = 4_000;

function NoticeToast({
  id,
  message,
  onDismiss,
}: {
  id: number;
  message: string;
  onDismiss: (id: number) => void;
}) {
  useEffect(() => {
    const timeout = window.setTimeout(() => onDismiss(id), NOTICE_MS);
    return () => window.clearTimeout(timeout);
  }, [id, onDismiss]);
  return (
    <p
      role="status"
      className="rounded-full border-2 border-[#5a3a22] bg-[#f6ecda]/95 px-4 py-1.5 text-sm text-[#3b2413] shadow-lg motion-safe:animate-page-in"
    >
      {message}
    </p>
  );
}

export default function GamePage() {
  const navigate = useNavigate();
  const {
    room,
    role,
    wallet,
    myVote,
    myCardId,
    myKillTarget,
    notices,
    dismissNotice,
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
  const catalog = useCardCatalog();
  const myCard = catalog?.cards.find((card) => card.id === myCardId) ?? null;
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

  // 살아 있는 살인마는 밤에 광장 캐릭터를 눌러 살해 대상을 고른다.
  const murdererActing =
    room.phase === "NIGHT" && role === "MURDERER" && Boolean(myPlayer?.alive);
  const panels = renderPanels(room);

  return (
    <section
      className="relative left-1/2 h-dvh w-screen -translate-x-1/2 overflow-hidden bg-cover bg-center bg-no-repeat"
      style={{ backgroundImage: `url("${background}")` }}
    >
      {isDesktop ? (
        // PC: 마을 그림 주변에 패널 배치
        <>
          {/* 광장 둘레에 선 플레이어들. 배경과 같은 크기·위치(cover)의 레이어에 그려 우물을 중심으로 선다. */}
          <div
            className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2"
            style={{
              width: `max(100%, calc(100dvh * ${BACKGROUND_WIDTH} / ${BACKGROUND_HEIGHT}))`,
              aspectRatio: `${BACKGROUND_WIDTH} / ${BACKGROUND_HEIGHT}`,
            }}
          >
            {panels.plaza(DESKTOP_PLAZA)}
          </div>
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
          <div className="relative min-h-40 flex-1">
            {panels.plaza(MOBILE_PLAZA)}
          </div>
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

      {/* 지난밤 살해 소식(다음 턴 토론 동안)과 개인 알림 */}
      <div className="pointer-events-none absolute left-1/2 top-[12%] z-30 flex w-[90%] max-w-md -translate-x-1/2 flex-col items-center gap-2">
        {room.nightVictim && room.phase === "DISCUSSION" && (
          <p
            key={`victim-${room.round}-${room.turn}`}
            role="status"
            className="rounded-full border-2 border-red-900 bg-stone-900/85 px-4 py-1.5 text-center text-red-100 shadow-lg motion-safe:animate-page-in"
          >
            밤사이 {room.nightVictim}님이 살해당했습니다
          </p>
        )}
        {notices.map((notice) => (
          <NoticeToast
            key={notice.id}
            id={notice.id}
            message={notice.message}
            onDismiss={dismissNotice}
          />
        ))}
      </div>

      {room.phase === "PREPARE" && (
        // 직업 공개: "당신의 역할은" → 직업 그림 → "OO입니다."
        <RevealBox
          key={`role-${room.round}`}
          lines={[
            "당신의 역할은",
            role && getRoleImage(role) ? (
              <img
                src={getRoleImage(role)}
                alt=""
                className="mx-auto size-36 object-contain sm:size-40"
              />
            ) : (
              <span className="text-6xl text-stone-300">?</span>
            ),
            <>
              <strong className={role ? ROLE_INFO[role].className : ""}>
                {role ? ROLE_INFO[role].name : "..."}
              </strong>
              입니다.
            </>,
          ]}
        />
      )}

      {room.phase === "ROUND_START" && (
        <RevealBox
          key={`round-start-${room.round}`}
          firstDelayMs={50}
          intervalMs={200}
          lines={["라운드를", "준비해주세요"]}
        />
      )}

      {room.phase === "RESULT" && room.voteResult && (
        <AnnouncementBox key="result">
          {voteResultMessage(room.voteResult)}
        </AnnouncementBox>
      )}

      {room.phase === "NIGHT" && (
        // 밤에는 화면을 어둡게 덮는다. 덮개는 클릭을 통과시켜 살인마가 광장 캐릭터를 고를 수 있다.
        <div className="pointer-events-none fixed inset-0 z-30 flex flex-col items-center justify-start gap-3 bg-indigo-950/55 pt-[18%] motion-safe:animate-fade-in">
          <p className="text-4xl text-amber-100 [text-shadow:0_2px_8px_rgba(0,0,0,0.8)]">
            밤이 되었습니다
          </p>
          {murdererActing && (
            <div className="pointer-events-auto flex flex-col items-center gap-2 rounded-xl border-2 border-red-900 bg-stone-900/85 px-5 py-3 text-center text-red-100 shadow-xl">
              <p>
                {myKillTarget
                  ? `${myKillTarget}님을 노리고 있습니다`
                  : "광장의 캐릭터를 눌러 살해할 사람을 고르세요"}
              </p>
              {myKillTarget && (
                <button
                  type="button"
                  disabled={!isConnected}
                  className="min-h-11 rounded-md border-2 border-red-200/60 px-4 text-sm transition enabled:hover:bg-red-900/60 disabled:opacity-50"
                  onClick={() => publish("kill", { target: null })}
                >
                  취소
                </button>
              )}
            </div>
          )}
        </div>
      )}

      {room.roundSummary &&
        (room.phase === "ROUND_END" || room.phase === "GAME_OVER") && (
          <RoundSummaryPanel
            summary={room.roundSummary}
            myNickname={myPlayer?.nickname}
            myRole={role}
            remainingSeconds={remainingSeconds}
            isConnected={isConnected}
            isHost={Boolean(myPlayer?.host)}
            onNext={() => publish("next", {})}
          />
        )}
    </section>
  );

  function renderPanels(currentRoom: Room) {
    // 토론과 최종 투표 중에만, 살아 있을 때만 투표/기권할 수 있다. 서버도 그 외 투표는 거부한다.
    const finalVote = currentRoom.phase === "VOTE";
    const canVote =
      isConnected &&
      (currentRoom.phase === "DISCUSSION" || finalVote) &&
      Boolean(myPlayer?.alive);
    // 중앙에 2명 이상(동표)이면 그들에게만 투표할 수 있다.
    const tieTrial =
      finalVote && currentRoom.players.filter((p) => p.onTrial).length > 1;
    const canTarget = (player: Player) =>
      player.alive && (!tieTrial || player.onTrial);
    const vote = canVote
      ? (target: string) => publish("vote", { target, abstain: false })
      : undefined;
    const abstain = canVote
      ? () => publish("vote", { target: null, abstain: true })
      : undefined;
    const voteTarget = myVote?.target ?? null;
    const cardLabel = (cardId: string) => {
      const card = catalog?.cards.find((item) => item.id === cardId);
      return card ? `${card.name}(${card.tier})` : undefined;
    };
    // 인물 카드는 라운드 중(역할 배정 후) 언제든, 생존자만 뽑을 수 있다. 서버도 같은 규칙으로 거부한다.
    const canDrawCard =
      isConnected &&
      Boolean(myPlayer?.alive) &&
      CARD_SHOP_PHASES.includes(currentRoom.phase);

    return {
      header: (
        <TurnHeader room={currentRoom} remainingSeconds={remainingSeconds} />
      ),
      vote: (
        <VotePanel
          players={currentRoom.players}
          myNickname={myPlayer?.nickname}
          voteTarget={voteTarget}
          abstained={Boolean(myVote?.abstain)}
          onVote={vote}
          onAbstain={abstain}
          canTarget={canTarget}
          finalVote={finalVote}
        />
      ),
      cards: (
        <CardsPanel
          role={role}
          card={myCard}
          revealed={Boolean(myPlayer?.revealedCardId)}
          onReveal={
            canDrawCard && myCard ? () => publish("reveal", {}) : undefined
          }
        />
      ),
      shop: (
        <MyInfoPanel
          wallet={wallet}
          coinPrice={currentRoom.config.coinPrice}
          isConnected={isConnected}
          onExchangeCoin={() => publish("exchange", {})}
          catalog={catalog}
          onDrawCard={
            canDrawCard ? (tier) => publish("draw", { tier }) : undefined
          }
        />
      ),
      progress: <ProgressPanel phase={currentRoom.phase} />,
      plaza: (layout: PlazaLayout) =>
        murdererActing ? (
          <PlazaRing
            players={currentRoom.players}
            myNickname={myPlayer?.nickname}
            layout={layout}
            voteTarget={myKillTarget}
            onVote={
              isConnected ? (target) => publish("kill", { target }) : undefined
            }
            canTarget={(player) =>
              player.alive && player.nickname !== myPlayer?.nickname
            }
            actionLabel=" 살해 대상으로 지정"
            cardLabel={cardLabel}
          />
        ) : (
          <PlazaRing
            players={currentRoom.players}
            myNickname={myPlayer?.nickname}
            layout={layout}
            voteTarget={voteTarget}
            onVote={vote}
            canTarget={canTarget}
            cardLabel={cardLabel}
          />
        ),
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
