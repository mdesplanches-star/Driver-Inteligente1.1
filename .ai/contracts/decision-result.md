# Contrato de resultado da decisao

Fonte de verdade: `app/src/main/java/br/com/nexo/driver/evaluation/OfferEvaluator.kt`.

`EvaluationResult` contem `metrics`, `weightedScore` e `decision` (`ACCEPT`, `ANALYZE`, `REJECT`).
Cada `MetricEvaluation` preserva a regra aplicada, valor observado, confianca, status e score.
Campos ausentes ou abaixo do limiar produzem `UNKNOWN`; regras eliminatorias desconhecidas levam a
`ANALYZE`, e `ACCEPT` exige que nenhuma metrica seja `UNKNOWN`.
