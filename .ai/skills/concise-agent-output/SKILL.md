# concise-agent-output

Gatilho: toda resposta interna de agente.

Use exatamente a estrutura de `.ai/contracts/agent-result.schema.yaml`: status, resumo, arquivos
lidos/alterados, testes (passaram/falharam/nao executados), decisoes, riscos, bloqueios e follow-up.

Mantenha comandos, codigo e mensagens de erro intactos. Nao repita a tarefa nem escreva introducao.
