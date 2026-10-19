import HoodCharacter, {
  type HoodPose,
} from "../../../components/HoodCharacter";
import { avatarColor } from "../../../components/avatarColor";
import type { Player } from "../../../context/gameTypes";

// 서 있는 각도(광장 중심 기준, 0°=오른쪽, 90°=아래)에 따라 중앙을 바라보는 그림을 고른다.
// 45° 간격 8방향. 원본 그림 이름의 "우측"은 캐릭터의 오른쪽 면이라 화면에서는 왼쪽을 바라본다.
// 그래서 화면 오른쪽을 바라봐야 하는 자리는 그림을 좌우 반전한다.
const POSE_BY_SECTOR: { pose: HoodPose; mirrored: boolean }[] = [
  { pose: "right", mirrored: false }, // 오른쪽(동)에 서서 왼쪽을 봄
  { pose: "right-diagonal-back", mirrored: false }, // 남동 → 왼쪽 위를 봄
  { pose: "back", mirrored: false }, // 아래(남) → 위를 봄
  { pose: "right-diagonal-back", mirrored: true }, // 남서 → 오른쪽 위를 봄
  { pose: "right", mirrored: true }, // 왼쪽(서) → 오른쪽을 봄
  { pose: "left-diagonal", mirrored: false }, // 북서 → 오른쪽 아래를 봄
  { pose: "front", mirrored: false }, // 위(북) → 아래를 봄
  { pose: "right-diagonal", mirrored: false }, // 북동 → 왼쪽 아래를 봄
];

// 시계 자리: 0=12시, 1=1시반, 2=3시, 3=4시반, 4=6시, 5=7시반, 6=9시, 7=10시반
const CLOCK_SEATS: Record<number, number[]> = {
  4: [0, 2, 4, 6],
  5: [0, 1, 3, 5, 7],
  6: [0, 1, 3, 4, 5, 7],
  7: [0, 1, 2, 3, 4, 5, 7],
  8: [0, 1, 2, 3, 4, 5, 6, 7],
};

// 화면 각도(0°=오른쪽, 90°=아래). 12시(270°)부터 시계 방향으로 자리를 채운다.
// 자리 표가 없는 인원(1~3명, 9~12명)은 12시부터 같은 간격으로 둔다.
function seatAngles(count: number) {
  const clockSeats = CLOCK_SEATS[count];
  if (clockSeats) return clockSeats.map((seat) => 270 + seat * 45);
  return Array.from(
    { length: count },
    (_, index) => 270 + (index * 360) / count,
  );
}

export type PlazaLayout = {
  // 타원 중심과 반지름 (컨테이너 대비 %). 캐릭터의 발끝이 이 타원 위에 선다.
  centerX: number;
  centerY: number;
  radiusX: number;
  radiusY: number;
  // 캐릭터 폭 (CSS 값)
  characterWidth: string;
};

