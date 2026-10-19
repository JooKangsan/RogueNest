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
        // 배경 없이 큰 숫자만 띄운다. 어떤 배경 위에서도 읽히도록 진한 테두리와 그림자를 준다.
        <div
          className={`text-5xl font-bold leading-none tabular-nums [-webkit-text-stroke:2px_#1c1917] [paint-order:stroke_fill] [text-shadow:0_3px_6px_rgba(0,0,0,0.7)] ${
            remainingSeconds <= 10 ? "text-red-500" : "text-amber-300"
          }`}
          aria-label={`남은 시간 ${remainingSeconds}초`}
        >
          {remainingSeconds}초
        </div>
      )}
    </div>
  );
}
