# Arquitetura vigente

O Driver Inteligente e um app Android local de modulo unico `:app`. A leitura de tela por
`DriverAccessibilityService` e somente leitura; quando insuficiente, `OfferCaptureService` usa
MediaProjection e ML Kit on-device. O pipeline normaliza a oferta, faz parsing Uber/99, calcula
metricas e produz uma decisao visual `ACCEPT`, `ANALYZE` ou `REJECT`.

`Accessibility/OCR → OfferOcrPipeline → OfferParserRegistry → NormalizedOffer → DestinationOfferEnricher → OfferEvaluator → OfferOverlayPresenter → WindowManagerOfferOverlay`

O overlay e `FLAG_NOT_TOUCHABLE` e `FLAG_SECURE`. Nao ha INTERNET, automacao de toque, persistencia
de frames/OCR bruto/screenshot/arvore de acessibilidade ou diagnostico remoto. O historico local de
corridas e opt-in, guarda somente resumo sanitizado e deve ter limpeza manual. Consulte `ARCHITECTURE_REVIEW.md` para
detalhamento tecnico e `.ai/contracts/` para contratos operacionais.
