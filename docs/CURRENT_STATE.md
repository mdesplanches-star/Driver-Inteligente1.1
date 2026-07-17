# Estado atual

- Versao: `0.1.0` (`versionCode` 1).
- Fase: desenvolvimento integrado; ambiente de agentes consolidado em 2026-07-17.
- Stack: Android unico modulo `:app`, Kotlin 2.2.10, AGP 8.13.2, Compose Material 3, ML Kit OCR local, JDK 17; SDK 29–36.
- Modulos ativos: accessibility, capture, ocr, parser, offer, evaluation, analysis, overlay, UI, destino/offline, profile, permission, location e speech.
- Pronto: leitura local por acessibilidade/OCR, parsers Uber/99, avaliacao visual/falada, overlay seguro, destino offline e testes unitarios/instrumentados selecionados.
- Incompleto: cobertura instrumentada do overlay/captura real e validacao final integrada em dispositivo continuam lacunas conhecidas.
- Riscos: drift dos layouts Uber/99; latencia OCR; release fica sem assinatura se `keystore.properties` nao existir; sem CI detectado; o runner de testes falha neste caminho Windows com caractere nao ASCII, por isso o gate completo deve rodar em caminho ASCII; historico local opt-in exige auditoria de privacidade.
- Ultimo gate: em 2026-07-17, guard e validacao de orquestracao passaram no workspace original. Em `C:\DriverInteligente-validation-20260717-165431`, tambem passaram `testDebugUnitTest`, `lintDebug`, `assembleDebug` e `assembleRelease`.
- Proximos passos: manter validacoes completas em caminho ASCII ate mover o workspace principal; usar Task Brief em delegacoes; anexar fixture e regressao a mudancas de parser; testar no Galaxy S23 somente no pre-release.
- Comandos principais: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .codex\scripts\validate-orchestration.ps1`; `./gradlew.bat testDebugUnitTest`; `./gradlew.bat lintDebug`; `./gradlew.bat assembleDebug`; `./gradlew.bat assembleRelease`.
