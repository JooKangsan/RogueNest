import type { ButtonHTMLAttributes } from "react";

type SquareButtonProps = ButtonHTMLAttributes<HTMLButtonElement>;

export default function SquareButton({
  type = "button",
  className = "",
  ...buttonProps
}: SquareButtonProps) {
  return (
    <button
      {...buttonProps}
      type={type}
      className={`min-h-11 rounded-2xl border-4 border-amber-900/70 bg-white px-3 font-bold text-amber-950 shadow-lg transition enabled:hover:bg-amber-100 enabled:active:scale-95 disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
    />
  );
}
