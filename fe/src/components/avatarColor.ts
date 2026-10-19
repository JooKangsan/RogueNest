// 플레이어 색. 입장 순서대로 배정하고 대기실 아바타와 광장 캐릭터 후드 색에 같이 쓴다.
// 후드는 원본 명암을 유지한 채 색조만 바꾸므로 흰색/검정처럼 채도가 없는 색은 쓰지 않는다.
// 같은 파스텔 톤(채도와 밝기를 비슷하게)으로 맞춘다. 실제 밝기는 HoodCharacter에서 한 번 더 올린다.
const PLAYER_COLORS = [
  "#e8a0a0", // 빨강
  "#9fb8e8", // 파랑
  "#a8d8a0", // 연두
  "#f2dc8a", // 노랑
  "#c4a8e0", // 보라
  "#f0b0c8", // 분홍
  "#94d4cc", // 청록
  "#f0bc94", // 주황
  "#a4d4f0", // 하늘
  "#b0d8b8", // 초록
  "#a8acd8", // 남색
  "#d4b49c", // 갈색
];

export function avatarColor(index: number) {
  return PLAYER_COLORS[index % PLAYER_COLORS.length];
}
