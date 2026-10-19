import { useEffect, useLayoutEffect, useRef } from "react";

export type CardTone = "role" | "common" | "rare" | "legendary" | "empty";

export type GameCardContent = {
  // role: 직업카드(이름 → 그림 → 효과), character: 인물카드(이름 → 그림 → 설명/효과/해시태그)
  layout: "role" | "character";
  title: string;
  tone: CardTone;
  // 카드 일러스트. 아직 없으면 symbol을 크게 보여준다.
  image?: string;
  symbol?: string;
  description?: string;
  effect: string;
  hashtags?: string[];
  action?: { label: string; disabled?: boolean; onClick: () => void };
};

// 바깥 테두리 색으로만 등급을 구분하고, 나머지는 양피지 톤으로 통일한다.
const TONE_OUTER: Record<CardTone, string> = {
  role: "border-[#5a3a22]",
  common: "border-[#3f6b3a]",
  rare: "border-[#2f5689]",
  legendary: "border-[#6b3f8a]",
  empty: "border-[#5a3a22]",
};

const INNER_LINE = "border-[0.6cqw] border-[#dcc6a3]";

function CardArt({ card }: { card: GameCardContent }) {
  return (
    <div
      className={`relative flex-[1.15] overflow-hidden rounded-[3cqw] bg-[#efe2cb] ${INNER_LINE}`}
    >
      {card.image ? (
        <img src={card.image} alt="" className="size-full object-contain" />
      ) : (
        <div className="absolute inset-[3cqw] flex items-center justify-center rounded-[2cqw] border-[0.5cqw] border-dashed border-[#d3b98f]">
          <span
            aria-hidden
            className="relative text-[24cqw] leading-none text-[#dcc39c]"
          >
            {card.symbol ?? "?"}
            <span className="absolute -left-[12cqw] top-[6cqw] text-[7cqw]">
              ✦
            </span>
            <span className="absolute -left-[7cqw] top-[14cqw] text-[4cqw]">
              ✦
            </span>
            <span className="absolute -right-[11cqw] top-[9cqw] text-[8cqw]">
              ✦
            </span>
            <span className="absolute -right-[13cqw] top-[2cqw] text-[4cqw]">
              ✦
            </span>
          </span>
        </div>
      )}
    </div>
  );
}

// 글자와 여백을 카드 폭(cqw) 기준으로 잡아서 작은 카드와 확대 카드가 같은 모양이 된다.
// 직업카드와 인물카드는 겉보기 크기가 같도록 같은 비율(CARD_ASPECT)을 쓴다.
const CARD_ASPECT = "aspect-[5/7]";

