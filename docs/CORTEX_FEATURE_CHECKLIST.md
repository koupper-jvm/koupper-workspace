# CORTEX — Feature Checklist
_Orden de implementación por impacto. Tachar cuando esté hecho._

---

## Fase 1 — Agentes útiles

- [x] **AgentCreatorAgent con generación real de código**
  - Prompt compacto, sin paths locales (evita FederatedEngine privacy guard)
  - Usa `predict<String>` sin listener (stream:false → respuesta completa)
  - Correction loop: detecta TODOs y hace segundo pass al LLM
  - Fallback a scaffold si el LLM no está disponible
  - Nota: requiere que el LLM server (192.168.1.9:1234 o Groq) esté online

- [x] **HeartbeatAgent operativo**
  - Corregidos: imports (`@Export`, `jacksonObjectMapper`), `log.info {}` → `emit()`, formato de job JSON
  - 3 condiciones reales en `~/.koupper/heartbeat.md`: morning-digest (08:00→RSS), failed-jobs-alert, nightly-cleanup (23:00→DiskCleaner)
  - Loop de 60 s añadido a `koupper-start.sh`; cooldown persiste en `~/.koupper/heartbeat-state.json`
  - Verificado: compila, evalúa condiciones, despacha jobs con formato correcto, el worker los consume

- [x] **Agente útil de ejemplo — GitStatusAgent**
  - Conecta al MCP GitHub (servers.json), lista últimos 5 commits + PRs abiertos por repo
  - Repos configurables en `~/.koupper/gitstatus-repos.json` (default: koupper-jvm/*)
  - Digest diario en `~/.koupper/jobs/logs/default/gitstatus-YYYY-MM-DD.log`
  - Condición en heartbeat.md: corre a las 09:00, cooldown 720 min

- [ ] **Agente útil de ejemplo — FileWatcherAgent**
  - Monitorea un directorio configurable y dispara acciones cuando aparecen archivos nuevos
  - Caso de uso: watch `~/Downloads/`, procesar PDFs automáticamente

---

## Fase 2 — Canal Telegram (cerrar gap vs OpenClaw)

- [ ] **TelegramBridgeAgent flow completo verificado**
  - El agente existe y arranca, pero ¿el flow mensaje → CORTEX → respuesta funciona end-to-end?
  - Meta: mandar un mensaje real desde Telegram y recibir respuesta de CORTEX

- [ ] **TelegramChannelProvider como Service Provider**
  - Mover la lógica de Telegram a un SP reutilizable en el framework
  - Permite que cualquier agente use Telegram sin reimplementar el bot

---

## Fase 3 — Marketplace

- [ ] **`koupper agent list`** — listar agentes instalados con su skill.json
- [ ] **`koupper agent install <url>`** — descargar e instalar un agente desde una URL
- [ ] **Registry simple** — GitHub-based o S3, publicar spec de `skill.json`

---

## Fase 4 — Memoria y observabilidad

- [ ] **Métricas en el dashboard** — jobs/min, success rate, P95 latency en la WebUI
- [ ] **`memory.md` human-readable** — log legible por humanos junto a los embeddings vectoriales
- [ ] **VectorDbProvider real** — reemplazar el HashEmbedder por embeddings LLM reales (opcional, mejora recall)

---

## Bugs / mejoras técnicas pendientes

- [ ] **Duplicate log lines en CortexAgent** — a veces recibe el mismo comando dos veces (cosmético)
- [ ] **`koupper start --web` sin MCP** — ya parcialmente resuelto (MCP vive en octopus), verificar que el flag --web sigue sirviendo o documentar que ya no aplica
- [ ] **emit() vs log() en CortexAgent** — evaluar si el `log()` local puede reemplazarse por el `emit()` del preamble de octopus

---

## Notas

- El orden de esta lista es por impacto, no por dificultad
- Cada ítem debería ser una sesión o menos
- Al terminar un ítem: marcar con `[x]`, hacer `/checkpoint`
