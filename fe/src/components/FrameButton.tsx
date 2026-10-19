import type { ButtonHTMLAttributes } from "react";
import buttonFrame from "../assets/start_button.webp";
import buttonDisabledFrame from "../assets/start_button_disabled.webp";

type FrameButtonProps = ButtonHTMLAttributes<HTMLButtonElement>;

export default function FrameButton({
  children,
  type = "button",
  disabled,
  className = "",
  style,
  ...buttonProps
}: FrameButtonProps) {
  return (
    <button
      {...buttonProps}
      type={type}
      disabled={disabled}
      className={`flex w-full items-center justify-center bg-center bg-no-repeat text-lg font-bold transition enabled:hover:scale-105 enabled:active:scale-95 disabled:cursor-not-allowed ${className}`}
      style={{
        aspectRatio: "24 / 7",
        minHeight: "44px",
        border: 0,
        backgroundColor: "transparent",
        backgroundImage: `url("${disabled ? buttonDisabledFrame : buttonFrame}")`,
        backgroundSize: "100% auto",
        color: "#fff8dc",
        textShadow: "0 1px 3px rgba(0, 0, 0, 0.8)",
        ...style,
      }}
    >
      {children}
    </button>
  );
}
