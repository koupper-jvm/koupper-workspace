# Phase 9: Swarm Orchestration - Debugging Guide

## Current Status
The architecture for **Phase 9 (Multi-Agent Handoff via Native Jobs)** is fully implemented in the `develop` branch. This includes the `SwarmCoordinator`, `AgentOrchestrator` integration, and the native job queuing logic (`::func.asJob().dispatchToQueue()`).

## The Blocker
We are currently unable to run the verification script `examples/agents/test_swarm_jobs.kts` using the `koupper run` command due to a low-level conflict in the Kotlin Scripting Host.

### 1. The Error
When running the script, the embedded Kotlin K2 compiler throws:
```
java.lang.RuntimeException: java.lang.IllegalArgumentException: source must not be null
...
Symbol is declared in module 'jakarta.annotation' which does not export package 'com.koupper.shared.annotations'
```

### 2. Root Cause: FatJar Module Poisoning
The `octopus.jar` is a "FatJar" that bundles all dependencies. Some modern dependencies (Jakarta, Jersey, Grizzly) include `module-info.class` files (Java 9 Modules). When these are merged into the FatJar, the Kotlin K2 compiler gets confused during static analysis of the script, leading to the "source must not be null" crash.

## Mission: Fix the Build Pipeline
The goal is to fix the `octopus` JAR generation so it doesn't trigger module collisions in K2.

### Recommended Steps for Claude/Next Agent:
1.  **Do NOT downgrade to Kotlin 1.9.** Stay on K2.
2.  **Clean the FatJar:** Modify `koupper/octopus/build.gradle`.
    *   Implement the **ShadowJar** plugin if possible.
    *   Explicitly exclude all module descriptors from the final JAR:
        ```gradle
        exclude 'module-info.class'
        exclude 'META-INF/versions/*/module-info.class'
        exclude 'META-INF/services/javax.annotation.processing.Processor'
        ```
3.  **Validate `ScriptingHostBackend.kt`**: Ensure it correctly handles the compilation context. If necessary, use the "Physical File Wrapper" approach (writing the script to `/tmp/` before compiling) combined with a clean classpath.
4.  **Verification Run**:
    ```bash
    # Rebuild
    cd koupper && ./gradlew clean :octopus:assemble
    # Deploy
    cp octopus/build/libs/octopus-x.x.x.jar ~/.koupper/libs/octopus.jar
    # Run Swarm Test
    koupper run examples/agents/test_swarm_jobs.kts
    ```

## Success Criteria
1.  Agent A starts and generates a `ProgrammingLanguages` DTO.
2.  Koupper logs: `[JOBS] Serializing task generateLanguages...`
3.  Agent B wakes up, receives the DTO, and gives the "Final Verdict".

---
*Last updated: 2026-05-27 by Gemini CLI*
