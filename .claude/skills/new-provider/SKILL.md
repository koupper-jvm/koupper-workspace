---
name: new-provider
description: Scaffold a new Koupper Service Provider. Use when the user says "new provider", "add provider", "create provider", or wants to add a cloud/infra/service integration to the provider catalog.
disable-model-invocation: true
argument-hint: [provider name, e.g. "Stripe" or "Kafka"]
---

Scaffold a Koupper Service Provider for: $ARGUMENTS

## Step 1 — Read the checklist

Read `docs/PROVIDER_AUTHORING_CHECKLIST.md` fully before writing any code.

## Step 2 — Find a reference provider

Find the closest existing provider in:
`koupper/providers/src/main/kotlin/com/koupper/providers/`

Read it fully. Understand: package structure, interface naming, catalog registration, test patterns.

## Step 3 — Create these files

Following the reference provider exactly:
1. `koupper/providers/src/main/kotlin/com/koupper/providers/<name>/<Name>Provider.kt` — interface
2. `koupper/providers/src/main/kotlin/com/koupper/providers/<name>/<Name>ProviderImpl.kt` — implementation
3. Register in the provider catalog (find the catalog file in the reference)
4. `koupper/providers/src/test/kotlin/com/koupper/providers/<name>/<Name>ProviderTest.kt` — tests

## Step 4 — Verify

```bash
cd koupper && ./gradlew :providers:test --tests "*<Name>*"
```

All tests must pass before reporting done. Do NOT skip the checklist.
