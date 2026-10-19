import { useEffect, useState, type FormEvent } from "react";

type RoomSettingsModalProps = {
  roomName: string;
  nickname: string;
  isHost: boolean;
  isConnected: boolean;
  error: string;
  onRename: (roomName: string) => void;
  onChangeNickname: (nickname: string) => void;
  onClose: () => void;
};

const inputClass =
  "h-11 min-w-0 flex-1 rounded-md border border-amber-900/30 bg-white px-3 text-amber-950 disabled:bg-amber-100/60";
const actionClass =
  "min-h-11 shrink-0 rounded-md bg-amber-900 px-4 text-sm font-semibold text-amber-50 disabled:opacity-40";

export default function RoomSettingsModal({
  roomName,
  nickname,
  isHost,
  isConnected,
  error,
  onRename,
  onChangeNickname,
  onClose,
}: RoomSettingsModalProps) {
  const [roomNameDraft, setRoomNameDraft] = useState(roomName);
  const [nicknameDraft, setNicknameDraft] = useState(nickname);
  const [copied, setCopied] = useState(false);
  // 변경을 요청한 항목과 요청 당시 값. 서버가 반영해 값이 바뀌면 모달을 닫는다.
  const [pending, setPending] = useState<{
    field: "roomName" | "nickname";
    from: string;
  } | null>(null);

  const [syncedRoomName, setSyncedRoomName] = useState(roomName);
  const [syncedNickname, setSyncedNickname] = useState(nickname);

  // 서버에서 변경이 반영되면 입력값도 최신 값으로 맞춘다.
  if (roomName !== syncedRoomName) {
    setSyncedRoomName(roomName);
    setRoomNameDraft(roomName);
  }
  if (nickname !== syncedNickname) {
    setSyncedNickname(nickname);
    setNicknameDraft(nickname);
  }

  useEffect(() => {
    if (!pending) return;
    const current = pending.field === "roomName" ? roomName : nickname;
    if (current !== pending.from) onClose();
  }, [nickname, onClose, pending, roomName]);

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [onClose]);

  const copyRoomName = async () => {
    try {
      await navigator.clipboard.writeText(roomName);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
    }
  };

  const submitRoomName = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setPending({ field: "roomName", from: roomName });
    onRename(roomNameDraft.trim());
  };

  const submitNickname = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setPending({ field: "nickname", from: nickname });
    onChangeNickname(nicknameDraft.trim());
  };

  const roomNameChanged =
    roomNameDraft.trim() !== "" && roomNameDraft.trim() !== roomName;
  const nicknameChanged =
    nicknameDraft.trim() !== "" && nicknameDraft.trim() !== nickname;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="room-settings-title"
        className="w-full max-w-sm rounded-xl border-2 border-amber-900/40 bg-amber-50 p-6 shadow-xl"
      >
        <div className="flex items-start justify-between gap-4">
          <h2
            id="room-settings-title"
            className="text-xl font-bold text-amber-950"
          >
            설정
          </h2>
          <button
            className="flex size-11 items-center justify-center rounded text-2xl text-amber-900/60 hover:bg-amber-100"
            onClick={onClose}
            aria-label="닫기"
          >
            ×
          </button>
        </div>

        <form className="mt-4" onSubmit={submitRoomName}>
          <label
            className="text-sm text-amber-900/70"
            htmlFor="settings-room-name"
          >
            방 이름 {!isHost && "(방장만 변경 가능)"}
          </label>
          <div className="mt-1 flex items-center gap-2">
            <input
              id="settings-room-name"
              className={inputClass}
              value={roomNameDraft}
              maxLength={24}
              pattern="[가-힣A-Za-z0-9 _\-]+"
              title="한글, 영문, 숫자, 공백, 하이픈, 밑줄만 사용할 수 있습니다."
              readOnly={!isHost}
              disabled={!isConnected}
              onChange={(event) => setRoomNameDraft(event.target.value)}
            />
            {isHost && roomNameChanged ? (
              <button type="submit" className={actionClass}>
                변경
              </button>
            ) : (
              <button
                type="button"
                className={actionClass}
                onClick={() => void copyRoomName()}
              >
                {copied ? "복사됨" : "복사"}
              </button>
            )}
          </div>
        </form>

        <form className="mt-4" onSubmit={submitNickname}>
          <label
            className="text-sm text-amber-900/70"
            htmlFor="settings-nickname"
          >
            내 닉네임
          </label>
          <div className="mt-1 flex items-center gap-2">
            <input
              id="settings-nickname"
              className={inputClass}
              value={nicknameDraft}
              maxLength={20}
              disabled={!isConnected}
              onChange={(event) => setNicknameDraft(event.target.value)}
            />
            <button
              type="submit"
              className={actionClass}
              disabled={!isConnected || !nicknameChanged}
            >
              변경
            </button>
          </div>
        </form>

        {error && (
          <p className="mt-3 text-sm text-red-700" role="alert">
            {error}
          </p>
        )}
        <p className="mt-4 text-sm text-amber-900/70">
          토론 시간은 180초로 고정이에요.
        </p>
      </section>
    </div>
  );
}
