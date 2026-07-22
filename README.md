# Koupper Framework: The Future of Agentic Automation

## Community users

Koupper is a Kotlin scripting runtime + CLI for automation and infrastructure workflows.

- **Install (standalone):** [GitHub Releases v7.2.0](https://github.com/koupper-jvm/koupper/releases/tag/v7.2.0) — download `install-standalone.kts` and run with `kotlinc -script` (see full steps in Getting Started).
- **Documentation:** https://koupper.com/getting-started
- **Distribution:** GitHub Releases (not Maven Central).

```bash
curl -L -o install-standalone.kts https://github.com/koupper-jvm/koupper/releases/latest/download/install-standalone.kts
kotlinc -script install-standalone.kts -- --force
```

---

## Maintainers (español)

Este repositorio es el punto central del ecosistema Koupper. Contiene el core del motor Octopus, las herramientas de CLI y la documentación estratégica para construir sistemas basados en agentes.

---

## 🐙 Novedades Modernización 2026 (v7.2.0)

Hemos realizado un salto tecnológico masivo para convertir a Koupper en un framework de grado enterprise, más rápido, más limpio y 100% asíncrono.

### 1. **Scripting DX: Magia Zero-Boilerplate**
Escribir scripts de automatización nunca fue tan fácil. El motor Octopus ahora inyecta automáticamente el contexto necesario:
*   **Auto-Import:** Ya no necesitas importar `@Export`. Úsalo directamente.
*   **Namespace Seguro:** Accede a todos los Service Providers a través del objeto global `koupper`.
    *   `koupper.json()` -> Manipulación de JSON.
    *   `koupper.dynamo()` -> Acceso a AWS DynamoDB.
    *   `koupper.mailing()` -> Envío de correos.
*   **Atajo `log`:** Olvida la verbosidad. Usa `log.info { "" }` para loggear de forma estructurada (JSONL) automáticamente.

### 2. **Arquitectura 100% Asíncrona (Kotlin 2.0)**
*   **Structured Concurrency:** Todo el framework ha sido migrado a Kotlin 2.0 y Coroutines 1.9.0.
*   **Handlers Non-Blocking:** La interfaz `KHandler` ahora es `suspend fun`, permitiendo escalar masivamente sin bloquear hilos de sistema.
*   **OS-Aware:** Motor optimizado y verificado para un arranque instantáneo tanto en **Windows** como en **Linux**.

### 3. **Octopus Sentinel (Cerebro de Dependencias)**
Hemos integrado un nuevo sistema de vigilancia (`koupper watch`) que detecta qué Service Providers usas en tu proyecto y gestiona las librerías subyacente en tu `build.gradle` de forma autónoma.

---

## 🚀 Guía de Inicio Rápido (Local)

1.  **Sincronizar:** `git pull origin develop` en todos los sub-repositorios.
2.  **Instalar:**
    ```bash
    kotlinc -script install-workspace.kts -- --force
    ```
3.  **Verificar:**
    ```bash
    koupper --version
    ```

## 📜 Estructura del Workspace

*   `/koupper`: El motor (Octopus) y los proveedores oficiales.
*   `/koupper-cli`: Herramientas de línea de comandos.
*   `/examples`: Demostraciones de las nuevas capacidades de Agentes.
*   `/docs`: Roadmaps detallados y decisiones de arquitectura.

---

**Arquitecto a cargo:** Modernización impulsada por la visión de Structured Concurrency y Zero-Boilerplate.
