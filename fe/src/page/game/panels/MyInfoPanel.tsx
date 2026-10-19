import type { Wallet } from "../../../context/gameTypes";
import { lightPanel } from "../styles";

const CARD_GRADES = [
  { name: "일반", price: 1, className: "bg-green-700 border-green-900" },
  { name: "희귀", price: 3, className: "bg-blue-700 border-blue-900" },
  { name: "전설", price: 5, className: "bg-purple-700 border-purple-900" },
];

export default function MyInfoPanel({
  wallet,
  coinPrice,
  isConnected,
  onExchangeCoin,
}: {
  wallet: Wallet | null;
  coinPrice: number;
  isConnected: boolean;
  onExchangeCoin: () => void;
}) {
  const canExchange =
    isConnected && wallet !== null && wallet.gold >= coinPrice;

  return (
    <section aria-labelledby="my-info-title" className={`${lightPanel} p-4`}>
      <h2 id="my-info-title" className="mb-2 text-xl font-bold">
        내정보
      </h2>
      <dl className="divide-y divide-amber-900/15 rounded-lg bg-white/70 px-3">
        <div className="flex items-center justify-between py-2">
          <dt>골드</dt>
          <dd className="font-bold tabular-nums">🪙 {wallet?.gold ?? "-"} G</dd>
        </div>
        <div className="flex items-center justify-between py-2">
          <dt>코인</dt>
          <dd className="font-bold tabular-nums">
            ⭐ {wallet?.coins ?? "-"} 개
          </dd>
        </div>
      </dl>

      <div className="mt-3 rounded-lg border-2 border-amber-600 bg-amber-300/80 p-3">
        <p className="font-bold">코인 교환 ({coinPrice}G)</p>
        <p className="text-sm text-amber-950/80">
          {coinPrice}G로 코인 1개를 구매합니다. 환불할 수 없어요.
        </p>
        <button
          type="button"
          className="mt-2 min-h-11 w-full rounded-md border-2 border-amber-700 bg-amber-100 font-semibold transition enabled:hover:bg-white disabled:opacity-50"
          disabled={!canExchange}
          onClick={onExchangeCoin}
        >
          교환하기
        </button>
      </div>

      <h3 className="mt-4 font-bold">인물 카드 뽑기</h3>
      <p className="mb-2 text-sm text-amber-950/70">
        원하는 등급의 인물 카드를 골드로 뽑을 수 있습니다.
      </p>
      {/* TODO: 카드 구매는 인물 카드 구현 때 연결한다. */}
      <div className="flex flex-col gap-2">
        {CARD_GRADES.map((grade) => (
          <button
            key={grade.name}
            type="button"
            disabled
            className={`flex min-h-11 items-center justify-between rounded-lg border-2 px-3 py-2 text-left text-white disabled:cursor-not-allowed disabled:opacity-60 ${grade.className}`}
          >
            <span>
              <span className="block font-bold">
                {grade.name} 뽑기 ({grade.price}G)
              </span>
              <span className="text-sm text-white/80">
                {grade.name} 등급 카드 획득
              </span>
            </span>
            <span className="text-xl font-bold">{grade.price}</span>
          </button>
        ))}
      </div>
    </section>
  );
}
