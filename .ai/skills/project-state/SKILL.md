# project-state

Gatilho: inicio de tarefa que pode alterar estado, contrato, mais de um arquivo ou exigir delegacao.

Entrada: `docs/CURRENT_STATE.md` e, se necessario, os caminhos citados nele.

Fluxo: leia o estado resumido; identifique modulo, contrato, riscos e comando inicial; abra apenas os
arquivos diretamente relacionados. Atualize o estado somente apos uma mudanca material confirmada.

Saida: contexto em no maximo oito linhas: modulo, objetivo, riscos, teste inicial e proxima decisao.
Limite: nao substituir leitura do contrato ou teste real; nao copiar codigo-fonte ao estado.
