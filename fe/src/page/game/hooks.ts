import { useEffect, useState, useSyncExternalStore } from "react";

// 서버가 보낸 종료 시각까지 남은 초. 각 기기 시계로 계산한다.
export function useRemainingSeconds(endsAt: string | null) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!endsAt) return;
    const timer = window.setInterval(() => setNow(Date.now()), 250);
    return () => window.clearInterval(timer);
  }, [endsAt]);
  if (!endsAt) return null;
  return Math.max(0, Math.ceil((new Date(endsAt).getTime() - now) / 1000));
}

const DESKTOP_QUERY = "(min-width: 768px)";

// PC는 마을 주변에 패널을 배치하고, 모바일은 하단 탭으로 패널을 하나씩 보여준다.
export function useIsDesktop() {
  return useSyncExternalStore(
    (onChange) => {
      const media = window.matchMedia(DESKTOP_QUERY);
      media.addEventListener("change", onChange);
      return () => media.removeEventListener("change", onChange);
    },
    () => window.matchMedia(DESKTOP_QUERY).matches,
  );
}
