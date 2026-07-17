# Mapa de modulos

| Caminho | Responsabilidade | Dependencias principais |
| --- | --- | --- |
| `app/src/main/java/br/com/nexo/driver/accessibility` | leitura de arvore acessivel | `analysis`, `ocr`, Android Accessibility |
| `capture` | servico MediaProjection, frames, latencia | `ocr`, Android media projection |
| `ocr` | OCR local e pipeline | ML Kit, `parser` |
| `parser`, `offer` | layouts e modelo normalizado | `evaluation` |
| `evaluation`, `analysis` | metricas e decisao | `offer`, `profile` |
| `destination`, `offline`, `location` | destino e dados locais de sessao | Android location/Geocoder, TSV |
| `overlay`, `speech`, `ui` | apresentacao segura e interface Compose | Compose, `evaluation` |
| `profile`, `permission` | preferencias e prontidao local | SharedPreferences, Android |
| `app/src/test` | regressao unitaria por dominio | JUnit |
| `app/src/androidTest` | lifecycle, OCR e renderizacao | AndroidX Test, fixtures |
