# beancounter

Kotlin and Spring Boot mono-repo that turns transactions into portfolio positions and values
them against market data. Services: `svc-data`, `svc-position`, `svc-event`, `svc-agent`,
`svc-admin`. Shared jars: `jar-contracts`, `jar-common`, `jar-auth`, `jar-client`.

This file is the starting point. Read the linked documents when the task needs them.
Ecosystem rules (TDD, cross-repo order, branches) are in
[`../bc-domain/AGENTS.md`](../bc-domain/AGENTS.md).

## Build rules

- **Contract stubs are Gradle artifacts.** `svc-data` and `svc-position` publish a `stubs`
  configuration that consumers take as `testImplementation(project(path = ..., configuration = "stubs"))`.
  A clean checkout builds; nothing is published to `~/.m2`.
- **`svc-data` never depends on `:jar-client`.** That edge brings back the circular build.
  Shared ingest code lives in `jar-common`; `validateDependencies` enforces the rule.
- **Flyway:** `git add` a new migration at once.

## Pre-commit check

```bash
./gradlew testAll
./gradlew formatKotlin
./gradlew lintKotlin
./gradlew detekt   # the ROOT task, never :svc-data:detekt
```

`:svc-data:detekt` matches no task, so Gradle's prefix matching runs `detektNullSafety` and
the real rules never run. `./gradlew check` runs all of the above.

## Where to read next

| When you…                                                    | Read |
|--------------------------------------------------------------|------|
| change module dependencies, messaging, topics, Flyway or enums, or set up local databases | [BEANCOUNTER.md](../bc-domain/BEANCOUNTER.md) |
| need ports, the service map or message flows across repos     | [ARCHITECTURE.md](../bc-domain/ARCHITECTURE.md) |
| build or change a service (naming, ownership, TDD)            | [SERVICE_DESIGN.md](../bc-domain/SERVICE_DESIGN.md) |
| touch transactions or cash settlement                         | [TRN.md](../bc-domain/TRN.md), [SETTLEMENT.md](../bc-domain/SETTLEMENT.md) |
| touch corporate events                                        | [DOMAIN.md](../bc-domain/DOMAIN.md) |
| add or change a market data provider                          | [PROVIDER.md](../bc-domain/PROVIDER.md), [SPLITS.md](SPLITS.md) |
| touch multi-currency code                                     | [FX.md](../bc-domain/FX.md) |
| touch auth or tokens                                          | [AUTH0.md](../bc-domain/AUTH0.md) |
| run services locally                                          | [FANTAIL.md](../bc-domain/FANTAIL.md) |
| work on one module                                            | that module's `README.md` |
