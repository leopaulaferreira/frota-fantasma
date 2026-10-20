# Revisão semanal de expulsões

A página `/expulsoes`, logo abaixo de **Premiações**, é exclusiva de ADMIN.
Ela responde à revisão de domingo do líder, independentemente do status
operacional calculado a partir de várias guerras. Nenhum cargo ou vínculo com
o clã é alterado pelo sistema.

## Regras e fonte

Um membro entra em **Avaliar individualmente** ao satisfazer pelo menos uma regra:

- `fame < 1600` na captura da corrida daquela semana;
- intervalo entre `api_last_seen` e a captura de membros **maior que 5 dias**.

1.600 pontos exatos e 5 dias exatos não acionam os respectivos critérios.
Todos os cargos do elenco observado participam da revisão. As referências
1.600/5 correspondem à regra semanal solicitada e são independentes das regras
de promoção e do status operacional.

A pontuação vem do `clan.participants` da corrida atual persistida, em dia de
guerra (`periodType=warDay`), capturada desde quinta-feira da semana no fuso
America/Sao_Paulo. Não se usa média de quatro guerras, corrida da semana passada
ou resultado oficial anterior como substituto. Participante ausente, pontuação
nula/negativa ou não numérica são desconhecidos; zero explícito é um valor válido.

As duas fontes precisam ter no máximo uma hora de idade em relação ao recorte.
O elenco deve corresponder integralmente à última captura de `clan_history` e
seu `run_id`; ex-membros de capturas anteriores não são incorporados à lista.
Sem elenco confiável, não se conclui descumprimento. Uma fonte de guerra
indisponível não impede um alerta de inatividade comprovado, e vice-versa.
Última atividade futura ou ausente não gera alerta de inatividade.

## Domingo às 22h

O recorte é domingo às **22:00 em America/Sao_Paulo**. Antes dele, a prévia usa as
capturas disponíveis até agora. Depois, a consulta usa somente capturas feitas
até o recorte. São Paulo pode ainda estar no domingo quando UTC já está na
segunda; a seleção e o agendamento usam explicitamente o fuso local.

O histórico já existente de capturas sustenta esse recorte; não é necessária
nova tabela ou cópia do relatório. O GET é somente leitura e usa transação
REPEATABLE_READ para combinar as fontes. Pontuações e elenco coletados após
22h não alteram o recorte anterior. Isto não significa que a API já publicou o
resultado oficial final da guerra.

Com `COLLECTION_ENABLED=true`, além das coletas normais, o scheduler tenta
coletar membros e corrida a cada cinco minutos no domingo entre 18h e 21h55,
e às 21h59. Chaves de deduplicação incluem clã, fonte e minuto; falha de uma
fonte não impede a tentativa da outra. Não há chamadas externas em GETs.

O processo precisa estar em execução, com internet e token válido. Falhas de
API ou máquina desligada aparecem como ausência/idade das fontes; o sistema
não inventa uma coleta das 22h. Na reabertura, o recorte pode ser reconstruído
com as capturas históricas disponíveis, sempre com sua qualidade explícita.

A página consulta a API interna a cada minuto enquanto aberta. O botão global
**Atualizar dados** aciona a coleta manual; **Recarregar lista** consulta os dados
já persistidos. Depois do recorte, uma nova coleta não retroage ao domingo.

## Consulta e justificativas

`GET /api/leadership/weekly-review?week=YYYY-MM-DD` recebe a segunda-feira de uma
semana atual ou passada. Sem parâmetro, de segunda a quarta abre a semana
anterior; de quinta a domingo abre a semana em andamento. O endpoint segue a
proteção ADMIN existente de `/api/leadership/**`.

- **Avaliar individualmente:** pelo menos um critério confirmado; outras
  evidências ainda podem estar pendentes.
- **Conferir dados:** nenhuma infração confirmada, mas há informação insuficiente.
- **Sem sinal pelas regras:** as duas verificações têm evidência suficiente e
  não acionam os critérios.

