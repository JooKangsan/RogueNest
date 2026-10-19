import { useCallback, useState } from "react";
import type { Role } from "../../../context/gameTypes";
import { CardModal, GameCard, type GameCardContent } from "../GameCard";
import { ROLE_INFO, getRoleImage } from "../roles";
import { lightPanel } from "../styles";

function roleCard(role: Role | null): GameCardContent {
  if (!role) {
    return {
      layout: "role",
      title: "직업",
      tone: "empty",
      symbol: "✦",
      effect: "직업이 아직 배정되지 않았어요.",
    };
  }
  const info = ROLE_INFO[role];
  return {
    layout: "role",
    title: info.name,
    tone: "role",
    image: getRoleImage(role),
    symbol: "✦",
    effect: info.cardText,
  };
}

// TODO: 보유한 인물 카드가 생기면 cards.json 데이터(이름/등급/설명/효과/시너지/이미지)로 채운다.
const EMPTY_CHARACTER_CARD: GameCardContent = {
  layout: "character",
  title: "인물카드",
  tone: "empty",
  symbol: "?",
  effect: "보유한 인물 카드가 없어요. 상점에서 골드로 뽑을 수 있어요.",
};

// 작게 보이는 카드. 누르면 그 자리에서 커지며 모달로 뜬다.
function MiniCard({
  label,
  card,
  onOpen,
  children,
}: {
  label: string;
  card: GameCardContent;
  onOpen: (origin: DOMRect) => void;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-2">
      <h3 className="font-bold">{label}</h3>
      <button
        type="button"
        aria-label={`${label} 크게 보기: ${card.title}`}
        className="w-full rounded-[6%] transition hover:-translate-y-1 hover:brightness-105 focus-visible:-translate-y-1"
        onClick={(event) => onOpen(event.currentTarget.getBoundingClientRect())}
      >
        <GameCard card={card} interactive={false} />
      </button>
      {children}
    </div>
  );
}

export default function CardsPanel({
  role,
  freeReroll = null,
}: {
  role: Role | null;
  // 무료로 다시 뽑을 수 있을 때만 넘긴다. 없으면 다시 뽑기 버튼은 비활성.
  // TODO: 인물 카드 구현 때 아래 두 경우에 연결한다.
  //  - 다알아 박사님 보유 (일반 → 희귀, 희귀 → 전설 1회)
  //  - 과거인 시너지 (다음 라운드 인물 카드 1회 무료)
  freeReroll?: { onClick: () => void } | null;
}) {
  const [openCard, setOpenCard] = useState<{
    card: GameCardContent;
    origin: DOMRect | null;
  } | null>(null);
  const closeCard = useCallback(() => setOpenCard(null), []);

  const jobCard = roleCard(role);
  const characterCard = EMPTY_CHARACTER_CARD;

  return (
    <section aria-label="내 카드" className={`${lightPanel} p-3`}>
      <div className="grid grid-cols-2 items-start gap-3">
        <MiniCard
          label="직업카드"
          card={jobCard}
          onOpen={(origin) => setOpenCard({ card: jobCard, origin })}
        >
          <button
            type="button"
            disabled={!freeReroll}
            title={
              freeReroll
                ? "보유한 인물 카드를 무료로 다시 뽑습니다."
                : "다알아 박사님을 보유하거나 과거인 시너지 혜택이 있으면 사용할 수 있어요."
            }
            className="min-h-11 w-full rounded-md bg-blue-700 text-sm font-semibold text-white disabled:opacity-40"
            onClick={freeReroll?.onClick}
          >
            다시 뽑기
          </button>
        </MiniCard>
        <MiniCard
          label="인물카드"
          card={characterCard}
          onOpen={(origin) => setOpenCard({ card: characterCard, origin })}
        >
          {/* TODO: 인물 카드 공개는 인물 카드 구현 때 연결한다. */}
          <button
            type="button"
            disabled
            className="min-h-11 w-full rounded-md bg-green-700 text-sm font-semibold text-white disabled:opacity-40"
          >
            카드 공개
          </button>
        </MiniCard>
      </div>

      {openCard && (
        <CardModal
          card={openCard.card}
          origin={openCard.origin}
          onClose={closeCard}
        />
      )}
    </section>
  );
}