export function GameCard({
  card,
  interactive = true,
}: {
  card: GameCardContent;
  // 작은 미리보기에서는 카드 안 버튼을 누를 수 없게 한다.
  interactive?: boolean;
}) {
  // 직업카드: 이중 테두리, 이름 → 그림 → 효과 (색은 인물카드와 같은 양피지 톤)
  if (card.layout === "role") {
    return (
      <div className="@container w-full">
        <article
          className={`${CARD_ASPECT} w-full rounded-[6cqw] border-[1.6cqw] border-[#5a3a22] bg-[#f6ecda] p-[6cqw] text-[#3b2413] shadow-2xl`}
        >
          <div className="flex size-full flex-col items-center gap-[4cqw] rounded-[4cqw] border-[1.6cqw] border-[#5a3a22] px-[4cqw] py-[5cqw]">
            <h2 className="text-[9cqw] leading-none">{card.title}</h2>
            <div className="flex w-full flex-1 items-center justify-center overflow-hidden">
              {card.image ? (
                <img
                  src={card.image}
                  alt=""
                  className="size-full object-contain"
                />
              ) : (
                <span aria-hidden className="text-[22cqw] text-[#dcc39c]">
                  {card.symbol ?? "?"}
                </span>
              )}
            </div>
            <p className="text-center text-[6.5cqw] leading-snug">
              {card.effect}
            </p>
          </div>
        </article>
      </div>
    );
  }

  // 인물카드: 양피지 톤, 이름 → 그림 → 설명/효과/해시태그(+액티브 버튼)
  return (
    <div className="@container w-full">
      <article
        className={`${CARD_ASPECT} w-full rounded-[6cqw] border-[1.6cqw] bg-[#f6ecda] p-[3.5cqw] text-[#3b2413] shadow-2xl ${TONE_OUTER[card.tone]}`}
      >
        <div
          className={`flex size-full flex-col gap-[3.5cqw] rounded-[4cqw] p-[4cqw] ${INNER_LINE}`}
        >
          <h2 className="text-center text-[8cqw] font-bold leading-tight">
            {card.title}
          </h2>
          <CardArt card={card} />
          <div
            className={`flex flex-1 flex-col justify-center gap-[2cqw] rounded-[3cqw] bg-[#f9f2e5] p-[4.5cqw] text-center text-[4.8cqw] leading-snug ${INNER_LINE}`}
          >
            <>
              {card.description && (
                <p className="text-[#6b4a2e]">{card.description}</p>
              )}
              <p>
                <span className="font-bold">카드 효과 : </span>
                {card.effect}
              </p>
              {(card.hashtags?.length || card.action) && (
                <div className="mt-[1cqw] flex items-center justify-center gap-[2cqw]">
                  {card.hashtags && card.hashtags.length > 0 && (
                    <p className="text-[4cqw] font-bold text-[#8a6440]">
                      {card.hashtags.map((tag) => `#${tag}`).join(" ")}
                    </p>
                  )}
                  {card.action && (
                    <button
                      type="button"
                      tabIndex={interactive ? undefined : -1}
                      className={`min-h-11 shrink-0 rounded-[2.5cqw] border-[0.8cqw] border-[#5a3a22] bg-[#fffaf0] px-[3cqw] font-bold transition enabled:hover:bg-white disabled:opacity-40 ${interactive ? "" : "pointer-events-none"}`}
                      disabled={card.action.disabled}
                      onClick={card.action.onClick}
                    >
                      {card.action.label}
                    </button>
                  )}
                </div>
              )}
            </>
          </div>
        </div>
      </article>
    </div>
  );
}

export function CardModal({
  card,
  origin,
  onClose,
}: {
  card: GameCardContent;
  // 카드가 날아오기 시작할 위치(누른 카드의 화면 좌표)
  origin: DOMRect | null;
  onClose: () => void;
}) {
  const slotRef = useRef<HTMLDivElement>(null);
  const flyRef = useRef<HTMLDivElement>(null);

  // 그리기 전에 출발점과 도착점 차이를 재서 애니메이션을 건다.
  useLayoutEffect(() => {
    const slot = slotRef.current;
    const fly = flyRef.current;
    if (!slot || !fly) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    if (origin) {
      const target = slot.getBoundingClientRect();
      const dx =
        origin.left + origin.width / 2 - (target.left + target.width / 2);
      const dy =
        origin.top + origin.height / 2 - (target.top + target.height / 2);
      fly.style.setProperty("--fly-x", `${dx}px`);
      fly.style.setProperty("--fly-y", `${dy}px`);
      fly.style.setProperty("--fly-scale", `${origin.width / target.width}`);
    }
    fly.classList.add("card-fly");
  }, [origin]);

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [onClose]);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={card.title}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 motion-safe:animate-fade-in"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        ref={slotRef}
        className="relative w-[85%] max-w-sm perspective-[1400px] sm:w-[32%]"
      >
        <div ref={flyRef} className="relative transform-3d">
          <div className="backface-hidden">
            <GameCard card={card} />
          </div>
          {/* 회전하는 동안 보이는 카드 뒷면 */}
          <div
            aria-hidden
            className="absolute inset-0 flex rotate-y-180 items-center justify-center rounded-2xl border-[6px] border-[#5a3a22] bg-[#e8d6b8] backface-hidden"
          >
            <span className="text-7xl text-[#c9a978]">✦</span>
          </div>
        </div>
        <button
          type="button"
          aria-label="닫기"
          className="absolute -right-3 -top-3 flex size-11 items-center justify-center rounded-full border-4 border-stone-900 bg-white text-2xl font-bold motion-safe:animate-fade-in-late"
          onClick={onClose}
        >
          ×
        </button>
      </div>
    </div>
  );
}
