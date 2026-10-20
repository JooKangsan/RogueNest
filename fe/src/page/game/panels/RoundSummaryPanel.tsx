import type { Role, RoundSummary } from "../../../context/gameTypes";
import { ROLE_INFO } from "../roles";

// 결과 표를 1등부터 한 줄씩 공개한다.
const FIRST_ROW_DELAY_MS = 300;
const ROW_INTERVAL_MS = 450;

// 도적은 끝까지 살아남으면, 시민(살인마 포함)은 도적을 잡으면 그 라운드 승리
function wonRound(role: Role | null, thiefCaught: boolean) {
  if (!role) return false;
  return role === "THIEF" ? !thiefCaught : thiefCaught;
}

export default function RoundSummaryPanel({
  summary,
  myNickname,
  myRole,
  remainingSeconds,
  isConnected,
  isHost,
  onNext,
}: {
  summary: RoundSummary;
  myNickname: string | undefined;
  myRole: Role | null;
  remainingSeconds: number | null;
  isConnected: boolean;
  // 방장만 "다음 라운드" / 마지막 라운드의 "다음"(최종 순위 공개)을 누를 수 있다.
  isHost: boolean;
  onNext: () => void;
}) {
  // 마지막 줄까지 나온 뒤에 버튼과 안내를 보여준다.
  const footerDelay =
    FIRST_ROW_DELAY_MS + summary.rows.length * ROW_INTERVAL_MS + 200;
  // 마지막 라운드도 그 라운드의 승패를 보여주고, 최종 순위는 "다음"을 누르면 따로 보여준다.
  const iWon = wonRound(myRole, summary.thiefCaught);

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="round-summary-title"
        className="w-[92%] max-w-2xl rounded-xl border-4 border-stone-900 bg-stone-100/95 p-4 text-stone-900 shadow-2xl motion-safe:animate-page-in sm:p-6"
      >
        <h2
          id="round-summary-title"
          className="rounded-md border-4 border-stone-900 bg-white py-2 text-center text-3xl font-bold"
        >
          <span className={iWon ? "text-amber-600" : "text-stone-500"}>
            {iWon ? "승리" : "패배"}
          </span>
        </h2>
        <p className="mt-2 text-center text-sm text-stone-600">
          {`${summary.round}라운드${summary.finalRound ? "(마지막)" : ""} 결과 · ${
            summary.thiefCaught
              ? "도적이 검거되었습니다"
              : "도적이 살아남았습니다"
          }`}
        </p>

        <ol className="mt-4 flex flex-col gap-2 overflow-hidden">
          {summary.rows.map((row, index) => {
            const isMe = row.nickname === myNickname;
            const role = ROLE_INFO[row.role];
            return (
              <li
                key={row.nickname}
                style={{
                  animationDelay: `${FIRST_ROW_DELAY_MS + index * ROW_INTERVAL_MS}ms`,
                }}
                className={`motion-safe:animate-row-fly-in grid grid-cols-[2.5rem_minmax(0,1fr)_4rem_4.5rem_4.5rem] items-center gap-2 rounded-md border-4 px-3 py-2 text-lg sm:grid-cols-[3rem_minmax(0,1fr)_5rem_6rem_6rem] ${
                  isMe
                    ? "border-amber-500 bg-amber-50"
                    : "border-stone-900 bg-white"
                }`}
              >
                <span className="font-bold">{index + 1}.</span>
                <span className="truncate">{row.nickname}</span>
                <span className={`text-center ${role.className}`}>
                  {role.name}
                </span>
                <span className="text-right tabular-nums">{row.gold}G</span>
                <span className="text-right tabular-nums">⭐ {row.coins}</span>
              </li>
            );
          })}
        </ol>

        <div
          className="mt-4 flex items-center justify-end gap-3 motion-safe:animate-fade-in"
          style={{ animationDelay: `${footerDelay}ms` }}
        >
          {remainingSeconds !== null && !summary.finalRound && (
            <span className="text-sm text-stone-600">
              {remainingSeconds}초 뒤 자동 진행
            </span>
          )}
          {/* 방장만 넘길 수 있다. 마지막 라운드의 "다음"은 모두에게 최종 순위를 공개한다. */}
          {isHost ? (
            <button
              type="button"
              disabled={!isConnected}
              onClick={onNext}
              className="min-h-11 rounded-md border-4 border-stone-900 bg-white px-5 text-xl font-bold transition enabled:hover:bg-amber-50 enabled:active:scale-95 disabled:opacity-50"
            >
              {summary.finalRound ? "다음" : "다음 라운드"}
            </button>
          ) : (
            summary.finalRound && (
              <span className="text-sm text-stone-600">
                방장이 다음을 누르면 순위가 공개돼요
              </span>
            )
          )}
        </div>
      </section>
    </div>
  );
}