export default function PlazaRing({
  players,
  myNickname,
  layout,
  voteTarget,
  onVote,
  canTarget = (player) => player.alive,
  actionLabel = "에게 투표",
  cardLabel = () => undefined,
}: {
  players: Player[];
  myNickname: string | undefined;
  layout: PlazaLayout;
  // 내가 투표한 대상 닉네임
  voteTarget: string | null;
  // 투표할 수 있을 때만 넘긴다. 캐릭터를 누르면 그 사람에게 투표한다.
  onVote?: (nickname: string) => void;
  // 이 플레이어에게 투표할 수 있는지 (사망자, 동표 시 중앙 밖 플레이어는 불가)
  canTarget?: (player: Player) => boolean;
  // 캐릭터를 눌렀을 때 하는 행동 (스크린리더용). 밤에는 살인마의 살해 대상 지정
  actionLabel?: string;
  // 공개된 인물 카드 id → 화면에 보일 이름
  cardLabel?: (cardId: string) => string | undefined;
}) {
  // 색과 자리 모두 입장 순서로 정한다. 첫 번째 플레이어가 12시, 이후 시계 방향.
  const angles = seatAngles(players.length);
  const seated = players.map((player, index) => ({
    player,
    color: avatarColor(index),
    angle: angles[index],
  }));
  // 최종 투표에서 중앙에 선 플레이어들. 우물 앞에 나란히 선다.
  const trial = players.filter((player) => player.onTrial);

  return (
    <div className="pointer-events-none absolute inset-0">
      {seated.map(({ player, color, angle }) => {
        const radians = (angle * Math.PI) / 180;
        const trialIndex = trial.indexOf(player);
        const onTrial = trialIndex >= 0;
        // 중앙에 서면 광장 가운데 제단 위에 정면을 보고 나란히 선다.
        const trialSpacing = layout.radiusX * 0.3;
        const x = onTrial
          ? layout.centerX +
            layout.radiusX * 0.03 +
            (trialIndex - (trial.length - 1) / 2) * trialSpacing
          : layout.centerX + layout.radiusX * Math.cos(radians);
        const y = onTrial
          ? layout.centerY - layout.radiusY * 0.4
          : layout.centerY + layout.radiusY * Math.sin(radians);
        const sector = Math.round(((angle % 360) + 360) / 45) % 8;
        const { pose, mirrored } = onTrial
          ? { pose: "front" as HoodPose, mirrored: false }
          : POSE_BY_SECTOR[sector];
        // 아래쪽(가까운 쪽)일수록 조금 크게 그려 원근감을 준다. 중앙에 서도 크기는 원래 자리 그대로.
        const depth = (Math.sin(radians) + 1) / 2;
        const isMe = player.nickname === myNickname;
        const isTarget = player.nickname === voteTarget;
        const targetable = Boolean(onVote) && canTarget(player);

        return (
          // 기준점이 발끝이 되도록 캐릭터 아래 가운데를 (x, y)에 맞추고, 닉네임은 발 아래에 띄운다.
          <button
            key={player.nickname}
            type="button"
            disabled={!targetable}
            aria-pressed={isTarget}
            aria-label={`${player.nickname}${actionLabel}`}
            onClick={() => onVote?.(player.nickname)}
            className={`group pointer-events-auto absolute -translate-x-1/2 -translate-y-full transition-[left,top,scale] duration-700 ease-out disabled:pointer-events-none ${
              targetable ? "cursor-pointer" : ""
            } ${player.alive ? "" : "opacity-45 grayscale"}`}
            style={{
              left: `${x}%`,
              top: `${y}%`,
              width: layout.characterWidth,
              minWidth: "44px",
              zIndex: onTrial ? 200 : Math.round(depth * 100),
              scale: `${0.85 + depth * 0.25}`,
              transformOrigin: "bottom center",
            }}
          >
            {/* 받은 표 수. 배경 없이 숫자만 띄우고, 내가 찍은 사람은 빨간 숫자로 강조한다. */}
            {player.alive && player.votes > 0 && (
              <span
                key={player.votes}
                aria-label={`${player.votes}표${isTarget ? ", 내가 투표함" : ""}`}
                className={`absolute bottom-full left-1/2 -translate-x-1/2 text-3xl font-bold leading-none [-webkit-text-stroke:1.5px_#1c1917] [paint-order:stroke_fill] [text-shadow:0_2px_4px_rgba(0,0,0,0.6)] motion-safe:animate-page-in ${
                  isTarget ? "text-red-500" : "text-amber-300"
                }`}
              >
                {player.votes}
              </span>
            )}
            {/* 투표를 마친 사람 표시 (대상은 비공개) */}
            {player.alive && player.voted && (
              <span
                aria-label="투표 완료"
                className="absolute -right-1 top-0 z-10 flex size-5 items-center justify-center rounded-full bg-green-600 text-[10px] text-white shadow"
              >
                ✓
              </span>
            )}
            <HoodCharacter
              pose={pose}
              mirrored={mirrored}
              color={color}
              className={`w-full transition ${
                isTarget
                  ? "drop-shadow-[0_0_6px_rgba(239,68,68,0.95)]"
                  : "drop-shadow-[0_4px_3px_rgba(0,0,0,0.35)]"
              } ${targetable ? "group-hover:-translate-y-1 group-hover:brightness-110" : ""}`}
            />
            <span
              className={`absolute left-1/2 top-full mt-0.5 max-w-[180%] -translate-x-1/2 truncate whitespace-nowrap rounded-full px-2 text-xs ${
                isMe
                  ? "bg-amber-400 font-bold text-stone-900"
                  : "bg-stone-900/75 text-amber-50"
              }`}
            >
              {player.nickname}
            </span>
            {/* 공개한 인물 카드 */}
            {player.revealedCardId && (
              <span
                title="공개된 인물 카드"
                className="absolute left-1/2 top-full mt-5 max-w-[200%] -translate-x-1/2 truncate whitespace-nowrap rounded-md border border-[#5a3a22] bg-[#f6ecda] px-1.5 text-[11px] font-bold text-[#3b2413] shadow motion-safe:animate-page-in"
              >
                🂠 {cardLabel(player.revealedCardId) ?? "인물 카드"}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}
