import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import ChatPanel from "../../components/ChatPanel";
import ConnectingScreen from "../../components/ConnectingScreen";
import FinalRankingPanel from "../../components/FinalRankingPanel";
import { useGame } from "../../context/GameContext";
import type {
  Alert,
  AlertTone,
  CardDefinition,
  Player,
  Role,
  Room,
  VoteResult,
} from "../../context/gameTypes";
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
import { ROLE_INFO, getCaughtImage, getRoleImage } from "./roles";
import { characterCard } from "./cardContent";
import { CardModal } from "./GameCard";

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
    case "THIEF_ASSASSINATED":
      return "늑대인간이 도적을 암살했습니다";
    case "WITCH":
      return `마녀 ${names}님이 라운드를 끝냈습니다`;
    case "THIEF_LEFT":
      return `도적 ${names}이(가) 도망쳐 검거 처리되었습니다`;
    case "NO_ONE":
      return "아무도 검거되지 않았습니다";
  }
}

// 인물 카드 사용 확인 창 (사용 전 1회)
function ConfirmBox({
  message,
  note,
  onConfirm,
  onCancel,
}: {
  message: string;
  note?: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div
        role="alertdialog"
        aria-modal="true"
        aria-label={message}
        className="w-[88%] max-w-sm rounded-2xl border-4 border-[#5a3a22] bg-[#f6ecda] p-1.5 text-[#3b2413] shadow-2xl motion-safe:animate-page-in"
      >
        <div className="flex flex-col gap-3 rounded-xl border-2 border-[#dcc6a3] p-5 text-center">
          <p className="text-xl font-bold">{message}</p>
          {note && <p className="text-sm text-[#8a6440]">{note}</p>}
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              className="min-h-11 rounded-lg bg-[#5a3a22] font-bold text-[#f6ecda] transition hover:brightness-110"
              onClick={onConfirm}
            >
              사용
            </button>
            <button
              type="button"
              className="min-h-11 rounded-lg border-2 border-[#5a3a22] font-bold transition hover:bg-white"
              onClick={onCancel}
            >
              취소
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// 도적·살인마가 쓰러진 결과는 "도적이 / 그림 / 검거되었습니다." 순서로 보여준다. 누구였는지는 밝히지 않는다.
function caughtLines(
  result: VoteResult,
): { subject: Role; rest: string } | null {
  switch (result.kind) {
    case "THIEF_CAUGHT":
      return { subject: "THIEF", rest: "검거되었습니다." };
    case "THIEF_LEFT":
      return { subject: "THIEF", rest: "도망쳐 검거 처리되었습니다." };
    case "THIEF_KILLED":
      return { subject: "THIEF", rest: "살인마에게 처치되었습니다." };
    case "THIEF_ASSASSINATED":
      return { subject: "THIEF", rest: "늑대인간에게 암살되었습니다." };
    case "MURDERER_CAUGHT":
      return { subject: "MURDERER", rest: "검거되었습니다." };
    default:
      return null;
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

// 큰 알림 모달. 몇 초 뒤 저절로 닫히고, 누르면 바로 닫힌다. 여러 개면 하나씩 이어서 보여준다.
const ALERT_MS = 3_500;

// 잃음(빨강) / 얻음(금색) / 살해함(검붉은) / 살해당함(회색) / 막음·실패(파랑) / 전체 공지(양피지)
const ALERT_STYLE: Record<
  AlertTone,
  { frame: string; inner: string; title: string; icon: string }
> = {
  LOSS: {
    frame: "border-red-900 bg-red-50 text-red-950",
    inner: "border-red-300",
    title: "text-red-600",
    icon: "💸",
  },
  GAIN: {
    frame: "border-amber-700 bg-amber-50 text-amber-950",
    inner: "border-amber-300",
    title: "text-amber-600",
    icon: "💰",
  },
  KILL: {
    frame: "border-red-950 bg-stone-900 text-red-100",
    inner: "border-red-800",
    title: "text-red-400",
    icon: "🔪",
  },
  DEATH: {
    frame: "border-stone-950 bg-stone-800 text-stone-100",
    inner: "border-stone-600",
    title: "text-stone-200",
    icon: "💀",
  },
  INFO: {
    frame: "border-sky-900 bg-sky-50 text-sky-950",
    inner: "border-sky-300",
    title: "text-sky-700",
    icon: "🛡️",
  },
  NOTICE: {
    frame: "border-[#5a3a22] bg-[#f6ecda] text-[#3b2413]",
    inner: "border-[#dcc6a3]",
    title: "",
    icon: "",
  },
};

function AlertModal({
  alert,
  onDismiss,
}: {
  alert: Alert;
  onDismiss: (id: number) => void;
}) {
  useEffect(() => {
    const timeout = window.setTimeout(() => onDismiss(alert.id), ALERT_MS);
    return () => window.clearTimeout(timeout);
  }, [alert.id, onDismiss]);
  const style = ALERT_STYLE[alert.tone];
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 motion-safe:animate-fade-in"
      onClick={() => onDismiss(alert.id)}
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-label={alert.title}
        className={`w-[90%] max-w-lg rounded-2xl border-[6px] p-1.5 shadow-2xl motion-safe:animate-page-in ${style.frame}`}
      >
        <div
          className={`flex flex-col items-center gap-4 rounded-xl border-2 px-6 py-7 text-center ${style.inner}`}
        >
          {alert.tone === "NOTICE" ? (
            // 전체 공지: 작은 머리말 + 큰 본문
            <>
              <h2 className="text-lg font-bold text-[#8a6440]">
                {alert.title}
              </h2>
              {alert.lines.map((line) => (
                <p key={line} className="text-2xl leading-snug">
                  {line}
                </p>
              ))}
            </>
          ) : (
            // 개인 소식: 아이콘과 큰 제목으로 무슨 일인지 한눈에, 자세한 내용은 아래 작게
            <>
              <span aria-hidden className="text-7xl leading-none">
                {style.icon}
              </span>
              <h2 className={`text-5xl font-bold leading-tight ${style.title}`}>
                {alert.title}
              </h2>
              <ul className="flex flex-col gap-1.5 text-2xl leading-snug">
                {alert.lines.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
            </>
          )}
          <p className="text-xs opacity-60">눌러서 닫기</p>
        </div>
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
    myCard: myCardState,
    myKillTarget,
    notices,
    dismissNotice,
    alerts,
    dismissAlert,
    chat,
    myPlayer,
    connection,
    isConnected,
    error,
    publish,
    leaveRoom,
    finalResult,
    finalStage,
    setFinalStage,
  } = useGame();
  const remainingSeconds = useRemainingSeconds(room?.phaseEndsAt ?? null);
  const isDesktop = useIsDesktop();
  const catalog = useCardCatalog();
  const myCard =
    catalog?.cards.find((card) => card.id === myCardState?.cardId) ?? null;
  const [mobileTab, setMobileTab] = useState<MobileTab>("vote");
  // 위쪽 시너지 칩을 눌러 효과를 보는 중인 시너지 이름
  const [openSynergy, setOpenSynergy] = useState<string | null>(null);
  // 광장에서 누른 다른 사람의 공개 카드
  const [publicCard, setPublicCard] = useState<{
    card: CardDefinition;
    origin: DOMRect;
  } | null>(null);
  const closePublicCard = useCallback(() => setPublicCard(null), []);
  // 턴·단계가 바뀌면 카드 확인 창과 시너지 효과 창을 자동으로 닫는다.
  const phaseKey = `${room?.round}-${room?.turn}-${room?.phase}`;
  const [shownPhaseKey, setShownPhaseKey] = useState(phaseKey);
  if (phaseKey !== shownPhaseKey) {
    setShownPhaseKey(phaseKey);
    setPublicCard(null);
    setOpenSynergy(null);
  }
  // 사용 중인 인물 카드: 대상 고르기 → 확인 창. key(라운드-턴)가 지금 턴과 같고 토론 중일 때만 유효하다.
  const [activation, setActivation] = useState<{
    key: string;
    card: CardDefinition;
    target: string | null;
  } | null>(null);

  // 아직 대기 중인 방이면 대기 화면으로 돌려보낸다. (새로고침 후 재접속 등)
  // 단, 게임이 끝나고 최종 순위를 보는 중이면 "다시하기"를 누를 때까지 이 화면에 남는다.
  // (다른 사람이 다시하기를 눌러 방이 대기 상태가 되어도 끌려가지 않는다.)
  const viewingRanking = Boolean(finalResult) && finalStage !== "dismissed";
  const waiting = room?.phase === "WAITING" && !viewingRanking;
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
      <ConnectingScreen
        background={background}
        message={
          connection === "reconnecting"
            ? "게임에 다시 연결하는 중..."
            : "게임에 연결하는 중..."
        }
        error={error}
        onLeave={leaveRoom}
      />
    );
  }

  // 살아 있는 살인마는 밤에 광장 캐릭터를 눌러 살해 대상을 고른다.
  // 장마 중에는 살인마도 움직일 수 없다.
  const murdererActing =
    room.phase === "NIGHT" &&
    role === "MURDERER" &&
    Boolean(myPlayer?.alive) &&
    room.rainTurns === 0;
  const turnKey = `${room.round}-${room.turn}`;
  const caught = room.voteResult ? caughtLines(room.voteResult) : null;
  // 시너지가 꺼지면 열려 있던 효과 창도 닫힌 것으로 본다.
  const shownSynergy =
    room.synergies.find((synergy) => synergy.name === openSynergy) ?? null;
  const canActivate =
    isConnected &&
    room.phase === "DISCUSSION" &&
    Boolean(myPlayer?.alive) &&
    Boolean(myCard?.actionLabel) &&
    !myCardState?.activated;
  const currentActivation =
    canActivate && activation?.key === turnKey ? activation : null;
  const choosingTarget = Boolean(
    currentActivation?.card.actionTargeted && !currentActivation.target,
  );
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
        {room.synergies.length > 0 && (
          <ul
            aria-label="발동 중인 시너지"
            className="flex flex-wrap justify-center gap-1.5"
          >
            {room.synergies.map((synergy) => (
              <li key={synergy.name}>
                <button
                  type="button"
                  title="눌러서 시너지 효과 보기"
                  className="pointer-events-auto min-h-8 rounded-full border-2 border-amber-500 bg-stone-900/85 px-3 py-0.5 text-sm font-bold text-amber-200 shadow transition hover:bg-stone-800 hover:text-amber-100"
                  onClick={() => setOpenSynergy(synergy.name)}
                >
                  ✨ #{synergy.name}
                </button>
              </li>
            ))}
          </ul>
        )}
        {room.rainTurns > 0 && (
          <p
            role="status"
            className="rounded-full border-2 border-sky-900 bg-sky-950/85 px-4 py-1.5 text-center text-sky-100 shadow-lg"
          >
            ☔ 장마 {room.rainTurns}턴 · 강탈과 살인마 행동이 막혀 있어요
          </p>
        )}
        {choosingTarget && currentActivation && (
          <div className="pointer-events-auto flex items-center gap-3 rounded-full border-2 border-[#5a3a22] bg-[#f6ecda] py-1 pl-4 pr-1 text-[#3b2413] shadow-lg">
            <span>
              광장 캐릭터를 눌러 {currentActivation.card.actionLabel} 대상을
              고르세요
            </span>
            <button
              type="button"
              className="min-h-11 rounded-full border-2 border-[#5a3a22] px-3 text-sm font-bold transition hover:bg-white"
              onClick={() => setActivation(null)}
            >
              취소
            </button>
          </div>
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

      {room.phase === "RESULT" &&
        room.voteResult &&
        (caught ? (
          <RevealBox
            key={`caught-${room.round}-${room.turn}`}
            lines={[
              <strong className={ROLE_INFO[caught.subject].className}>
                {caught.subject === "THIEF" ? "도적이" : "살인마가"}
              </strong>,
              <img
                src={getCaughtImage(room.voteResult.kind)}
                alt=""
                className="mx-auto w-56 max-w-full object-contain"
              />,
              caught.rest,
            ]}
          />
        ) : (
          <AnnouncementBox key="result">
            {voteResultMessage(room.voteResult)}
          </AnnouncementBox>
        ))}

      {room.phase === "NIGHT" && (
        // 밤에는 화면을 어둡게 덮는다. 덮개는 클릭을 통과시켜 살인마가 광장 캐릭터를 고를 수 있다.
        <div className="pointer-events-none fixed inset-0 z-30 flex flex-col items-center justify-start gap-3 bg-indigo-950/55 pt-[18%] motion-safe:animate-fade-in">
          <p className="text-4xl text-amber-100 [text-shadow:0_2px_8px_rgba(0,0,0,0.8)]">
            밤이 되었습니다
          </p>
          {role === "MURDERER" && myPlayer?.alive && room.rainTurns > 0 && (
            <p className="rounded-xl border-2 border-sky-900 bg-stone-900/85 px-5 py-3 text-sky-100">
              장마 때문에 오늘 밤은 움직일 수 없어요
            </p>
          )}
          {murdererActing && (
            <div className="pointer-events-auto flex flex-col items-center gap-2 rounded-xl border-2 border-red-900 bg-stone-900/85 px-5 py-3 text-center text-red-100 shadow-xl">
              {myKillTarget ? (
                <>
                  <p className="text-2xl font-bold">
                    🔪 {myKillTarget}님을 살해했습니다
                  </p>
                  <p className="text-sm text-red-100/70">
                    다른 사람들에게는 다음 턴이 시작될 때 알려집니다
                  </p>
                </>
              ) : (
                <p>광장의 캐릭터를 누르면 바로 살해합니다 (되돌릴 수 없어요)</p>
              )}
            </div>
          )}
        </div>
      )}

      {alerts[0] && (
        <AlertModal
          key={alerts[0].id}
          alert={alerts[0]}
          onDismiss={dismissAlert}
        />
      )}

      {shownSynergy && (
        // 발동한 시너지의 지금 단계 효과와 참여자
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 motion-safe:animate-fade-in"
          onClick={() => setOpenSynergy(null)}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-label={`시너지 ${shownSynergy.name}`}
            className="w-[88%] max-w-md rounded-2xl border-4 border-amber-600 bg-stone-900 p-1.5 text-amber-50 shadow-2xl motion-safe:animate-page-in"
          >
            <div className="flex flex-col items-center gap-3 rounded-xl border-2 border-amber-500/50 px-6 py-5 text-center">
              <h2 className="text-3xl font-bold text-amber-300">
                ✨ #{shownSynergy.name}
              </h2>
              <p className="text-2xl leading-snug">{shownSynergy.effect}</p>
              <p className="text-sm text-amber-100/70">
                참여자: {shownSynergy.nicknames.join(", ")}
              </p>
              <p className="text-xs text-amber-100/50">눌러서 닫기</p>
            </div>
          </div>
        </div>
      )}

      {publicCard && (
        <CardModal
          card={characterCard(publicCard.card)}
          origin={publicCard.origin}
          onClose={closePublicCard}
        />
      )}

      {currentActivation && !choosingTarget && (
        <ConfirmBox
          message={`${
            currentActivation.target ? `${currentActivation.target}님에게 ` : ""
          }${currentActivation.card.name}의 "${currentActivation.card.actionLabel}"을(를) 사용할까요?`}
          note={
            currentActivation.card.active
              ? "사용하면 카드가 모두에게 공개돼요."
              : undefined
          }
          onConfirm={() => {
            publish("activate", { target: currentActivation.target });
            setActivation(null);
          }}
          onCancel={() => setActivation(null)}
        />
      )}

      {room.roundSummary &&
        (room.phase === "ROUND_END" ||
          (room.phase === "GAME_OVER" && finalStage === "summary")) && (
          <RoundSummaryPanel
            summary={room.roundSummary}
            myNickname={myPlayer?.nickname}
            myRole={role}
            remainingSeconds={remainingSeconds}
            isConnected={isConnected}
            isHost={Boolean(myPlayer?.host)}
            onNext={() =>
              publish(room.roundSummary?.finalRound ? "ranking" : "next", {})
            }
          />
        )}

      {finalResult && finalStage === "ranking" && (
        <FinalRankingPanel
          summary={finalResult}
          myNickname={myPlayer?.nickname}
          onMain={() => {
            setFinalStage("dismissed");
            void leaveRoom();
          }}
          onRematch={() => {
            // 누른 사람만 대기실에 들어간다. 방이 아직 게임 종료 상태면 서버가 대기 상태로 돌린다.
            // 아직 순위를 보는 다른 사람은 이 화면에 그대로 남고, 대기실 목록에도 보이지 않는다.
            setFinalStage("dismissed");
            publish("rematch", {});
          }}
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
    // 광장 공통: 공개 카드 이름표 → 카드 보기
    const plazaCommon = {
      cardLabel,
      onOpenCard: (cardId: string, origin: DOMRect) => {
        const card = catalog?.cards.find((item) => item.id === cardId);
        if (card) setPublicCard({ card, origin });
      },
    };
    // 무료 다시 뽑기: 카드 효과(다알아 박사님)가 먼저, 없으면 시너지(통모)로 받은 것
    const rerollTier =
      myCard?.freeRerollTier ??
      (myCard ? (myCardState?.bonusRerollTier ?? null) : null);
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
          closeKey={phaseKey}
          role={role}
          card={myCard}
          revealed={Boolean(myPlayer?.revealedCardId)}
          onReveal={
            canDrawCard && myCard ? () => publish("reveal", {}) : undefined
          }
          freeReroll={
            canDrawCard && rerollTier
              ? { tier: rerollTier, onClick: () => publish("reroll", {}) }
              : null
          }
          activated={Boolean(myCardState?.activated)}
          onActivate={
            canActivate && myCard
              ? () =>
                  setActivation({ key: turnKey, card: myCard, target: null })
              : undefined
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
          freeDraw={Boolean(myCardState?.freeDraw)}
        />
      ),
      progress: <ProgressPanel phase={currentRoom.phase} />,
      plaza: (layout: PlazaLayout) =>
        choosingTarget && currentActivation ? (
          <PlazaRing
            players={currentRoom.players}
            myNickname={myPlayer?.nickname}
            layout={layout}
            voteTarget={null}
            onVote={(target) => setActivation({ ...currentActivation, target })}
            canTarget={(player) =>
              player.alive && player.nickname !== myPlayer?.nickname
            }
            actionLabel={` ${currentActivation.card.actionLabel} 대상으로 지정`}
            {...plazaCommon}
          />
        ) : murdererActing && !myKillTarget ? (
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
            {...plazaCommon}
          />
        ) : (
          <PlazaRing
            players={currentRoom.players}
            myNickname={myPlayer?.nickname}
            layout={layout}
            voteTarget={voteTarget}
            onVote={vote}
            canTarget={canTarget}
            {...plazaCommon}
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
