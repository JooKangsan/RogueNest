import { useState } from "react";

// 방 연결을 기다리는 화면. 연결이 안 될 때 갇히지 않도록 나가기 버튼과 오류를 함께 보여준다.
export default function ConnectingScreen({
  background,
  message,
  error,
  onLeave,
}: {
  background: string;
  message: string;
  error: string;
  // 나가기: 저장된 세션을 지우고 방 목록으로 돌아간다(연결 전이어도 동작).
  onLeave: () => Promise<void>;
}) {
  const [leaving, setLeaving] = useState(false);

  return (
    <section
      className="relative left-1/2 flex min-h-screen w-screen -translate-x-1/2 items-center justify-center bg-cover bg-center px-4 text-amber-950"
      style={{ backgroundImage: `url("${background}")` }}
    >
      <div
        aria-live="polite"
        className="flex w-full max-w-sm flex-col items-center gap-4 rounded-2xl border-4 border-amber-900/70 bg-amber-50/90 px-6 py-5 text-center shadow-xl"
      >
        <p className="text-lg">
          <span
            aria-hidden
            className="mr-2 inline-block motion-safe:animate-spin"
          >
            ↻
          </span>
          {message}
        </p>
        {error && (
          <p role="alert" className="text-sm text-red-700">
            {error}
          </p>
        )}
        <button
          type="button"
          disabled={leaving}
          className="min-h-11 w-full rounded-lg border-2 border-amber-900/60 bg-white/80 px-4 font-bold transition enabled:hover:bg-white disabled:opacity-50"
          onClick={() => {
            setLeaving(true);
            void onLeave();
          }}
        >
          {leaving ? "나가는 중..." : "나가기"}
        </button>
      </div>
    </section>
  );
}
