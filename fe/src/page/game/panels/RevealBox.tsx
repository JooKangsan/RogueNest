import type { ReactNode } from "react";

// 라운드 시작 전 안내 박스. 줄(lines)이 위에서부터 하나씩 날아온다.
export default function RevealBox({
  lines,
  firstDelayMs = 150,
  intervalMs = 450,
}: {
  lines: ReactNode[];
  firstDelayMs?: number;
  intervalMs?: number;
}) {
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/30 p-4">
      <div className="flex aspect-[4/5] w-[75%] max-w-xs flex-col items-center justify-center gap-[6%] overflow-hidden rounded-2xl border-[6px] border-stone-900 bg-stone-50/95 p-6 text-center text-3xl leading-snug text-stone-900 shadow-2xl motion-safe:animate-page-in sm:w-[28%]">
        {lines.map((line, index) => (
          <div
            key={index}
            className="motion-safe:animate-row-fly-in"
            style={{ animationDelay: `${firstDelayMs + index * intervalMs}ms` }}
          >
            {line}
          </div>
        ))}
      </div>
    </div>
  );
}
