# Status de release

- Build debug: aprovado em 2026-07-17 (`assembleDebug`) na copia ASCII `C:\DriverInteligente-validation-20260717-165431`; artefato: `app\build\outputs\apk\debug\app-debug.apk`.
- Build release: aprovado em 2026-07-17 (`assembleRelease`) na copia ASCII `C:\DriverInteligente-validation-20260717-165431`; artefato: `app\build\outputs\apk\release\app-release-unsigned.apk`.
- Testes/lint: `testDebugUnitTest` e `lintDebug` aprovados em 2026-07-17 na copia ASCII. No caminho original com caractere nao ASCII, o runner de testes falha com `ClassNotFoundException`; essa falha foi isolada como problema de ambiente.
- Assinatura: `assembleRelease` pode gerar APK nao assinado sem `keystore.properties`.
- Dispositivo: Galaxy S23 (Android 16+) e referencia de pre-release; nenhuma validacao e declarada aqui.
- Riscos: layouts de terceiros, lifecycle MediaProjection, OCR/latencia e cobertura instrumentada parcial.
- Bloqueadores atuais: checklist fisico em dispositivo real e assinatura de release quando houver `keystore.properties`.
- Aprovacao: gate local aprovado; pre-release fisico pendente.
