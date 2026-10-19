// cards.json 의 "image" 파일명으로 assets/cards/ 안의 일러스트를 찾는다.
const cardImages = Object.fromEntries(
  Object.entries(
    import.meta.glob<string>("../../assets/cards/*.{webp,png,jpg,jpeg}", {
      eager: true,
      import: "default",
    }),
  ).map(([path, url]) => [path.split("/").pop()!, url]),
);

export function getCardImage(fileName: string | null | undefined) {
  if (!fileName) return undefined;
  const url = cardImages[fileName];
  if (!url && import.meta.env.DEV) {
    console.warn(`카드 이미지 파일이 없습니다: assets/cards/${fileName}`);
  }
  return url;
}
