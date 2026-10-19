import { useEffect, useRef, useState, type FormEvent } from "react";
import type { ChatMessage } from "../context/gameTypes";

type ChatPanelProps = {
  chat: ChatMessage[];
  myNickname: string | undefined;
  isConnected: boolean;
  onSend: (message: string) => void;
  tone?: "light" | "dark";
  title?: string;
  emptyText?: string;
  className?: string;
};

const tones = {
  light: {
    panel:
      "rounded-2xl border-4 border-amber-900/70 bg-amber-50/90 text-amber-950 shadow-xl",
    log: "border-2 border-amber-900/30 bg-white/70",
    muted: "text-amber-900/50",
    me: "text-amber-700",
    input:
      "border-2 border-amber-900/30 bg-white text-amber-950 placeholder:text-amber-900/40",
    send: "bg-amber-900 text-amber-50",
  },
  dark: {
    panel:
      "rounded-xl border-2 border-amber-900/60 bg-stone-900/85 text-amber-50 shadow-xl backdrop-blur-sm",
    log: "",
    muted: "text-amber-50/40",
    me: "text-amber-300",
    input:
      "border border-amber-50/20 bg-stone-950/60 text-amber-50 placeholder:text-amber-50/40",
    send: "bg-amber-500 text-stone-950",
  },
} as const;

export default function ChatPanel({
  chat,
  myNickname,
  isConnected,
  onSend,
  tone = "light",
  title = "채팅",
  emptyText = "첫 인사를 건네보세요.",
  className = "",
}: ChatPanelProps) {
  const [input, setInput] = useState("");
  const endRef = useRef<HTMLDivElement>(null);
  const style = tones[tone];

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [chat]);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!input.trim()) return;
    onSend(input.trim());
    setInput("");
  };

  return (
    <section
      aria-label={title}
      className={`flex flex-col p-4 ${style.panel} ${className}`}
    >
      <h2
        className={`mb-2 text-xl font-bold ${tone === "dark" ? "text-amber-400" : ""}`}
      >
        {title}
      </h2>
      <div
        className={`min-h-0 flex-1 overflow-y-auto rounded-xl p-2 ${style.log}`}
        aria-live="polite"
      >
        {chat.length === 0 ? (
          <p
            className={`flex h-full items-center justify-center text-sm ${style.muted}`}
          >
            {emptyText}
          </p>
        ) : (
          <ul className="flex flex-col gap-1.5">
            {chat.map((message, index) => (
              <li
                key={`${message.sentAt}-${index}`}
                className="wrap-break-word"
              >
                <strong
                  className={message.nickname === myNickname ? style.me : ""}
                >
                  {message.nickname}
                </strong>
                <span className={`mx-1 ${style.muted}`}>:</span>
                {message.message}
                <time className={`ml-2 text-xs ${style.muted}`}>
                  {new Date(message.sentAt).toLocaleTimeString("ko-KR", {
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </time>
              </li>
            ))}
          </ul>
        )}
        <div ref={endRef} />
      </div>
      <form className="mt-2 flex gap-2" onSubmit={submit}>
        <input
          aria-label="채팅 메시지"
          className={`h-11 min-w-0 flex-1 rounded-md px-3 ${style.input}`}
          value={input}
          maxLength={500}
          disabled={!isConnected}
          onChange={(event) => setInput(event.target.value)}
          placeholder={isConnected ? "메시지를 입력하세요" : "연결 중..."}
        />
        <button
          type="submit"
          className={`min-h-11 rounded-md px-4 font-semibold disabled:opacity-50 ${style.send}`}
          disabled={!isConnected || !input.trim()}
        >
          보내기
        </button>
      </form>
    </section>
  );
}
