import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useLocation,
} from "react-router-dom";
import { GameProvider, useGame } from "./context/GameContext";
import EnterPage from "./page/enter/page";
import MakePage from "./page/make/page";
import WaitingPage from "./page/waiting/page";
import GamePage from "./page/game/page";
import "./App.css";

function EntryRoute({ children }: { children: React.ReactNode }) {
  const { nickname } = useGame();
  const location = useLocation();
  if (!nickname.trim())
    return <Navigate to="/enter" replace state={{ from: location.pathname }} />;
  return children;
}

function WaitingRoute() {
  const { hasSession } = useGame();
  if (!hasSession) return <Navigate to="/make" replace />;
  return <WaitingPage />;
}

function GameRoute() {
  const { hasSession } = useGame();
  if (!hasSession) return <Navigate to="/make" replace />;
  return <GamePage />;
}

function AppLayout() {
  const { nickname } = useGame();

  return (
    <main className="app-shell">
      <Routes>
        <Route
          path="/"
          element={
            <Navigate to={nickname.trim() ? "/make" : "/enter"} replace />
          }
        />
        <Route path="/enter" element={<EnterPage />} />
        <Route
          path="/make"
          element={
            <EntryRoute>
              <MakePage />
            </EntryRoute>
          }
        />
        <Route
          path="/waiting"
          element={
            <EntryRoute>
              <WaitingRoute />
            </EntryRoute>
          }
        />
        <Route
          path="/game"
          element={
            <EntryRoute>
              <GameRoute />
            </EntryRoute>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </main>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <GameProvider>
        <AppLayout />
      </GameProvider>
    </BrowserRouter>
  );
}
