import type { Role, VoteResult } from "../../context/gameTypes";

// 직업카드 일러스트: assets/roles/{image}
const roleImages = Object.fromEntries(
  Object.entries(
    import.meta.glob<string>("../../assets/roles/*.{webp,png,jpg,jpeg}", {
      eager: true,
      import: "default",
    }),
  ).map(([path, url]) => [path.split("/").pop()!, url]),
);

export const ROLE_INFO: Record<
  Role,
  {
    name: string;
    className: string;
    darkClassName: string;
    // 직업카드에 들어가는 한두 줄 효과 문구
    cardText: string;
    image: string;
  }
> = {
  THIEF: {
    name: "도적",
    className: "text-red-700",
    darkClassName: "text-red-400",
    cardText:
      "매 턴 종료 시 골드를 강탈합니다. 끝까지 살아남으면 코인 1개를 얻습니다.",
    image: "thief.webp",
  },
  MURDERER: {
    name: "살인마",
    className: "text-purple-800",
    darkClassName: "text-purple-300",
    cardText: "매 턴 종료 후 1G를 써서 밤에 1명 살해합니다.",
    image: "murderer.webp",
  },
  CITIZEN: {
    name: "시민",
    className: "text-green-700",
    darkClassName: "text-green-400",
    cardText: "토론과 투표로 도적을 찾아 검거합니다.",
    image: "citizen.webp",
  },
};

export function getRoleImage(role: Role) {
  return roleImages[ROLE_INFO[role].image];
}

// 투표 결과 안내에 띄우는 쓰러진 도적/살인마 그림
const CAUGHT_IMAGES: Partial<Record<VoteResult["kind"], string>> = {
  THIEF_CAUGHT: "thief-caught.webp",
  THIEF_LEFT: "thief-caught.webp",
  THIEF_KILLED: "thief-caught.webp",
  THIEF_ASSASSINATED: "thief-caught.webp",
  MURDERER_CAUGHT: "murderer-caught.webp",
};

export function getCaughtImage(kind: VoteResult["kind"]): string | undefined {
  const image = CAUGHT_IMAGES[kind];
  return image ? roleImages[image] : undefined;
}
