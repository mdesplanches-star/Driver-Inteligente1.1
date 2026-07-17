# offer-parser

Gatilho: alteracao em Accessibility, OCR, normalizacao, parser Uber/99 ou regras de confianca.

Entrada: layout/plataforma, texto ou fixture sanitizada, e contrato `offer-snapshot.md`.

Fluxo: registrar entrada bruta → identificar plataforma → normalizar → verificar campos criticos e
confianca → criar fixture e expectativa → adicionar regressao → testar fallback seguro.

Saida: plataforma, fixture, campos extraidos, campos ausentes, confianca esperada e teste. Limites:
nao persistir frame/OCR bruto/oferta; ausencia ou baixa confianca deve levar a `ANALYZE`, nunca a
aceite silencioso.
