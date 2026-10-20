import { test, expect } from "@playwright/test";
import type { WeeklyReviewReport } from "../src/lib/weeklyReview";
const report: WeeklyReviewReport = {
  week:"2026-10-12", generatedAt:"2026-10-19T00:59:00Z", cutoff:"2026-10-19T00:59:00Z", deadline:"2026-10-19T01:00:00Z",timezone:"America/Sao_Paulo",phase:"LIVE",pointsTarget:1600,offlineDays:5,collectionEnabled:true,
  membersCapturedAt:"2026-10-19T00:59:00Z",riverCapturedAt:"2026-10-19T00:58:00Z",riverSnapshotId:1,membersFresh:true,riverFresh:true,rosterComplete:true,
  members:[
    {tag:"#BOTH",name:"Duas regras",role:"member",points:900,decksUsed:6,lastSeen:"2026-10-12T00:59:00Z",offlineSeconds:604800,observedSince:"2026-08-01T00:00:00Z",belowPoints:true,offlineExceeded:true,status:"REVIEW",missingEvidence:[]},
    {tag:"#LOW",name:"Viagem avisada",role:"elder",points:1599,decksUsed:10,lastSeen:"2026-10-19T00:00:00Z",offlineSeconds:3540,observedSince:"2026-10-16T00:00:00Z",belowPoints:true,offlineExceeded:false,status:"REVIEW",missingEvidence:[]},
    {tag:"#UNKNOWN",name:"Sem registro",role:"member",points:null,decksUsed:null,lastSeen:null,offlineSeconds:null,observedSince:null,belowPoints:false,offlineExceeded:false,status:"UNKNOWN",missingEvidence:["Pontuação não informada para este membro; ausência de registro não equivale a zero."]},
    {tag:"#CLEAR",name:"No limite",role:"leader",points:1600,decksUsed:16,lastSeen:"2026-10-14T00:59:00Z",offlineSeconds:432000,observedSince:null,belowPoints:false,offlineExceeded:false,status:"CLEAR",missingEvidence:[]}
  ]
};
test.beforeEach(async({page})=>{
  await page.route("**/api/**",route=>{
    const path=new URL(route.request().url()).pathname;
    if(path==="/api/auth/me") return route.fulfill({json:{username:"lider",roles:["ADMIN"]}});
    if(path==="/api/auth/csrf") return route.fulfill({json:{headerName:"X-CSRF-TOKEN",token:"test-token"}});
    if(path==="/api/leadership/weekly-review/decisions") return route.fulfill({json:[]});
    if(path==="/api/leadership/weekly-review") return route.fulfill({json:report});
    if(path==="/api/leadership/notes/active") return route.fulfill({json:{generatedAt:report.generatedAt,timezone:report.timezone,notes:[]}});
    return route.fulfill({status:404,json:{detail:"Sem registro"}});
  });
});
test("menu, lista compacta, regras distintas e justificativa preservam a revisão",async({page},info)=>{
  let saved:Record<string,unknown>|null=null;
  await page.route("**/api/leadership/notes/active",route=>route.fulfill({json:{generatedAt:report.generatedAt,timezone:report.timezone,notes:saved?[saved]:[]}}));
  await page.route("**/api/leadership/players/**/notes",async route=>{
    const body=route.request().postDataJSON();expect(body.kind).toBe("JUSTIFICATION");expect(body.body).toContain("Revisão semanal de 12/10/2026");expect(body.requestKey).toBeTruthy();
    saved={...body,id:"note-1",playerTag:"#LOW",createdAt:report.generatedAt,createdBy:"lider",status:"ACTIVE",followUpOn:null,followUpDue:false,resolvedAt:null};
    return route.fulfill({json:saved});
  });
  await page.goto("/expulsoes");
  await expect(page.getByRole("heading",{name:"Expulsões semanais",exact:true})).toBeVisible();
  const nav=page.locator(".sidebar nav");
  const names=await nav.locator("a").allTextContents();expect(names.indexOf("Expulsões semanais")).toBe(names.indexOf("Premiações")+1);
  await expect(page.getByRole("heading",{name:"Expulsões semanais",exact:true})).toBeVisible();
  const rows=page.locator(".weekly-review-member");await expect(rows).toHaveCount(2);
  await expect(rows.first()).toContainText("Duas regras");
  await expect(page.getByText("Prévia em atualização",{exact:false}).first()).toBeVisible();
  const second=rows.nth(1);await second.locator(":scope > summary").click();
  await expect(second.getByText("Abaixo de 1.600 pontos",{exact:true})).toBeVisible();
  await second.getByText("Registrar justificativa para esta revisão",{exact:true}).click();
  await second.getByLabel("Motivo informado pelo membro").fill("Avisou viagem. Manter e reavaliar na próxima semana.");
  await second.getByRole("button",{name:"Salvar justificativa"}).click();
  await expect(second.getByText("Justificativa registrada no histórico do membro.")).toBeVisible();
  await expect(second.locator(":scope > summary")).toContainText("Tem justificativa");
  await expect(rows).toHaveCount(2); // A note never silently removes the flag.
  await page.getByLabel("Com justificativa vigente").check();await expect(rows).toHaveCount(1);
  await page.getByRole("button",{name:"Mostrar todos"}).click();await expect(rows).toHaveCount(4);
  await page.getByLabel("Filtrar revisão").selectOption("UNKNOWN");await expect(rows).toHaveCount(1);
  await expect(rows.first().locator(":scope > summary")).toContainText("Pontos desconhecidos");
  await page.getByLabel("Filtrar revisão").selectOption("CLEAR");await expect(rows.first()).toContainText("No limite");
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
  await page.screenshot({path:`test-results/weekly-review-${info.project.name}.png`,fullPage:true});
});
test("recorte fechado e coleta antiga mostram limites e semana solicitada",async({page})=>{
  await page.goto("/expulsoes");await expect(page.locator(".weekly-review-member")).toHaveCount(2);
  await page.route("**/api/leadership/weekly-review*",route=>{
    expect(new URL(route.request().url()).searchParams.get("week")).toBe("2026-10-12");
    return route.fulfill({json:{...report,phase:"CLOSED",membersFresh:false,riverFresh:false,collectionEnabled:false,members:[]}});
  });
  await page.getByLabel("Semana da revisão",{exact:true}).fill("2026-10-12");
  await expect(page.getByText("Recorte de domingo às 22h",{exact:false}).first()).toBeVisible();
  await expect(page.getByRole("alert").filter({hasText:"coleta automática está desativada"})).toBeVisible();
  await expect(page.getByText("Sem elenco coletado para este recorte. Consulte as coletas.")).toBeVisible();
});
test("consulta não vê menu nem solicita dados privados",async({page})=>{
  await page.route("**/api/auth/me",route=>route.fulfill({json:{username:"consulta",roles:["VIEWER"]}}));
  let privateRequests=0;page.on("request",request=>{if(request.url().includes("/api/leadership/"))privateRequests++;});
  await page.goto("/expulsoes");
  await expect(page.getByText("Esta revisão e as justificativas são reservadas à liderança.")).toBeVisible();
  await expect(page.locator(".sidebar nav").getByRole("link",{name:"Expulsões semanais"})).toHaveCount(0);
  expect(privateRequests).toBe(0);
});

