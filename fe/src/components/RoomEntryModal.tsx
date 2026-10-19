import { useEffect, useState } from "react";

export type RoomEntryMode = "create" | "join";

const copy = {
  create: {
    title: "방 만들기",
    roomNamePlaceholder: "예: 우리 모임",
    passwordAutoComplete: "new-password",
  },
  join: {
    title: "참여하기",
    roomNamePlaceholder: "방 이름",
    passwordAutoComplete: "current-password",
  },
} as const;

type RoomEntryModalProps = {
  mode: RoomEntryMode;
  initialRoomName?: string;
  busy: boolean;
  error: string;
  onSubmit: (roomName: string, password: string) => Promise<boolean>;
  onClose: () => void;
};

export default function RoomEntryModal({
  mode,
  initialRoomName = "",
  busy,
  error,
  onSubmit,
  onClose,
}: RoomEntryModalProps) {
  const [roomName, setRoomName] = useState(initialRoomName);
  const [password, setPassword] = useState("");
  const text = copy[mode];

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) onClose();
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [busy, onClose]);

  const close = () => {
    if (!busy) onClose();
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (await onSubmit(roomName, password)) onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) close();
      }}
    >
      <section
        aria-labelledby="entry-modal-title"
        aria-modal="true"
        className="w-full max-w-md rounded-xl border-2 border-amber-900/40 bg-amber-50 p-6 shadow-xl"
        role="dialog"
      >
        <h2
          className="w-full flex justify-center text-xl font-bold text-amber-950"
          id="entry-modal-title"
        >
          {text.title}
        </h2>

        <form
          className="mt-6 flex flex-col gap-2"
          onSubmit={(event) => void submit(event)}
        >
          <label
            className="text-sm font-semibold text-amber-950"
            htmlFor="room-name"
          >
            방 이름
          </label>
          <input
            className="h-11 rounded-md border border-amber-900/30 bg-white px-3 text-amber-950"
            id="room-name"
            value={roomName}
            maxLength={24}
            onChange={(event) => setRoomName(event.target.value)}
            placeholder={text.roomNamePlaceholder}
            pattern="[가-힣A-Za-z0-9 _\-]+"
            title="한글, 영문, 숫자, 공백, 하이픈, 밑줄만 사용할 수 있습니다."
            required
            autoFocus={!initialRoomName}
          />
          {mode === "create" && (
            <p className="text-xs text-amber-900/60">
              한글, 영문, 숫자, 공백, 하이픈, 밑줄 (최대 24자)
            </p>
          )}

          <label
            className="mt-3 text-sm font-semibold text-amber-950"
            htmlFor="room-password"
          >
            비밀번호{" "}
            <span className="font-normal text-amber-900/60">
              (없으면 비워두세요)
            </span>
          </label>
          <input
            className="h-11 rounded-md border border-amber-900/30 bg-white px-3 text-amber-950"
            id="room-password"
            type="password"
            value={password}
            maxLength={100}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="비밀번호"
            autoComplete={text.passwordAutoComplete}
            autoFocus={Boolean(initialRoomName)}
          />

          {error && (
            <p className="mt-2 text-sm text-red-700" role="alert">
              {error}
            </p>
          )}

          <div className="mt-5 flex justify-end gap-3 mx-auto">
            <button
              className="min-h-11 rounded-md bg-amber-900 px-4 text-sm font-semibold text-amber-50 disabled:opacity-50"
              type="submit"
              disabled={busy || !roomName.trim()}
            >
              {busy ? "처리 중..." : text.title}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
