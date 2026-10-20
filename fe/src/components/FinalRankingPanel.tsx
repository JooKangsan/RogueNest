import type { RoundSummary } from "../context/gameTypes";

// 게임이 모두 끝난 뒤의 최종 순위. 코인 많은 순, 같으면 남은 골드 많은 순으로 보여준다.
// 순위는 코인으로만 매긴다. 코인이 같으면 공동 순위(공동 우승).
export default function FinalRankingPanel({
  summary,
  myNickname,
  onMain,
  onRematch,
}: {
  summary: RoundSummary;
  myNickname: string | undefined;
  // 메인화면으로: 방에서 나가 방 목록으로
  onMain: () => void;
  // 다시하기: 같은 방 대기실로 돌아가 다음 판 준비
  onRematch: () => void;
}) {
  const rows = [...summary.rows].sort(
    (a, b) => b.coins - a.coins || b.gold - a.gold,
  );
  const rankOf = (coins: number) =>
    rows.findIndex((row) => row.coins === coins) + 1;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="final-ranking-title"
        className="flex w-[92%] max-w-xl flex-col gap-3 rounded-xl border-4 border-stone-900 bg-stone-100/95 p-4 text-stone-900 shadow-2xl motion-safe:animate-page-in sm:p-6"
      >
        <h2
          id="final-ranking-title"
          className="rounded-md border-4 border-stone-900 bg-white py-2 text-center text-3xl font-bold"
        >
          순위
        </h2>

        <ol className="flex flex-col gap-2 overflow-hidden">
          {rows.map((row, index) => {
            const rank = rankOf(row.coins);
            const isMe = row.nickname === myNickname;
            return (
              <li
                key={row.nickname}
                style={{ animationDelay: `${200 + index * 350}ms` }}
                className={`motion-safe:animate-row-fly-in grid grid-cols-[3rem_minmax(0,1fr)_5rem_5.5rem] items-center gap-2 rounded-md border-4 px-3 py-2 text-lg sm:grid-cols-[3.5rem_minmax(0,1fr)_6rem_6.5rem] sm:text-xl ${
                  isMe
                    ? "border-amber-500 bg-amber-50"
                    : "border-stone-900 bg-white"
                }`}
              >
                <span className="font-bold">
                  {rank === 1 ? "🏆" : `${rank}.`}
                </span>
                <span className="truncate">
                  {row.nickname}
                  {isMe && (
                    <span className="ml-1 text-sm text-amber-700">(나)</span>
                  )}
                </span>
                <span className="text-right tabular-nums">⭐ {row.coins}</span>
                <span className="text-right tabular-nums">
                  남은 <b>{row.gold}G</b>
                </span>
              </li>
            );
          })}
        </ol>

        <div className="mt-1 grid grid-cols-2 gap-3">
          <button
            type="button"
            onClick={onMain}
            className="min-h-14 rounded-md border-4 border-stone-900 bg-white text-xl font-bold transition hover:bg-amber-50 active:scale-95"
          >
            메인화면으로
          </button>
          <button
            type="button"
            onClick={onRematch}
            className="min-h-14 rounded-md border-4 border-stone-900 bg-white text-xl font-bold transition hover:bg-amber-50 active:scale-95"
          >
            다시하기
          </button>
        </div>
      </section>
    </div>
  );
}
