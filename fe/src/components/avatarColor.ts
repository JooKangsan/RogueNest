// 입장 순서대로 아바타 색을 배정한다.
const AVATAR_COLORS = [
  "#c0392b",
  "#7cb342",
  "#3f6fd8",
  "#f2c230",
  "#8a5cd1",
  "#e86a92",
  "#f1efe9",
  "#e8742a",
  "#38b6b0",
  "#8a8f98",
  "#6d4c41",
  "#2e7d32",
];

export function avatarColor(index: number) {
  return AVATAR_COLORS[index % AVATAR_COLORS.length];
}
