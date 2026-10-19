import { darkPanel } from "../styles";

const STEPS = [
  { phase: "DISCUSSION", label: "투표중" },
  { phase: "VOTE", label: "투표" },
  { phase: "SETTLE", label: "턴정산" },
  { phase: "NIGHT", label: "밤" },
];

export default function ProgressPanel({ phase }: { phase: string }) {
  return (
    <section aria-labelledby="progress-title" className={`${darkPanel} p-4`}>
      <h2 id="progress-title" className="mb-2 text-lg font-bold">
        진행상황
      </h2>
      <ol className="flex flex-col gap-1.5">
        {STEPS.map((step, index) => {
          const current = step.phase === phase;
          return (
            <li
              key={step.phase}
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
