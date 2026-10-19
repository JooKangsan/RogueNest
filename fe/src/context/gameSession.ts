import type { JoinResponse } from "./gameTypes";

const sessionStorageKey = "rogue-nest-session";

export function readStoredJoin(): JoinResponse | null {
  try {
    const value: unknown = JSON.parse(
      sessionStorage.getItem(sessionStorageKey) ?? "null",
    );
    if (typeof value !== "object" || value === null) return null;
    const roomName =
      "roomName" in value && typeof value.roomName === "string"
        ? value.roomName
        : "roomCode" in value && typeof value.roomCode === "string"
          ? value.roomCode
          : null;
    if (
      roomName === null ||
      !("nickname" in value) ||
      typeof value.nickname !== "string" ||
      !("sessionToken" in value) ||
      typeof value.sessionToken !== "string"
    )
      return null;
    return {
      roomName,
      nickname: value.nickname,
      sessionToken: value.sessionToken,
    };
  } catch {
    sessionStorage.removeItem(sessionStorageKey);
    return null;
  }
}

export function saveStoredJoin(join: JoinResponse) {
  sessionStorage.setItem(sessionStorageKey, JSON.stringify(join));
}

export function clearStoredJoin() {
  sessionStorage.removeItem(sessionStorageKey);
}
