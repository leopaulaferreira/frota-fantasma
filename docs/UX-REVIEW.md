# Revisão visual desktop — concluída em 24/11/2026

Escopo definido com o usuário: uso em desktop. Celular está fora desta entrega.

## Diagnóstico e implementação

React/TypeScript, React Router, TanStack Query, Lucide e CSS local. Rotas em
`main.tsx`; componentes compartilhados em `components/ui.tsx`. Os estilos anteriores
estão distribuídos em quatro arquivos. `product.css` concentra tokens e padrões
novos sem reescrever regras de domínio.

| Módulo | Problema observado / ação |
|---|---|
| Visão geral | Superfícies e indicadores competiam: cartões sem gradientes e sombras excessivas |
| Tripulação | Filtros desalinhados: labels acima dos controles |
| Perfil | Muitas métricas: preservada hierarquia numérica e explicações |
| Guerras | Filtros densos: alinhamento vertical consistente; cinco indicadores preservados |
| Comparativo | Tabela com altura limitada, cabeçalho visível e rolagem para listas extensas |
| Comparar | Nome ambíguo no menu: Comparar jogadores; rota preservada |
| Doações | Tabela larga: rolagem natural e identificação mantida |
| Recomendações | Contraste e superfícies unificados; classificações preservadas |
| Premiações | Artes mantidas; controles e superfícies compartilhados |
| Expulsões | Tabela com rolagem para listas extensas; decisões preservadas |
| Regras | Formulário dividido por responsabilidade, publicação separada |
| Operações | Superfícies e filtros consistentes; importações intactas |

Regras agora agrupa participação, atividade/histórico, reconhecimento, qualidade,
score e publicação. Validações e expectedVersion permanecem intactos. UTC é um
padrão de configuração do backend e o fuso participa do recorte de semanas;
permanece somente leitura. Nenhum valor foi alterado.

## Fechamento visual

Revisadas as 15 rotas da auditoria local: visão geral, tripulação, perfil, doações,
guerras, comparativo, comparação de jogadores, candidatos, acompanhamento,
avaliações, avaliação individual, premiações, revisão semanal, regras e operações.
A comparação das capturas anteriores e posteriores identificou e corrigiu:

- Valores dos gráficos semanais quebrados no meio do número: gráficos com mais
  espaço e valores em uma linha, preservando os números e marcas de dados parciais.
- Cartões com alturas e conteúdos desalinhados: altura uniforme por linha,
  conteúdo alinhado ao topo e espaço reservado para os ícones.
- Contexto e horário de captura colados à qualidade: informações em linhas distintas.
- Cartões de acompanhamento e histórico encostados: espaçamento entre registros.

Mantidos os filtros, as evidências em diálogos e as tabelas extensas com rolagem
interna e cabeçalho visível. As capturas locais com dados reais ficam fora do Git.

## Validação

- Build de produção concluído; frontend local atualizado via Docker Compose.
- 54 testes unitários do frontend e 67 testes Playwright desktop passaram.
- Teste de erro recuperável ampliado com capturas de carregamento, erro e lista
  vazia; executado novamente com sucesso e capturas inspecionadas.
- Auditoria autenticada: 15 rotas e 195 jogadores, sem divergências nas métricas
  conferidas, erros JavaScript ou transbordamento horizontal em 1440 × 1000.
- Checagem adicional em 1366 × 768: cartões alinhados, diálogo de evidências
  abre e fecha, sem transbordamento horizontal.
- Revisão das capturas do dashboard, perfil, tabelas, formulários, históricos e
  estados compartilhados. Os testes desktop também verificam abertura e fechamento
  das evidências, filtros, permissões e erros de consulta.

Comandos reproduzíveis em [FRONTEND.md](FRONTEND.md#verificação); resumo da execução
local em [DESKTOP-VALIDATION.json](DESKTOP-VALIDATION.json). A auditoria usa o histórico
local e não é uma comparação nova com a Supercell. A conclusão refere-se ao visual
para desktop; não inclui testes de celular nem publicação externa.
