<div align="center">

<img src="frontend/public/assets/royale/clan-emblem.png" width="88" alt="Emblema da Frota Fantasma" />

# Frota Fantasma Analytics

### Uma tripulação. Um legado.

**Uma central de comando para cuidar do clã, reconhecer quem participa<br />e tomar decisões com história, contexto e evidências.**

Clash Royale · Frota Fantasma · **#RQJGC9GV**

[Conheça os recursos](#-o-que-você-pode-fazer) · [Veja o painel](#-a-frota-em-tela) · [Guia de execução](docs/GUIA-DE-EXECUCAO.md)

</div>

---

## ⚓ Liderar um clã vai além da pontuação

Quem participa com consistência? Quem merece uma oportunidade como Ancião?
Uma semana ruim foi exceção ou parte de uma queda de desempenho?
E aquele membro que avisou que precisaria se ausentar?

O **Frota Fantasma Analytics** reúne guerras, doações, atividade e trajetória dos
membros em um só lugar. Você acompanha o presente, consulta o passado e registra
o contexto de cada decisão, sem depender apenas da memória ou de números isolados.

> **A ferramenta organiza as evidências. O líder decide.**
> Promoções e expulsões nunca são executadas automaticamente no jogo.

## 🧭 O que você pode fazer

| Área | Como ajuda a liderança |
| --- | --- |
| **Visão do clã** | Acompanhe o pulso da semana, os destaques e a evolução da Frota em um painel. |
| **Tripulação e perfis** | Conheça a trajetória de cada membro: guerras, doações, atividade, cargos e anotações da liderança. |
| **Guerras** | Consulte a corrida atual e resultados anteriores, com pontos, decks utilizados e participação registrada. |
| **Comparar jogadores** | Veja membros lado a lado e compare desempenho no mesmo contexto histórico. |
| **Candidatos a Ancião** | Encontre candidatos ordenados, confira critérios atendidos e abra as evidências antes de decidir. |
| **Doações** | Consulte a semana atual e as cinco anteriores, preservando os números coletados mesmo após o reset do jogo. |
| **Premiações** | Acompanhe a projeção da semana e consulte o histórico dos títulos formalizados. |
| **Revisão semanal** | Organize os casos que precisam de avaliação, registre justificativas e acompanhe decisões pendentes. |

## 🖼️ A Frota em tela

### Uma visão para começar a semana

Indicadores do clã e destaques reunidos em uma interface com a identidade da Frota.

![Dashboard do Frota Fantasma Analytics](docs/screenshots/frontend-desktop.png)

### Evidências por trás dos indicadores

Consulte como cada indicador foi calculado, a origem dos dados e o período
analisado para interpretar os resultados no contexto da trajetória do membro.

![Explicação da média das últimas quatro guerras](docs/screenshots/metric-explanation-desktop.png)

<sub>Capturas demonstrativas da aplicação com dados fictícios. Os resultados reais dependem do histórico disponível.</sub>

## 👑 Reconheça quem faz a diferença

<table>
<tr>
<td align="center" width="33%">
<img src="frontend/public/assets/royale/skeleton-king.png" height="140" alt="Rei Esqueleto" /><br />
<strong>Capitão da Guerra</strong><br />
Destaque pela pontuação na guerra.
</td>
<td align="center" width="33%">
<img src="frontend/public/assets/royale/fisherman.png" height="140" alt="Pescador" /><br />
<strong>Guardião da Frota</strong><br />
Reconhecimento pelas doações observadas.
</td>
<td align="center" width="33%">
<img src="frontend/public/assets/royale/royal-ghost.png" height="140" alt="Fantasma Real" /><br />
<strong>Alma Imortal</strong><br />
Consistência ao longo das guerras analisadas.
</td>
</tr>
</table>

Os títulos seguem os critérios e a amostra disponíveis. A projeção é provisória;
o reconhecimento formalizado fica registrado. Um jogador não acumula títulos
na mesma semana, e categorias sem dados suficientes podem ficar sem vencedor.

## 🏴‍☠️ Uma revisão mais justa da tripulação

A **revisão semanal de expulsões** reúne os membros que precisam de atenção e
ajuda a organizar a avaliação individual. Na regra atual, são analisados casos
com menos de **1.600 pontos** ou mais de **cinco dias offline**, respeitando a
disponibilidade e a atualização dos dados.

O líder pode registrar uma decisão: **manter, conversar, dispensar ou aguardar
justificativa**. Cada registro conserva motivo, autor e histórico; avaliações
podem ser reabertas quando surgir novo contexto.

Assim, um aviso de ausência ou uma justificativa não se perde no meio dos números.
A decisão registrada no painel não remove ninguém do jogo.

## 📅 O tempo vira contexto

**Guerras contam uma trajetória.** Médias recentes, últimas quatro guerras e
tendências ajudam a distinguir consistência de um resultado isolado.

**Doações não somem com o reset.** As capturas já realizadas permanecem salvas.
A consulta de seis semanas se renova automaticamente; sair dessa janela não apaga
o histórico armazenado. Os contadores e as doações observadas têm suas limitações
explicadas no painel.

**Recomendações mostram seus motivos.** Critérios, pendências e evidências ajudam
a entender por que um membro aparece como candidato ou merece acompanhamento.

## 🔎 Confiança para decidir

- **Fonte oficial como base:** os dados da Supercell têm prioridade.
- **Números com explicação:** consulte período, origem e limitações das métricas.
- **Lacunas visíveis:** informação desconhecida não vira zero nem certeza artificial.
- **Contexto preservado:** notas da liderança, avaliações e decisões guardam histórico.
- **Acesso por perfil:** liderança e consulta têm permissões distintas; notas privadas ficam restritas.
- **Histórico complementar:** importações manuais de CSV do RoyaleAPI, nos formatos suportados, passam por análise, identificação e confirmação. Conflitos não sobrescrevem dados oficiais.

O histórico cresce enquanto a aplicação coleta dados. Informações anteriores que
não foram capturadas não são recuperadas apenas com o passar do tempo.

## 🚀 Quer colocar a Frota para navegar?

Configuração, instalação e comandos estão no **[Guia de execução](docs/GUIA-DE-EXECUCAO.md)**.

| Quero saber mais sobre… | Onde encontrar |
| --- | --- |
| Instalar e executar a aplicação | [Guia de execução](docs/GUIA-DE-EXECUCAO.md) |
| Entender indicadores e critérios | [Métricas e análises](docs/ANALYTICS.md) |
| Conhecer as telas e os fluxos | [Guia do painel](docs/FRONTEND.md) |
| Revisar membros ao fim da semana | [Revisão semanal](docs/WEEKLY-REVIEW.md) |
| Importar arquivos históricos | [Importação RoyaleAPI](docs/ROYALEAPI-IMPORT.md) |
| Cuidar de acesso, backups e publicação | [Operação](docs/OPERATIONS.md) |

---

<div align="center">

**A Frota não afunda. O legado se constrói a cada semana. 👻⚓**

</div>

<sub>Projeto de fãs, não oficial e não endossado pela Supercell. As artes de Clash Royale são disponibilizadas pelo repositório RoyaleAPI; consulte os [créditos e condições de uso](frontend/public/assets/royale/NOTICE.md).</sub>
