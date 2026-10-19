import Avatar from "../../../components/Avatar";
import { avatarColor } from "../../../components/avatarColor";
import type { Player } from "../../../context/gameTypes";
import { darkPanel } from "../styles";

// TODO: 투표/기권 전송과 투표 현황은 투표 단계 구현 때 연결한다.
export default function VotePanel({
  players,
  myNickname,
}: {
  players: Player[];
  myNickname: string | undefined;
}) {
  return (
    <section aria-labelledby="vote-title" className={`${darkPanel} p-3`}>
      <h2 id="vote-title" className="text-xl font-bold text-amber-400">
        투표하기
      </h2>
      <p className="mb-2 text-sm text-amber-50/70">플레이어를 선택하세요</p>
      <div className="flex flex-wrap items-center gap-1.5">
        {players.map((player, index) => (
          <button
            key={player.nickname}
            type="button"
            disabled
            title={player.nickname}
            aria-label={`${player.nickname}에게 투표`}
            className={`flex size-11 items-center justify-center rounded-lg border-2 bg-stone-800/80 disabled:cursor-not-allowed ${
              player.nickname === myNickname
                ? "border-amber-400"
                : "border-amber-50/15"
            }`}
          >
            <Avatar color={avatarColor(index)} className="size-8" />
          </button>
        ))}
        <button
          type="button"
          disabled
          className="min-h-11 rounded-lg border-2 border-amber-50/15 bg-stone-800/80 px-3 text-amber-50/80 disabled:cursor-not-allowed"
        >
          기권
        </button>
      </div>
    </section>
  );
}
