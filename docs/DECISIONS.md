# Decisoes arquiteturais

## ADR-001 — Processamento estritamente local

Status: aceito.  
Contexto: ofertas e tela do motorista sao dados sensiveis.  
Decisao: sem `INTERNET`; OCR, parsing, avaliacao e diagnosticos sao locais.  
Consequencias: nao adicionar SDK de rede/analytics; dados brutos nao sao persistidos. Historico de
corridas pode existir somente como opt-in local, sanitizado, sem backup e com limpeza manual.

## ADR-002 — Overlay apenas informativo

Status: aceito.  
Contexto: a decisao final pertence ao motorista.  
Decisao: overlay usa `FLAG_NOT_TOUCHABLE` e `FLAG_SECURE`; sem acoes de acessibilidade ou gestos.  
Consequencias: o app so apresenta aceitar/analisar/recusar visual ou verbalmente.

## ADR-003 — Orquestracao enxuta

Status: aceito em 2026-07-17.  
Contexto: o repositorio ja possuia catalogo Codex e precisava de contratos e limites operacionais.  
Decisao: reutilizar `.codex/agents`, limitar paralelismo a 2/3 e manter skills/policies em `.ai/`.  
Consequencias: nenhuma segunda configuracao de agentes; Task Brief e ownership sao exigidos ao delegar.
