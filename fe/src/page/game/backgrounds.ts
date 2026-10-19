import fallbackBackground from "../../assets/waiting_page.webp";

// assets/game/ 안의 이미지를 파일 이름 순서대로 번호를 매긴다.
// 서버가 보내는 backgroundIndex가 이 순서의 번호다. (서버 설정 game.room.background-count와 개수를 맞춘다)
const gameBackgrounds = Object.entries(
  import.meta.glob<string>("../../assets/game/*.webp", {
    eager: true,
    import: "default",
  }),
)
  .sort(([a], [b]) => a.localeCompare(b, undefined, { numeric: true }))
  .map(([, url]) => url);

export function getGameBackground(index: number | null) {
  if (index === null || gameBackgrounds.length === 0) return fallbackBackground;
  return gameBackgrounds[index % gameBackgrounds.length];
}
