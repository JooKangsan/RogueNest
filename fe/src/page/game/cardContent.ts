import type { CardDefinition } from "../../context/gameTypes";
import { getCardImage } from "./cardImages";
import type { GameCardContent } from "./GameCard";

// 인물 카드 데이터 → 카드 UI 내용. 내 카드와 다른 사람이 공개한 카드에 같이 쓴다.
const TIER_TONE = {
  일반: "common",
  희귀: "rare",
  전설: "legendary",
} as const;

// 사용 버튼 상태. onActivate는 지금 사용할 수 있을 때만 넘긴다.
export type CardUse = { activated: boolean; onActivate?: () => void };

export function characterCard(
  card: CardDefinition,
  use?: CardUse,
): GameCardContent {
  return {
    layout: "character",
    title: `${card.name} (${card.tier})`,
    tone: TIER_TONE[card.tier],
    image: getCardImage(card.image),
    symbol: "?",
    description: card.description ?? undefined,
    effect: card.effect,
    hashtags: card.synergyTags,
    // 사용 버튼: 토론 단계에서만, 라운드당 1회. 확인 창은 게임 화면에서 띄운다.
    action: card.actionLabel
      ? {
          label: use?.activated
            ? `${card.actionLabel} (사용함)`
            : card.actionLabel,
          disabled: !use?.onActivate || use.activated,
          onClick: () => use?.onActivate?.(),
        }
      : undefined,
  };
}
