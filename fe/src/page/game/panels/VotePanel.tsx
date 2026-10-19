import HoodCharacter from "../../../components/HoodCharacter";
import { avatarColor } from "../../../components/avatarColor";
import type { Player } from "../../../context/gameTypes";
import { darkPanel } from "../styles";

export default function VotePanel({
  players,
  myNickname,
  voteTarget,
  abstained,
  onVote,
  onAbstain,
  canTarget = (player) => player.alive,
  finalVote = false,
}: {
  players: Player[];
  myNickname: string | undefined;
  voteTarget: string | null;
  abstained: boolean;
  // 투표할 수 있을 때만 넘긴다.
  onVote?: (nickname: string) => void;
  onAbstain?: () => void;
  canTarget?: (player: Player) => boolean;
  // 최종 투표(중앙에 선 뒤) 중인지
  finalVote?: boolean;
}) {
  const alive = players.filter((player) => player.alive);
  const votedCount = alive.filter((player) => player.voted).length;

  return (
    <section aria-labelledby="vote-title" className={`${darkPanel} p-3`}>
      <div className="flex items-baseline justify-between gap-2">
        <h2 id="vote-title" className="text-xl font-bold text-amber-400">
          투표하기
        </h2>
        <span className="text-sm text-amber-50/60">
          {votedCount}/{alive.length} 완료
        </span>
      </div>
      <p className="mb-2 text-sm text-amber-50/70">
        {!onVote
          ? "토론 시간에 투표할 수 있어요"
          : finalVote
            ? "최종 투표! 중앙에 선 사람을 검거할지 정하세요"
            : "플레이어를 선택하세요 (광장 캐릭터를 눌러도 돼요)"}
      </p>
      <div className="flex flex-wrap items-center gap-1.5">
        {players.map((player, index) => {
          const selected = player.nickname === voteTarget;
          const targetable = Boolean(onVote) && canTarget(player);
          return (
            <button
              key={player.nickname}
              type="button"
              disabled={!targetable}
              title={
                player.alive ? player.nickname : `${player.nickname} (사망)`
              }
              aria-pressed={selected}
              aria-label={`${player.nickname}에게 투표`}
              onClick={() => onVote?.(player.nickname)}
              className={`relative flex size-11 items-center justify-center rounded-lg border-2 bg-stone-800/80 transition enabled:hover:bg-stone-700 disabled:cursor-not-allowed ${
                player.alive ? "" : "opacity-40 grayscale"
              } ${player.onTrial ? "ring-2 ring-amber-400" : ""} ${
                selected
                  ? "border-red-500 ring-2 ring-red-500/60"
                  : player.nickname === myNickname
                    ? "border-amber-400"
                    : "border-amber-50/15"
              }`}
            >
              <HoodCharacter
                pose="head"
                color={avatarColor(index)}
                className="w-9"
              />
              {player.alive && player.voted && (
                <span className="absolute -right-1 -top-1 flex size-4 items-center justify-center rounded-full bg-green-600 text-[9px] text-white">
                  ✓
                </span>
              )}
            </button>
          );
        })}
        <button
          type="button"
          disabled={!onAbstain}
          aria-pressed={abstained}
          onClick={onAbstain}
          className={`min-h-11 rounded-lg border-2 px-3 transition enabled:hover:bg-stone-700 disabled:cursor-not-allowed ${
            abstained
              ? "border-sky-400 bg-sky-900/60 text-sky-200"
              : "border-amber-50/15 bg-stone-800/80 text-amber-50/80"
          }`}
        >
          기권
        </button>
      </div>
    </section>
  );
}
