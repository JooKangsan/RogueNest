import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import FrameButton from "../../components/FrameButton";
import { useGame } from "../../context/GameContext";
import enterBackground from "../../assets/enter_page.webp";
import makeBackground from "../../assets/waiting_page.webp";
import logo from "../../assets/logo.webp";
import nicknameFrame from "../../assets/nickName_input.webp";

// 배경이 확대되는 시간. make 배경은 끝부분에 겹쳐서 페이지 전환 이음새를 감춘다.
const ZOOM_MS = 900;
const CROSSFADE_MS = 350;

export default function EnterPage() {
  const navigate = useNavigate();
  const { nickname, setNickname } = useGame();
  const [leaving, setLeaving] = useState(false);
  const leaveTimer = useRef<number | undefined>(undefined);

  useEffect(() => () => window.clearTimeout(leaveTimer.current), []);

  const goToMake = () => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      navigate("/make");
      return;
    }
    setLeaving(true);
    leaveTimer.current = window.setTimeout(() => navigate("/make"), ZOOM_MS);
  };

  return (
    <section className="relative left-1/2 flex min-h-screen w-screen -translate-x-1/2 items-center justify-center overflow-hidden px-4 py-12">
      <div
        aria-hidden
        className="absolute inset-0 bg-cover bg-center bg-no-repeat"
        style={{
          backgroundImage: `url("${enterBackground}")`,
          transform: leaving ? "scale(4)" : "scale(1)",
          transition: `transform ${ZOOM_MS}ms cubic-bezier(0.7, 0, 0.84, 0)`,
        }}
      />
      <div
        aria-hidden
        className="absolute inset-0 bg-cover bg-center bg-no-repeat"
        style={{
          backgroundImage: `url("${makeBackground}")`,
          opacity: leaving ? 1 : 0,
          transition: `opacity ${CROSSFADE_MS}ms ease-out ${ZOOM_MS - CROSSFADE_MS}ms`,
        }}
      />
      <div
        className="relative w-full max-w-md"
        style={{
          opacity: leaving ? 0 : 1,
          transform: leaving ? "scale(1.1)" : "scale(1)",
          transition: "opacity 250ms ease-in, transform 250ms ease-in",
          pointerEvents: leaving ? "none" : undefined,
        }}
      >
        <img src={logo} alt="Rogue Nest" className="mx-auto w-full" />
        <form
          className="mt-5 flex flex-col items-center gap-3"
          onSubmit={(event) => {
            event.preventDefault();
            if (!nickname.trim() || leaving) return;
            goToMake();
          }}
        >
          <div
            className="relative w-full bg-center bg-no-repeat"
            style={{
              height: "88px",
              backgroundImage: `url("${nicknameFrame}")`,
              backgroundSize: "100% auto",
            }}
          >
            <input
              id="nickname"
              name="nickname"
              type="text"
              value={nickname}
              maxLength={20}
              onChange={(event) => setNickname(event.target.value)}
              placeholder="닉네임을 입력해주세요"
              autoComplete="nickname"
              autoFocus
              className="absolute text-center text-lg font-semibold outline-none placeholder:text-amber-950/60"
              style={{
                left: "14%",
                top: 0,
                width: "76%",
                height: "82%",
                padding: "0 0.75rem",
                border: 0,
                outline: "none",
                boxShadow: "none",
                borderRadius: "0.5rem",
                backgroundColor: "transparent",
                color: "#3b2413",
                textAlign: "center",
                fontSize: "1.125rem",
                fontWeight: 600,
              }}
            />
          </div>
          <div style={{ width: "240px", marginTop: "-0.75rem" }}>
            <FrameButton type="submit" disabled={!nickname.trim()}>
              시작하기
            </FrameButton>
          </div>
        </form>
      </div>
    </section>
  );
}
