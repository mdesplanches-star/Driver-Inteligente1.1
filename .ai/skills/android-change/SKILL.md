# android-change

Gatilho: correcao ou feature Android delimitada.

Entrada: Task Brief quando houver delegacao; contrato e modulo afetado.

Fluxo obrigatorio: localizar contrato → localizar implementacao → localizar testes → definir menor
mudanca → implementar → executar teste direcionado → executar lint relevante → resumir resultado.

Saida: resultado no formato `agent-result.schema.yaml`, incluindo comandos exatos. Limites: nao fazer
refatoracao transversal sem novo brief; preservar as garantias de privacidade e overlay seguro.
