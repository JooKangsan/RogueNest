import type { CardCatalog, JoinResponse, RoomSummary } from "./gameTypes";

async function request<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method: body === undefined ? "GET" : "POST",
    headers:
      body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const responseBody = await response.text();
  let data: unknown = null;
  if (responseBody) {
    try {
      data = JSON.parse(responseBody);
    } catch {
      throw new Error(
        response.ok
          ? "서버 응답 형식이 올바르지 않습니다."
          : `요청 실패 (${response.status})`,
      );
    }
  }
  if (!response.ok) {
    const message =
      typeof data === "object" &&
      data !== null &&
      "message" in data &&
      typeof data.message === "string"
        ? data.message
        : `요청 실패 (${response.status})`;
    throw new Error(message);
  }
  if (data === null) throw new Error("서버 응답이 비어 있습니다.");
  return data as T;
}

export function getCardCatalog() {
  return request<CardCatalog>("/api/meta/cards");
}

export function getRooms() {
  return request<RoomSummary[]>("/api/rooms");
}

export function createRoomRequest(roomName: string, password: string) {
  return request<{ roomName: string }>("/api/rooms", {
    roomName: roomName.trim(),
    password,
  });
}

export function joinRoomRequest(
  roomName: string,
  nickname: string,
  password: string,
) {
  return request<JoinResponse>(
    `/api/rooms/${encodeURIComponent(roomName.trim())}/join`,
    { nickname: nickname.trim(), password },
  );
}
