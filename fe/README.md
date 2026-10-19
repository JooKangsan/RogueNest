# RogueNest frontend

React + TypeScript lobby client for testing the backend REST and STOMP APIs.
The entry flow has a nickname screen, a room menu with create/join dialogs,
and the waiting-room screen. Players enter a unique room name when creating a
room, then share that name for others to join. Tailwind CSS is integrated
through the Vite plugin for the entry screens; the current waiting-room styles
remain in `src/App.css`.

The screens are split into route pages under `src/page`: `/enter` collects the
nickname, `/make` creates or joins rooms and lists public rooms, and `/waiting`
shows the waiting room. The shared room and connection state lives in
`src/context/GameContext.tsx`. `/waiting` redirects to `/waiting` for convenience.

## Run locally

Start the backend in one terminal:

```bash
cd ../be/demo
./gradlew bootRun
```

Start Vite in a second terminal:

```bash
npm install
npm run dev
```

Open the Vite URL (normally `http://localhost:5173`). To test from a phone on
the same Wi-Fi, find the development computer's local IP (for example,
`ipconfig getifaddr en0` on macOS) and open `http://<local-ip>:5173` on the
phone. Vite listens on the network and proxies `/api` to the backend and
upgrades `/ws` to WebSocket. Keep both servers running and allow incoming
connections in the computer's firewall if prompted. Create a room in one
browser tab; join its room name from another tab with a different nickname to
test roster updates, ready status, host controls, settings, and chat.
Choose whether each newly created room is public or private. Public waiting
rooms appear in the lobby list, which refreshes every 10 seconds; private rooms
remain joinable by room name. Public rooms can also require a password,
which is indicated by a lock badge in the list.

The client authenticates STOMP using the join response's session token, uses
10-second heartbeats, subscribes to the public room and private snapshot
destinations, and requests a fresh snapshot after each connection or reconnect.
The session token is stored in tab-scoped `sessionStorage` so a page reload
reconnects to the same room. Leaving the room clears it; closing the tab clears
the browser session storage.

Run `npm run build` and `npm run lint` to validate the frontend.
