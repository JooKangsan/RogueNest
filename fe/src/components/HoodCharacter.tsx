import backShade from "../assets/character/back-shade.webp";
import backMask from "../assets/character/back-mask.webp";
import frontShade from "../assets/character/front-shade.webp";
import frontMask from "../assets/character/front-mask.webp";
import headShade from "../assets/character/head-shade.webp";
import headMask from "../assets/character/head-mask.webp";
import leftDiagonalShade from "../assets/character/left-diagonal-shade.webp";
import leftDiagonalMask from "../assets/character/left-diagonal-mask.webp";
import rightShade from "../assets/character/right-shade.webp";
import rightMask from "../assets/character/right-mask.webp";
import rightDiagonalShade from "../assets/character/right-diagonal-shade.webp";
import rightDiagonalMask from "../assets/character/right-diagonal-mask.webp";
import rightDiagonalBackShade from "../assets/character/right-diagonal-back-shade.webp";
import rightDiagonalBackMask from "../assets/character/right-diagonal-back-mask.webp";

export type HoodPose =
  | "front"
  | "left-diagonal"
  | "right-diagonal"
  | "right"
  | "right-diagonal-back"
  | "back"
  // 투표 패널 등에 쓰는 얼굴 아이콘 (좌측 대각 얼굴을 정사각형으로 자른 것)
  | "head";

// shade: 후드만 흑백(명암)으로 바꾼 그림, mask: 후드 영역, aspect: 그림 비율
const POSES: Record<
  HoodPose,
  { shade: string; mask: string; aspect?: string }
> = {
  front: { shade: frontShade, mask: frontMask },
  "left-diagonal": { shade: leftDiagonalShade, mask: leftDiagonalMask },
  "right-diagonal": { shade: rightDiagonalShade, mask: rightDiagonalMask },
  right: { shade: rightShade, mask: rightMask },
  "right-diagonal-back": {
    shade: rightDiagonalBackShade,
    mask: rightDiagonalBackMask,
  },
  back: { shade: backShade, mask: backMask },
  head: { shade: headShade, mask: headMask, aspect: "1 / 1" },
};

// 후드를 밝게 들어 올린 뒤 색을 입혀 모든 색이 같은 파스텔 톤이 되게 한다.
const PASTEL_LIFT = 0.45;

// 후드 영역에만 color를 'color' 블렌드로 입혀서 명암·외곽선·눈은 원본 그대로 둔다.
function maskStyle(mask: string) {
  return {
    maskImage: `url("${mask}")`,
    maskSize: "100% 100%",
    WebkitMaskImage: `url("${mask}")`,
    WebkitMaskSize: "100% 100%",
  };
}

export default function HoodCharacter({
  pose,
  color,
  mirrored = false,
  className = "",
}: {
  pose: HoodPose;
  color: string;
  mirrored?: boolean;
  className?: string;
}) {
  const { shade, mask, aspect = "36 / 46" } = POSES[pose];
  return (
    <div
      aria-hidden
      className={`relative isolate ${mirrored ? "-scale-x-100" : ""} ${className}`}
      style={{ aspectRatio: aspect }}
    >
      <img
        src={shade}
        alt=""
        className="absolute inset-0 size-full"
        draggable={false}
      />
      {/* 후드 명암을 흰색으로 살짝 덮어 밝힌다. 색 블렌드는 이 밝기를 그대로 쓴다. */}
      <div
        className="absolute inset-0"
        style={{
          ...maskStyle(mask),
          backgroundColor: "#fff",
          opacity: PASTEL_LIFT,
        }}
      />
      <div
        className="absolute inset-0 mix-blend-color"
        style={{ ...maskStyle(mask), backgroundColor: color }}
      />
    </div>
  );
}
