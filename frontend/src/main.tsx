import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, NavLink, Routes, Route, Link } from "react-router-dom";
import {
  QueryClient,
  QueryClientProvider,
  useMutation,
} from "@tanstack/react-query";
import {
  LayoutDashboard,
  Users,
  Swords,
  GitCompareArrows,
  ClipboardList,
  Trophy,
  Settings,
  RefreshCw,
  Activity,
} from "lucide-react";
import { Overview } from "./pages/Overview";
import { Members, PlayerProfile } from "./pages/Players";
import { Wars } from "./pages/Wars";
import { Compare } from "./pages/Compare";
import { Recommendations, Awards } from "./pages/Recommendations";
import { Rules } from "./pages/Rules";
import { AuthGate, useAccount, useAdmin } from "./lib/auth";
import { Operations } from "./pages/Operations";
import { api } from "./lib/api";
import "./style.css";
import "./royale.css";
const client = new QueryClient();
const nav = [
  ["/", "Visão geral", LayoutDashboard],
  ["/membros", "Tripulação", Users],
  ["/guerras", "Guerras", Swords],
  ["/comparar", "Comparar", GitCompareArrows],
  ["/recomendacoes", "Recomendações", ClipboardList],
  ["/premiacoes", "Premiações", Trophy],
  ["/regras", "Regras", Settings],
  ["/operacoes", "Operações", Activity],
] as const;
function App() {
  const account = useAccount();
  const admin = useAdmin();
  const logout = useMutation({
    mutationFn: () => api("/auth/logout", {}),
    onSuccess: async () => {
      client.removeQueries({ predicate: (q) => q.queryKey[0] !== "/auth/me" });
      await client.resetQueries({ queryKey: ["/auth/me"] });
    },
  });
  const sync = useMutation({
    mutationFn: () =>
      api<{ kind: string; status: string; errorCode: string | null }[]>(
        "/collections/sync",
        {},
      ),
    onSuccess: () => client.invalidateQueries(),
  });
  return (
    <div className="app">
      <a className="skip" href="#main">
        Pular para conteúdo
      </a>
      <aside className="sidebar">
        <Link className="brand" to="/">
          <img src="/assets/royale/clan-emblem.png" alt="" />
          <span>
            FROTA
            <br />
            <b>FANTASMA</b>
          </span>
        </Link>
        <div className="sidebar-identity">
          <span>CLÃ • CLASH ROYALE</span>
          <div className="identity-rule" />
        </div>
        <span className="nav-caption">CENTRAL DE COMANDO</span>
        <nav>
          {nav.map(([to, name, Icon]) => (
            <NavLink key={to} to={to} end={to === "/"}>
              <Icon size={19} />
              {name}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <img
            className="sidebar-arena"
            src="/assets/royale/royal-arena.png"
            alt=""
          />
          <span className="live-dot" /> A história da nossa Frota
          <small>Histórico. Contexto. Decisão.</small>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span>
            CLASH ROYALE <span className="muted">/ Gestão do clã</span>
          </span>
          <div className="account-menu">
            <span>
              {account?.username} · {admin ? "Líder" : "Consulta"}
            </span>
            <button
              disabled={sync.isPending || !admin}
              onClick={() => sync.mutate()}
            >
              <RefreshCw size={15} className={sync.isPending ? "spin" : ""} />
              {sync.isPending ? "Coletando…" : "Atualizar dados"}
            </button>
            <button disabled={logout.isPending} onClick={() => logout.mutate()}>
              Sair
            </button>
          </div>
        </header>
        {logout.error && (
          <p role="alert" className="error">
            {logout.error.message}
          </p>
        )}
        {sync.error && (
          <div role="alert" className="error">
            {sync.error.message}
          </div>
        )}
        {sync.data && (
          <div className="sync-result" role="status">
            {sync.data.map((r, i) => (
              <span key={i}>
                {r.kind}: {r.status}
                {r.errorCode ? " (" + r.errorCode + ")" : ""}
              </span>
            ))}
          </div>
        )}
        <main id="main">
          <Routes>
            <Route path="/" element={<Overview />} />
            <Route path="/membros" element={<Members />} />
            <Route path="/membros/:tag" element={<PlayerProfile />} />
            <Route path="/guerras" element={<Wars />} />
            <Route path="/guerras/:id" element={<Wars />} />
            <Route path="/comparar" element={<Compare />} />
            <Route path="/recomendacoes" element={<Recommendations />} />
            <Route path="/recomendacoes/:id" element={<Recommendations />} />
            <Route path="/premiacoes" element={<Awards />} />
            <Route path="/operacoes" element={<Operations />} />
            <Route path="/regras" element={<Rules />} />
            <Route
              path="*"
              element={
                <>
                  <h1>Página não encontrada</h1>
                  <Link to="/">Voltar à visão geral</Link>
                </>
              }
            />
          </Routes>
        </main>
        <footer>
          <span>Frota Fantasma · decisões humanas, evidências claras.</span>
          <p>
            Conteúdo não oficial, não endossado pela Supercell. A Supercell não
            é responsável por este conteúdo.{" "}
            <a
              href="https://supercell.com/en/fan-content-policy/"
              target="_blank"
              rel="noreferrer"
            >
              Política de conteúdo de fãs
            </a>{" "}
            · <a href="/assets/royale/NOTICE.md">Créditos dos assets</a>
          </p>
        </footer>
      </div>
    </div>
  );
}
ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={client}>
      <BrowserRouter>
        <AuthGate>
          <App />
        </AuthGate>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
);
