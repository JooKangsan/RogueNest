import { darkPanel } from "../styles";

// 진행 단계와 그 단계로 표시할 서버 phase 목록
const STEPS: { label: string; phases: string[] }[] = [
  { label: "투표중", phases: ["DISCUSSION"] },
  { label: "투표", phases: ["VOTE", "RESULT"] },
  { label: "턴정산", phases: ["SETTLE"] },
  { label: "밤", phases: ["NIGHT"] },
];

export default function ProgressPanel({ phase }: { phase: string }) {
  return (
    <section aria-labelledby="progress-title" className={`${darkPanel} p-4`}>
      <h2 id="progress-title" className="mb-2 text-lg font-bold">
        진행상황
      </h2>
      <ol className="flex flex-col gap-1.5">
        {STEPS.map((step, index) => {
          const current = step.phases.includes(phase);
          return (
            <li
              key={step.label}
              aria-current={current ? "step" : undefined}
              className={
                current ? "font-bold text-sky-400" : "text-amber-50/70"
              }
            >
              {index + 1}. {step.label}
            </li>
          );
        })}
      </ol>
    </section>
  );
}
