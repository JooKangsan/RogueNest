import type { Room } from "../../../context/gameTypes";
import { darkPanel } from "../styles";

export default function TurnHeader({
  room,
  remainingSeconds,
}: {
  room: Room;
  remainingSeconds: number | null;
}) {
  return (
    <div className="flex flex-col items-center gap-1">
      <div
        className={`${darkPanel} flex items-center gap-3 px-5 py-2 text-lg text-amber-400`}
      >
        <span>라운드 {room.round}</span>
        <span>
          턴 {room.turn} / {room.maxTurns}
        </span>
        <span aria-hidden>⌛</span>
      </div>
      {remainingSeconds !== null && (
        <div
          className={`rounded-md border-2 border-stone-900 bg-stone-100 px-6 py-1 text-xl font-bold tabular-nums shadow-lg ${
            remainingSeconds <= 10 ? "text-red-700" : "text-stone-900"
          }`}
          aria-label={`남은 시간 ${remainingSeconds}초`}
        >
          {remainingSeconds}초
        </div>
      )}
    </div>
  );
}
