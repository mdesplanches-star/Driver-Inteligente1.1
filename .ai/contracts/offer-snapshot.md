# Contrato de oferta normalizada

Fonte de verdade: `app/src/main/java/br/com/nexo/driver/offer/OfferModels.kt`.

`NormalizedOffer` representa uma oferta localmente extraida. Campos criticos sao `source`, `kind`,
`detectedAtEpochMs`, `payout`, pernas `pickup` e `trip`, `passenger`, `rawLayoutVersion` e
`fieldConfidence`. Cada valor extraido usa `Confidence<T>` (`value`, `score` de 0 a 1, `source`).

Mapeamento de negocio: plataforma=`source`; categoria=`kind`/`serviceType`; tarifa=`payout`;
distancias e duracoes=`pickup` e `trip`; nota=`passenger.rating`; destino=`trip.location`;
confianca=`fieldConfidence` e cada `Confidence`; origem=`FieldSource` (OCR, ACCESSIBILITY, DERIVED).
`missing_fields` nao e armazenado como lista: e inferido de valores nulos ou abaixo do limiar.

Regra: ausencia ou confianca insuficiente vira `MetricStatus.UNKNOWN`; nao pode resultar em `ACCEPT`.
