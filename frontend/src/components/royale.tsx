import type { PointerEvent } from "react";
import { Link } from "react-router-dom";
import { ArrowUpRight } from "lucide-react";
import { date, type Highlight, type Player } from "../lib/api";
import { title } from "../lib/labels";
import { MetricView } from "./ui";

export const art = (name: string) => "/assets/royale/" + name + ".png";

// Move decorative layers only; text and controls remain stationary.
function followArt(event: PointerEvent<HTMLElement>) {
  if (event.pointerType !== "mouse" || !window.matchMedia("(hover: hover) and (prefers-reduced-motion: no-preference)").matches) return;
  const bounds = event.currentTarget.getBoundingClientRect();
  const x = Math.max(0, Math.min(1, (event.clientX - bounds.left) / bounds.width));
  const y = Math.max(0, Math.min(1, (event.clientY - bounds.top) / bounds.height));
  const style = event.currentTarget.style;
  style.setProperty("--art-x", `${(x - 0.5) * 16}px`);
  style.setProperty("--art-y", `${(y - 0.5) * 12}px`);
  style.setProperty("--light-x", `${x * 100}%`);
  style.setProperty("--light-y", `${y * 100}%`);
}
function resetArt(event: PointerEvent<HTMLElement>) {
  for (const key of ["--art-x", "--art-y", "--light-x", "--light-y"]) {
    event.currentTarget.style.removeProperty(key);
  }
}
const artInteraction = {
  onPointerMove: followArt,
  onPointerLeave: resetArt,
  onPointerCancel: resetArt,
};

export function FleetBanner({ week }: { week?: string }) {
  return (
    <section className="fleet-banner" {...artInteraction}>
      <div className="art-light" aria-hidden="true" />
      <div className="fleet-banner-copy">
        <div className="eyebrow">
          <span className="live-dot" /> FROTA FANTASMA · CENTRAL DO CLÃ
        </div>
        <h1>
          Uma tripulação.
          <br />
          <span>Um legado.</span>
        </h1>
        <p>
          Seu clã, seu comando. Reconheça quem faz a diferença e escolha o
          próximo rumo da Frota.
        </p>
        <div className="banner-actions">
          <Link to="/recomendacoes?tipo=candidatos" className="button primary">
            Avaliar candidatos <ArrowUpRight size={17} />
          </Link>
          <Link className="banner-link" to="/guerras">
            Diário de guerras →
          </Link>
        </div>
        <small className="banner-period">
          {week
            ? "SEMANA DE " + date(week)
            : "HISTÓRICO · PARTICIPAÇÃO · RECONHECIMENTO"}
        </small>
      </div>
      <div className="fleet-banner-art" aria-hidden="true">
        <div className="crest-ring" />
        <img className="banner-king" src={art("skeleton-king")} alt="" />
        <span className="banner-art-caption">#RQJGC9GV · A FROTA NÃO AFUNDA</span>

        <img
          className="banner-ghost"
          src={art("royal-ghost")}
          alt=""
          fetchPriority="high"
        />
        <div className="banner-seal">
          <img src={art("clan-emblem")} alt="" />
          <span>
            FROTA
            <br />
            <b>FANTASMA</b>
          </span>
        </div>
      </div>
    </section>
  );
}

const awardArt: Record<
  string,
  { character: string; badge: string; label: string; number: string }
> = {
  CAPITAO_DA_GUERRA: {
    character: "skeleton-king",
    badge: "war-emblem",
    label: "Pontos na guerra",
    number: "01",
  },
  GUARDIAO_DA_FROTA: {
    character: "fisherman",
    badge: "cards-token",
    label: "Doações observadas",
    number: "02",
  },
  ALMA_IMORTAL: {
    character: "royal-ghost",
    badge: "crown-emblem",
    label: "Média de pontuação",
    number: "03",
  },
};

export function AwardCard({
  highlight: h,
  players,
  week,
}: {
  highlight: Highlight;
  players: Player[];
  week?: string;
}) {
  const visual = awardArt[h.title] || awardArt.CAPITAO_DA_GUERRA;
  const winner = players.find((p) => p.tag === h.playerTag);
  const weeklyDonationPeak = h.title === "GUARDIAO_DA_FROTA"
    && h.result.period.basis.startsWith("WEEKLY_PEAK_COUNTER");
  return (
    <article className={"royale-award award-" + visual.number} {...artInteraction}>
      <div className="award-stage" aria-hidden="true">
        <div className="art-light" />
        <span className="award-number">{visual.number}</span>
        <img className="award-character" src={art(visual.character)} alt="" />
        <img className="award-badge" src={art(visual.badge)} alt="" />
      </div>
      <div className="award-content">
        <small className="eyebrow">
          PROVISÓRIO{week ? " · " + date(week) : ""}
        </small>
        <h3>{title(h.title)}</h3>
        {h.playerTag ? (
          <Link
            className="award-winner"
            to={"/membros/" + encodeURIComponent(h.playerTag)}
          >
            {winner?.name || h.playerTag} <ArrowUpRight size={14} />
          </Link>
        ) : (
          <p className="muted">Aguardando candidato elegível</p>
        )}
        {weeklyDonationPeak && <p className="muted">Doações · semana encerrada de {date(h.result.period.from)}</p>}
        <MetricView compact name={weeklyDonationPeak ? "Maior contador semanal coletado" : visual.label} metric={h.result} />
        {h.title === "CAPITAO_DA_GUERRA" && winner?.wars[0] && (
          <MetricView
            compact
            name="Decks na guerra"
            metric={winner.wars[0].decksUsed}
          />
        )}
      </div>
    </article>
  );
}
