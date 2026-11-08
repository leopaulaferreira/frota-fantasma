# Revisão de produto — 08/11/2026

## Diagnóstico e implementação desta etapa

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
| Comparativo | Rolagem vertical aninhada removida; horizontal preservada |
| Comparar | Nome ambíguo no menu: Comparar jogadores; rota preservada |
| Doações | Tabela larga: rolagem natural e identificação mantida |
| Recomendações | Contraste e superfícies unificados; classificações preservadas |
| Premiações | Artes mantidas; controles e superfícies compartilhados |
| Expulsões | Removida altura máxima da tabela; decisões preservadas |
| Regras | Formulário dividido por responsabilidade, publicação separada |
| Operações | Superfícies e filtros consistentes; importações intactas |

Regras agora agrupa participação, atividade/histórico, reconhecimento, qualidade,
score e publicação. Validações e expectedVersion permanecem intactos. UTC é um
padrão de configuração do backend e o fuso participa do recorte de semanas;
permanece somente leitura. Nenhum valor foi alterado.

## Validação e limites

Capturas via Playwright com APIs simuladas em desktop e mobile. A auditoria geral
produz 26 capturas; Comparativo e Expulsões dependem das fixtures específicas de
suas suítes para dados completos. Evidência histórica individual na auditoria geral
exibe erro de API simulada, não comprova o estado preenchido. O teste visual de
Expulsões da auditoria geral não comprova o conteúdo (fixture incompatível).
Não considerar estas capturas validação completa de todos os estados reais.

Build e testes são executados por npm; não existe script de lint no package.json.
A remoção do limite vertical prioriza a página; o cabeçalho sticky dentro de um
contêiner com overflow horizontal não equivale a um cabeçalho fixo na viewport.
Ainda falta auditoria autenticada de todas as rotas com dados locais, comparações
antes/depois e revisão detalhada de cada estado. Esta etapa não encerra o redesign
global solicitado.
