# Matriz de testes

| Funcionalidade | Tipo | Arquivo/exemplo | Status | Lacuna |
| --- | --- | --- | --- | --- |
| Parser Uber/99 | unitario | `parser/OfferTextParserTest.kt` | coberto | novos layouts exigem fixture |
| Pipeline OCR | unitario/instrumentado | `ocr/OfferOcrPipelineTest.kt`, screenshots | coberto | cards reais adicionais |
| Avaliacao | unitario | `evaluation/OfferEvaluatorTest.kt` | coberto | regras futuras |
| Captura/lifecycle | unitario/instrumentado | `capture/*Test.kt`, `OfferCaptureServiceLifecycleTest.kt` | parcial | revogacao/rotacao ampliadas |
| Overlay | unitario/instrumentado | `overlay/*Test.kt` | parcial | overlay real em mais estados |
| Destino/offline | unitario | `destination/**Test.kt` | coberto | cenarios de pacote maior |
| Permissao/localizacao | unitario/instrumentado | `permission`, `location` tests | parcial | pre-release no aparelho |
| Release | gate manual | `.ai/skills/release-gate` | pendente por release | dispositivo S23 e assinatura |