A lista é única, compacta, numerada e expansível. Ordenação: revisão, dados
pendentes, sem sinal; dentro de revisão, primeiro as duas regras acionadas,
depois menor pontuação, maior tempo offline e tag para desempate. Busca,
situação e presença de justificativa vigente podem ser filtradas.

Notas vigentes são apresentadas como **contexto atual**, inclusive na consulta
a recortes anteriores. O formulário registra uma nota `JUSTIFICATION`, com
referência à semana, autor da sessão, validade opcional e chave de idempotência.
A nota não remove o membro da revisão nem muda sua pontuação. O líder pode
registrar que decidiu manter o membro, combinar uma próxima revisão e consultar
ou encerrar as notas no perfil. As regras de privacidade e CSRF das notas são
preservadas. Não se infere que ausência de nota significa ausência de aviso.

## Validação

Testes unitários verificam limites exatos, valores ausentes/zero, capturas
antigas, atividade inválida, fuso e agenda de domingo. `WeeklyReviewIT` usa
PostgreSQL descartável para verificar o recorte entre capturas anteriores e
posteriores às 22h, elenco correto, leitura sem escrita, datas inválidas e
permissões ADMIN. Playwright valida navegação, filtros, justificativas,
indicadores de coleta e bloqueio de contas de consulta em desktop e mobile.

## Decisão individual e andamento

Ao expandir um membro, a liderança pode registrar **Manter nesta semana**,
**Conversar com o membro**, **Dispensar do clã**, **Aguardar justificativa** ou
**Reabrir avaliação**, sempre com motivo. Manter, Conversar e Dispensar contam
como avaliados; sem decisão, Aguardar e Reabrir contam como pendentes. Conversar
significa que essa foi a decisão registrada, não que a conversa já ocorreu.
O filtro de andamento combina com os filtros de regras, nome e justificativa.
Os contadores de andamento se referem ao elenco inteiro do recorte.

As decisões são próprias de cada clã, membro e semana. Abrir outra semana não
carrega a decisão da anterior. Cada alteração preserva as versões anteriores,
com autor autenticado, horário e motivo. O histórico fica dentro do membro na
semana consultada. Os sinais das regras e as justificativas continuam visíveis.
**Dispensar não expulsa o jogador:** a remoção é feita manualmente no jogo.

A migration V7 cria `weekly_review_decision`, com histórico imutável e índice
por clã/semana/membro. Cada registro guarda também as evidências do membro e
os horários das fontes consultadas no momento da decisão. Essas evidências são
preservadas no banco; o histórico visual mostra decisão, motivo, autor e data.
Nenhuma migration anterior foi alterada.

- `GET /api/leadership/weekly-review/decisions?week=YYYY-MM-DD`: revisões em
  ordem decrescente; o primeiro registro de cada membro é a decisão vigente.
- `POST /api/leadership/weekly-review/decisions/{tag}`: `week`, `action`,
  `reason`, `requestKey` (UUID) e `expectedRevision` (zero na primeira decisão).
- Semana deve ser uma segunda-feira atual ou passada; membro deve pertencer
  ao elenco daquele recorte. Motivo obrigatório, até 2.000 caracteres.
- ADMIN e CSRF são exigidos. Autor vem da sessão, nunca do formulário.
- Repetição idêntica da chave retorna a revisão original. Reutilização da chave
  para outro conteúdo ou revisão desatualizada retorna 409, sem sobrescrever.
- Atualizações concorrentes são serializadas por clã e conferem a revisão
  esperada. Falha ao carregar decisões não classifica membros como pendentes;
  o formulário só é liberado quando o histórico está disponível.

Testes em PostgreSQL descartável cobrem migration, imutabilidade, autorização,
CSRF, validação, idempotência, revisão concorrente e evidências preservadas.
Testes de navegador cobrem pendentes/avaliados, reabertura, troca de semana,
recarregamento e falhas, no computador e no celular.
