# release-gate

Gatilho: release, merge importante ou mudanca critica (servico, permissao, contrato, parsing,
DecisionEngine, concorrencia ou persistencia).

Entrada: diff, resultado dos testes direcionados e estado de assinatura.

Fluxo: clean quando aplicavel; testes unitarios e integracao disponivel; lint; builds debug/release;
R8/ProGuard; permissoes; servicos; logs; dados sensiveis; crash handling; documentacao; checklist
de dispositivo Samsung Galaxy S23 Android 16+.

Saida: aprovado/bloqueado, comandos, artefatos, assinatura, omissoes e riscos. Limite: nao instala
em dispositivo, publica ou assina sem autorizacao; `assembleRelease` sem keystore e nao assinado.
