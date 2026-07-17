# compose-ui-review

Gatilho: nova tela, componente Compose, overlay, token visual ou reorganizacao de interface.

Entrada: composables e estados afetados; screenshot/teste visual quando disponivel.

Fluxo: revisar legibilidade, contraste, escala de fonte, hierarquia, feedback, alvo de toque,
TalkBack, modo noturno, densidades, loading/erro/vazio e carga visual para uso automotivo. Para
overlay, confirmar `FLAG_NOT_TOUCHABLE` e `FLAG_SECURE`.

Saida: achados priorizados, testes vistos e risco residual. Limite: nao mudar tokens do design system
sem justificativa; revisao e somente leitura salvo autorizacao explicita.
