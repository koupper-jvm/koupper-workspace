# Migration: Unified @Export Annotation Path (v6.5.0)

Date: 2026-05-24
Status: **Mandatory for v6.5.0+**

## Overview

The `@Export` annotation, previously located in `com.koupper.octopus.annotations.Export`, has been moved to a unified shared package to allow its use across all Koupper modules (Engine, CLI, and Shared) without circular dependencies.

## Change Summary

| Old Path | New Path |
|----------|----------|
| `com.koupper.octopus.annotations.Export` | `com.koupper.shared.annotations.Export` |

## Impact

Any existing Kotlin script (`.kts`) or module initialized with previous versions of the Koupper CLI will fail to compile under Octopus 6.5.0+ unless the import statement is updated.

## Action Required

### 1. Manual Update (Recommended for few scripts)

Open your script files and update the import line:

```kotlin
// BEFORE
import com.koupper.octopus.annotations.Export

// AFTER
import com.koupper.shared.annotations.Export
```

### 2. Mass Update (Linux/macOS)

You can use `sed` to update all your scripts at once:

```bash
find . -name "*.kts" -o -name "*.kt" | xargs sed -i 's/com.koupper.octopus.annotations.Export/com.koupper.shared.annotations.Export/g'
```

### 3. Mass Update (Windows PowerShell)

```powershell
Get-ChildItem -Recurse -Include *.kts, *.kt | ForEach-Object { (Get-Content $_.FullName) -replace "com.koupper.octopus.annotations.Export", "com.koupper.shared.annotations.Export" | Set-Content $_.FullName }
```

## Verification

After updating, verify your script with the CLI:

```bash
koupper run my-script.kts
```

If you see a "unresolved reference" error, please double-check that the new import path is correct and that you are using Koupper CLI 4.8.0+.
