import { useEffect, useState } from "react";
import { getCardCatalog } from "../../context/gameApi";
import type { CardCatalog } from "../../context/gameTypes";

// 카드 데이터는 바뀌지 않는 정적 데이터라 한 번만 받아 두고 같이 쓴다.
let cached: CardCatalog | null = null;
let pending: Promise<CardCatalog> | null = null;

export function useCardCatalog() {
  const [catalog, setCatalog] = useState<CardCatalog | null>(cached);

  useEffect(() => {
    if (cached) return;
    pending ??= getCardCatalog();
    let active = true;
    pending
      .then((loaded) => {
        cached = loaded;
        if (active) setCatalog(loaded);
      })
      .catch(() => {
        pending = null;
      });
    return () => {
      active = false;
    };
  }, []);

  return catalog;
}
