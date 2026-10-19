import type { InputHTMLAttributes } from "react";

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label?: string;
  error?: string;
};

export default function Input({
  id,
  label,
  error,
  className = "",
  ...inputProps
}: InputProps) {
  const inputId = id ?? inputProps.name;

  return (
    <div className="relative flex flex-col gap-2">
      <label
        htmlFor={inputId}
        className="text-sm font-semibold leading-5 text-stone-700"
      >
        {label}
      </label>
      <input
        {...inputProps}
        id={inputId}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${inputId}-error` : undefined}
        className={`h-12 w-full rounded-md border bg-white px-3 text-base text-stone-900 outline-none focus-visible:ring-2 focus-visible:ring-stone-500 ${
          error ? "border-red-500" : "border-stone-300"
        } ${className}`}
      />
      {error && (
        <p
          id={`${inputId}-error`}
          className="text-sm font-semibold text-red-700"
          role="alert"
        >
          {error}
        </p>
      )}
    </div>
  );
}
