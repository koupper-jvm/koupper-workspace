# Agent Setup

Use these commands to bootstrap and validate agent workflow files in this repository.

## Initialize standard files

```bash
koupper run scripts/agent/init.kts '{"force":false,"profile":"standard","projectName":"Koupper","baseBranch":"develop"}'
```

## Validate setup

```bash
koupper run scripts/agent/validate.kts '{}'
```

## Session preflight

```bash
koupper run scripts/agent/preflight.kts '{}'
```

## Re-generate with overwrite

```bash
koupper run scripts/agent/init.kts '{"force":true,"profile":"standard","projectName":"Koupper","baseBranch":"develop"}'
```

## Port this to another project

Copy these files into the target repository:

- `scripts/agent/init.kts`
- `scripts/agent/validate.kts`
- `scripts/agent/preflight.kts`

Then run:

```bash
koupper run scripts/agent/init.kts '{"force":false,"profile":"standard","projectName":"<TargetProject>","baseBranch":"develop"}'
```
