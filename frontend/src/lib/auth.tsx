import {
  createContext,
  useContext,
  useState,
  useEffect,
  type ReactNode,
} from "react";
import { useQuery } from "@tanstack/react-query";
import { api, ApiError } from "./api";
import { QueryState } from "../components/ui";
import { art } from "../components/royale";
export interface Account {
  username: string;
  roles: string[];
}
const Context = createContext<Account | null>(null);
export const useAccount = () => useContext(Context);
export const useAdmin = () => useAccount()?.roles.includes("ADMIN") ?? false;
export function AuthGate({ children }: { children: ReactNode }) {
  const q = useQuery({
    queryKey: ["/auth/me"],
    queryFn: () => api<Account>("/auth/me"),
    retry: false,
    staleTime: 60000,
  });
  useEffect(() => {
    const expired = () => {
      q.refetch();
    };
    window.addEventListener("frota:session-expired", expired);
    return () => window.removeEventListener("frota:session-expired", expired);
  }, [q.refetch]);
  if (q.error instanceof ApiError && q.error.status === 401)
    return <Login onSuccess={() => q.refetch()} />;
  return (
    <QueryState query={q}>
      {q.data && <Context.Provider value={q.data}>{children}</Context.Provider>}
    </QueryState>
  );
}
function Login({ onSuccess }: { onSuccess: () => unknown }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  return (
    <main className="login-shell">
      <div className="login-art" aria-hidden="true">
        <img src={art("royal-ghost")} alt="" />
      </div>
      <section className="login-panel">
        <img className="login-crest" src={art("clan-emblem")} alt="" />
        <div className="eyebrow">FROTA FANTASMA</div>
        <h1>Bem-vindo a bordo.</h1>
        <p className="lede">Entre para acompanhar a história da tripulação.</p>
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setBusy(true);
            setError("");
            try {
              await api(
                "/auth/login",
                new URLSearchParams({ username, password }),
              );
              setPassword("");
              await onSuccess();
            } catch (e) {
              setError(e instanceof Error ? e.message : "Falha ao entrar.");
            } finally {
              setBusy(false);
            }
          }}
        >
          <label>
            Usuário
            <input
              autoComplete="username"
              required
              maxLength={100}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </label>
          <label>
            Senha
            <input
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button className="primary" disabled={busy}>
            {busy ? "Entrando…" : "Entrar na Frota"}
          </button>
        </form>
        <small>
          Conteúdo não oficial, não endossado pela Supercell.{" "}
          <a href="/assets/royale/NOTICE.md">Créditos</a>
        </small>
      </section>
    </main>
  );
}
