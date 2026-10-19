type NumberSettingProps = {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  suffix: string;
  editable: boolean;
  disabled: boolean;
  onCommit: (value: number) => void;
};

export function NumberSetting({
  label,
  value,
  min,
  max,
  step,
  suffix,
  editable,
  disabled,
  onCommit,
}: NumberSettingProps) {
  const adjust = (amount: number) => {
    const nextValue = Math.min(max, Math.max(min, value + amount));
    if (nextValue !== value) onCommit(nextValue);
  };

  const stepButtonClass =
    "flex size-11 items-center justify-center rounded-md text-2xl font-bold text-amber-950 transition enabled:hover:bg-amber-200/70 disabled:opacity-30";

  return (
    <div className="flex flex-col items-center gap-1">
      <span className="text-lg text-amber-950">{label}</span>
      <div className="flex items-center gap-1">
        {editable && (
          <button
            aria-label={`${label} 줄이기`}
            className={stepButtonClass}
            disabled={disabled || value <= min}
            onClick={() => adjust(-step)}
            type="button"
          >
            −
          </button>
        )}
        <output
          aria-label={label}
          aria-live="polite"
          className="min-w-16 text-center text-3xl font-bold tabular-nums text-amber-950"
        >
          {value}
          <span className="ml-0.5 text-base font-normal text-amber-900/70">
            {suffix}
          </span>
        </output>
        {editable && (
          <button
            aria-label={`${label} 늘리기`}
            className={stepButtonClass}
            disabled={disabled || value >= max}
            onClick={() => adjust(step)}
            type="button"
          >
            +
          </button>
        )}
      </div>
    </div>
  );
}
