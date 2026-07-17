# targeted-testing

Gatilho: qualquer alteracao de comportamento.

Entrada: modulo, comportamento e arquivos modificados.

Fluxo: escolher primeiro o menor teste capaz de provar a mudanca: parser→parser; avaliador→dominio;
Compose→UI; service→lifecycle; persistencia→DAO/repository. Amplie apenas para mudanca transversal,
merge/release, regressao complexa ou pedido explicito.

Saida: comando, aprovados, falhos, nao executados e motivo. Limite: nao declarar dispositivo validado
sem execucao real; testes conectados ficam para pre-release integrado.
