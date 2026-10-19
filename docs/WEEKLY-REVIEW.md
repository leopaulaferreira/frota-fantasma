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