test("decisões semanais preservam histórico, separam pendentes e não apagam sinais", async ({page}) => {
  const history: Record<string,unknown>[] = [];
  await page.route("**/api/leadership/weekly-review/decisions?*", route => {
    const week = new URL(route.request().url()).searchParams.get("week");
    return route.fulfill({json: history.filter(d => d.week === week)});
  });
  await page.route("**/api/leadership/weekly-review/decisions/*", route => {
    const body = route.request().postDataJSON();
    const current = history.find(d => d.week === body.week);
    expect(body.expectedRevision).toBe(current?.revision || 0);
    expect(body.requestKey).toBeTruthy();
    expect(body.reason.trim()).not.toBe("");
    const decision = {...body, revision:history.length+1,playerTag:"#BOTH",createdAt:report.generatedAt,createdBy:"lider"};
    history.unshift(decision);
    return route.fulfill({json:decision});
  });
  await page.route("**/api/leadership/weekly-review?*", route => route.fulfill({json:{...report,week:new URL(route.request().url()).searchParams.get("week")}}));
  await page.goto("/expulsoes");
  const rows=page.locator(".weekly-review-member");
  await expect(page.getByText("4 pendentes · 0 avaliados no elenco da semana")).toBeVisible();
  await page.getByLabel("Filtrar andamento").selectOption("pending");
  await rows.first().locator(":scope > summary").click();
  const form=page.getByRole("region",{name:"Decisão da revisão semanal"});
  await form.getByLabel("Decisão",{exact:true}).selectOption("WAIT");
  await form.getByLabel("Motivo da decisão").fill("Aguardando retorno do membro.");
  await form.getByRole("button",{name:"Salvar decisão"}).click();
  await expect(page.getByRole("status").filter({hasText:"Decisão registrada: Aguardar justificativa."})).toBeVisible();
  await expect(rows).toHaveCount(2);
  await form.getByLabel("Decisão",{exact:true}).selectOption("KEEP");
  await form.getByLabel("Motivo da decisão").fill("Justificativa aceita nesta semana.");
  await form.getByRole("button",{name:"Salvar decisão"}).click();
  await expect(rows).toHaveCount(1);
  await page.getByLabel("Filtrar andamento").selectOption("done");
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText("Duas regras");
  await rows.first().locator(":scope > summary").click();
  await form.getByText("Histórico de decisões desta semana (2)",{exact:true}).click();
  await expect(form.getByText("Aguardando retorno do membro.",{exact:true})).toBeVisible();
  await form.getByLabel("Decisão",{exact:true}).selectOption("PENDING");
  await form.getByLabel("Motivo da decisão").fill("Reavaliar após informação nova.");
  await form.getByRole("button",{name:"Salvar decisão"}).click();
  await expect(rows).toHaveCount(0);
  await page.getByLabel("Filtrar andamento").selectOption("pending");
  await expect(rows).toHaveCount(2);
  await page.reload();
  await expect(rows.first().locator(":scope > summary")).toContainText("Reabrir avaliação");
  await page.getByLabel("Semana da revisão",{exact:true}).fill("2026-10-05");
  await expect(rows.first().locator(":scope > summary")).toContainText("Pendente");
  await expect(page.getByText("4 pendentes · 0 avaliados no elenco da semana")).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

test("falha das decisões não classifica membros como pendentes nem permite sobrescrita",async({page})=>{
  await page.route("**/api/leadership/weekly-review/decisions?*",route=>route.fulfill({status:403,json:{detail:"Indisponível"}}));
  await page.goto("/expulsoes");
  await expect(page.getByRole("alert").filter({hasText:"Não foi possível carregar as decisões"})).toBeVisible();
  await expect(page.getByLabel("Filtrar andamento")).toBeDisabled();
  await page.locator(".weekly-review-member").first().locator(":scope > summary").click();
  await expect(page.getByRole("button",{name:"Salvar decisão"})).toHaveCount(0);
  await expect(page.getByText("4 pendentes · 0 avaliados no elenco da semana")).toHaveCount(0);
});
