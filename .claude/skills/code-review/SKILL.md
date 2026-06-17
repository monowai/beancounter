---
name: code-review
description: Review uncommitted/branch diff for security, correctness, clean-code (incl. DRY), and project guardrails. Optimised for low-noise high-signal feedback. Trigger on /code-review or when the user asks for a review, security audit, or PR feedback. Skip stylistic issues already enforced by lint/prettier/ktlint.
---

You are reviewing a code diff for the Beancounter ecosystem. Output ONLY actionable issues. No praise, no summaries of what changed, no speculative refactors.

## How to gather the diff

In priority order:

1. If the user gave a PR number or URL → `gh pr diff <num>`
2. If `--base <branch>` was specified → `git diff <base>...HEAD`
3. Default → `git diff main...HEAD` plus `git diff` (for uncommitted)

Stop if the diff is empty.

## Optional static-analysis layer

Before reasoning over the diff, run Codacy if the MCP tool is available **and** the diff contains files Codacy can analyse:

- Use `mcp__codacy__codacy_cli_analyze` with the changed file paths (relative to repo root). Skip if it fails — do not block the review.
- Pull findings into the review only when severity ≥ **Warning** AND the finding sits on a line the diff actually touches. Drop everything else; Codacy is otherwise too noisy.
- If Codacy is unavailable or times out, note it in one line at the end (`Codacy: skipped (reason)`) and continue.

Do NOT run `.codacy/cli.sh analyze` directly — first-run installs are slow (semgrep/trivy DB) and break the review loop. Use the MCP tool which reuses the cached install.

## Severity rubric

- 🔴 **Critical**: security vulnerability, data loss, auth bypass, secret leak, broken access control, OWASP top-10, hard-coded credential, dangerous SQL/cmd construction
- 🟠 **Bug**: logic error, race, leak, swallowed error, off-by-one, wrong equality, async misuse
- 🟡 **Cleanup**: clean-code violation likely to bite later — DRY violation (3+ near-identical blocks), large function (>~30 lines, multi-purpose), premature abstraction, dead code, name lying about behaviour on a public API
- ⚪ **Nit**: stylistic, only if not already auto-fixable by lint/prettier/ktlint

Cap at **10 comments**. If more issues exist, report the worst 10 and add a one-line tally (`8 additional nits omitted`).

## Required for each comment

```
{severity} `path/to/file.kt:42`
**Issue**: <one sentence>
**Why**: <impact — security risk, specific bug scenario, future maintenance cost — be concrete>
**Fix**:
\`\`\`<lang>
<minimal patch>
\`\`\`
```

## What to check

### Security (highest priority — never skip)

- Injection: SQL, command, path traversal, SSRF, LDAP, header injection
- Authn/authz: missing JWT validation, missing scope check on state-changing endpoints, role bypass, IDOR
- Secrets: hard-coded keys/tokens/passwords, secrets in logs or error messages, secrets in URL query strings
- CSRF on state-changing endpoints that aren't bearer-token-only
- Input validation at trust boundaries only (don't add it inside trusted internal code)
- Deserialization of untrusted data, especially Java/Jackson polymorphic types
- Broken multi-tenant isolation: BC repos must store `SystemUser.id` as the owner, NEVER `jwt.subject` directly (see `SERVICE_DESIGN.md` data-ownership rule)
- Unbounded/expensive operations without rate limit (LLM calls, exports, recursive joins)
- Open redirect, file upload without type/size/path-traversal guard
- Dependency vulnerability if a version is pinned in this diff (note + suggest upgrade)

### Correctness

- Off-by-one, missing `await`/fire-and-forget on a critical path
- Empty `catch {}` swallowing errors, lost stack traces
- Resource leak (stream / connection / file descriptor not closed; missing `use {}` / `try-with-resources`)
- Wrong equality semantics: `==` vs `equals` in Kotlin, `==` vs `===` in TS, list-by-reference, float equality
- Timezone assumed local, `Instant` vs `LocalDateTime` mix-ups
- Mutated shared mutable state, race on shared resource
- Premature `return` skipping cleanup
- Optional/nullable unwrapped without guard

### Clean code (incl. DRY)

- **DRY violations**: 3+ near-identical blocks (rule of three — extract on the third occurrence, not the first; flag when 3+ exist and aren't extracted)
- Function does more than one thing or > ~30 lines without a strong reason
- Names lie about behaviour (`getX` that mutates, `isEmpty` that throws, etc.)
- Premature abstraction added to handle one-off variation
- Defensive null/undefined checks inside trusted internal boundaries (BC convention: validate at system boundaries only)
- Comments explaining WHAT instead of WHY; comments referencing the current task/PR/issue (rot)
- **Missing KDoc on non-trivial public surface** — see Kotlin idioms section for the rule and worked examples.
- Magic numbers without named constant
- Deeply nested conditionals (>3 levels)
- Public API surface that should be internal/private

### Tests

- Test mocks the unit under test (mocking the thing you're testing)
- No assertions
- `Thread.sleep` / `setTimeout` -based wait
- Integration scope where unit suffices, or vice versa
- Tests deleted without replacement

### Idioms — Kotlin / Spring (beancounter, svc-retire, svc-rebalance)

Apply these only when the diff touches `*.kt` files. Skip if the file is a generated build artefact.

- **Immutability**: prefer `val` over `var`; flag mutable backing fields on data classes without justification.
- **Null safety**: `!!` non-null assertion in production code is 🟠 unless paired with a comment explaining why null is impossible. Prefer `?.let { }`, `requireNotNull()`, or `checkNotNull()` with a message.
- **`when` exhaustiveness**: when used as an expression on a sealed type or enum, every branch must be covered without `else` — flag bare `else` swallowing future cases.
- **Spring DI**: constructor injection only — no `@Autowired` on fields or setters, no `lateinit var` for beans.
- **`@Bean` + `@Profile`**: profile-qualified beans must inject the concrete subtype (e.g., `AnthropicChatModel`), not the super-type (`ChatModel`) with `@ConditionalOnBean`. User-config conditions evaluate before auto-config and silently skip otherwise (BC trap, see `ChatClientConfiguration.kt` comment).
- **`@Transactional`**: required on service-layer methods that modify the DB through a repository; flag direct repo writes from `@Controller` classes.
- **RabbitMQ message handlers**: must be idempotent — flag handlers that don't tolerate redelivery.
- **DTOs**: prefer `data class` over plain class for value objects with `equals`/`hashCode`/`copy` semantics.
- **Mocks**: prefer `mockito-kotlin` (`whenever(x).thenReturn(...)`) over `Mockito.when` (clashes with Kotlin `when` keyword); prefer `mock<T> { on { ... } doReturn ... }` lambda style.
- **Assertions**: prefer AssertJ `assertThat(x).isEqualTo(y)` over JUnit `assertEquals(y, x)` (argument order trap).
- **Coroutines vs Reactor**: don't mix unless explicitly bridged — flag `runBlocking` inside reactive chains, flag `.block()` outside tests.
- **Kotlin Spring AI options builder**: per-call options REPLACE (don't merge with) the ChatClient's defaults — re-apply `cacheOptions` etc. on every per-call override (BC incident: silent loss of prompt caching).
- **Logging**: parameterised `log.debug("x={}, y={}", x, y)` — never `log.debug("x=$x")` (string concatenation runs even when DEBUG is off).
- **KDoc on public surface (insist)**: new or modified **public** classes, interfaces, top-level functions, public/internal methods, and `@Bean` factories MUST carry KDoc that explains the *contract* — what it guarantees, what callers must not assume, non-obvious failure modes, why the design choice. Apply the rule to the diff: if the function is touched and is missing KDoc, flag it.
  - **Skip** for: `private` helpers, simple `data class` fields (class-level KDoc is enough), trivial accessors that restate the name (`getX(): X`), `override` functions whose contract is unchanged from the parent (link to parent doc instead), test functions (test name *is* the doc).
  - **Quality bar**: KDoc that just restates the function name in prose ("Fetches the price response for a single asset" on `getPriceResponse`) does NOT satisfy this rule — flag as if missing. KDoc must add information beyond what the signature already conveys.
  - **Format**: KDoc indent matches the function indent (4-space block, `*` lines aligned). Auto-generated docs that misalign (column 0 or 12-space drift) are 🟠 — they break `lintKotlin` and pollute diffs.
  - **Severity**: 🟡 Cleanup when missing on non-trivial public API; ⚪ Nit when added but signal-free. 🟠 only when the missing doc hides a non-obvious failure mode that has bitten the project before (e.g. an `@Async` method's proxy-bypass-on-self-invocation trap).
  - Reference good examples in-repo: `PriceBackfillCoordinator.scheduleBackfill`, `CacheInvalidationProducer.sendPriceHistoryEvent`, `EnsureHistoryRequest` (class-level).
- **Spring caching — `@Cacheable` traps** (BC has caused two outages here):
  - **Cache must be registered with the active `CacheManager` bean.** `svc-data` defines a programmatic `SimpleCacheManager` in `CacheConfig.kt`, which **overrides** Spring Boot's `spring.cache.cache-names` autoconfig. Adding the name to `application.yml` is dead code. Every new `@Cacheable("foo.bar")` must add `foo.bar` to `CacheConfig.cacheManager()` — flag any `@Cacheable` whose cache name doesn't appear in the bean. Failure mode is `IllegalArgumentException: Cannot find cache named '…'` on first call.
  - **SpEL key collisions.** Concatenating string components with a delimiter that can appear in the values produces silent cache poisoning. `key = "#a + '-' + #b"` collides when either `#a` or `#b` legitimately contains `-` (e.g. ticker `BRK-B`). Flag any concat-with-dash key — recommend `|` with `~` sentinel for nulls, or `T(java.util.Objects).hash(...)`.
  - **Self-invocation bypasses AOP.** `@Cacheable` on a method called from inside the same class doesn't intercept (proxy bypass). Flag any in-class call to a `@Cacheable` method that expects caching to fire.
  - **Empty results sticky.** `@Cacheable` without `unless = "#result.isEmpty()"` caches empty maps/lists forever — a transient provider failure becomes permanent no-coverage. Flag when the cached return is a collection type whose emptiness implies upstream failure.
- **Spring `@ConfigurationProperties` wiring**:
  - The class needs `@Component`, or registration via `@EnableConfigurationProperties(X::class)` on a `@Configuration`, or `@ConfigurationPropertiesScan` on the boot class. Flag a `@ConfigurationProperties` class that's only `data class` with no path to bean registration — it'll inject as defaults silently.
  - **YAML binder structural rule.** Spring's binder fails (`Could not bind properties to '…'`) when a `@ConfigurationProperties` prefix contains a key that's both a scalar AND a map at sibling positions, e.g. `beancounter.market.providers.news: alpha` next to `beancounter.market.providers.alpha: { … }`. Flag any new top-level scalar under a prefix that already binds nested maps.
- **Spring `@Value` and Kotlin 2.x string templates**:
  - `@Value($$"${prop:default}")` (double-dollar prefix) is **valid Kotlin 2.x multi-dollar template syntax** — escapes the `$` so Spring sees `${prop:default}` literally. Do NOT flag as a syntax error; it's the BC convention for property placeholders to avoid Kotlin interpreting the `$` as string interpolation. Confirm with `MarketStackConfig.kt:26`.
  - Defaults in `@Value("\${prop:fallback}")` MUST match the corresponding `application.yml` value — silent drift otherwise. When a diff changes one, flag if the other isn't touched.
- **URI template hygiene** (Spring `RestClient.uri(template, vars)`):
  - String-interpolating values into the template (`"/api?x=$value"`) bypasses Spring's URI encoding and is unsafe even for internal callers if the value can ever contain reserved chars. Flag any `uri("…${var}…")` in a gateway/client — prefer `uri("…{var}…", value)` with the placeholder pattern.
- **Upstream-failure backoff (quota amplification)**:
  - When an external API call fails and a cache/cooldown row is only updated on success, every subsequent request re-hits the upstream — turning a transient outage or a 429 into a retry storm that burns quota and amplifies the upstream's load. Flag any provider integration where the cache TTL / cooldown record advances **only** on the success path. Recommend bumping the `last_fetched_at` (or equivalent) in the `catch` block so failures respect the same backoff window. Real BC incident: EODHD news service in PR #845 — caller would have retry-stormed on every 429 until CodeRabbit caught it.
- **Read-then-save upsert race**:
  - Code that does `findByX` → conditionally `new()` → `save()` can race when two concurrent transactions both miss the read and both try to insert the same unique key. Postgres throws on the second insert; Spring surfaces it as `DataIntegrityViolationException`. Flag any read-then-save against a `@Column(unique=true)` / `@UniqueConstraint` field that doesn't wrap the `save` in a `try`/`catch` that re-reads the winning row and merges. JPA has no native upsert — the catch-and-retry pattern is the canonical fix without dropping to dialect-specific SQL. Severity 🟠 because the symptom is sporadic 500s, not deterministic, so it slips through tests easily.
- **`@ApiResponses` must list every status the method can produce**:
  - When a controller method introduces a new exception path that maps to a non-2xx HTTP status (e.g. `throw ForbiddenException(...)` → 403, `throw NotFoundException(...)` → 404), the method's `@ApiResponses` block must include an `ApiResponse(responseCode = "<status>", description = "...")` entry for it. Flag any controller change that adds a new status-mapped exception (or adds a parameter that triggers one) without updating `@ApiResponses`. Severity 🟡 — pure-doc bug; breaks the generated OpenAPI contract / client codegen but doesn't affect runtime. Real BC incident: PR #904 added a `systemUserId` query param with a 403 path; CodeRabbit caught the missing `403` entries on both `/assets/config` and `/portfolios`.

### Idioms — TypeScript / Next.js (bc-view)

Apply only when the diff touches `*.ts` / `*.tsx`.

- **Pages Router**: bc-view uses Pages Router (`src/pages/`), NOT App Router. Flag any `app/` directory imports, `'use client'` directives, or `next/navigation` imports — should be `next/router`.
- **Auth0 v4**: `auth0.getSession(req)` + `auth0.getAccessToken(req, res)` — flag legacy `withApiAuthRequired` (removed in v4). `getAccessToken` returns `{ token }` (was `{ accessToken }` in v3). Auth routes live at `/auth/*` (NOT `/api/auth/*`). `User` type imports from `@auth0/nextjs-auth0/types` — flag imports from `/client`.
- **API routes**: use `createApiHandler` (`src/pages/api/...`) for the centralised path — flag bare `export default function handler(...)` that re-implements auth/error handling already in the wrapper.
- **`any` usage**: `any` in non-test code is 🟡 — push for `unknown` + narrowing. Casts via `as unknown as T` are 🟠.
- **Hooks**: `useEffect` cleanup function required for subscriptions / timers / event listeners. `useCallback` / `useMemo` only with a concrete reason (referenced in dep array of a child memo, expensive compute) — flag premature memoization.
- **State updates in tests**: every state update inside `act(async () => { ... })`; flag bare `fireEvent` followed by an assertion that depends on the post-update state without an `await`.
- **SSR safety**: flag `window`, `document`, `localStorage` access in component render path without a `typeof window !== 'undefined'` guard or `useEffect` wrapper.
- **Tailwind**: prefer utilities over inline `style={{}}` when a utility exists; mobile-first breakpoint defaults (`md:`, `lg:` add at wider sizes); `mobile-portrait:` is a custom variant defined in `tailwind.config.js` (only fires at max-width 639px + portrait).
- **Routing imports**: `useRouter` from `next/router` (Pages Router), not `next/navigation`.
- **Path aliases — bc-view only** (cross-repo rule; the configs cited live in the `bc-view` repo, not in this one — verify against `../bc-view/tsconfig.json` and `../bc-view/scripts/check-imports.js` before flagging). Per bc-view's `tsconfig.json` `paths`, both **`@lib/*` and `@utils/*` resolve to the same target `src/lib/utils/*`** (two aliases, one destination). Other roots: `@components/*` → `src/components/*`, `@hooks/*` → `src/hooks/*`, `@contexts/*`, `@providers/*`, `@constants/*`, `@test-fixtures/*` → `src/test-fixtures/*`, `@styles/*` → `styles/*`, `@types/*` → `types/*`.

  Rules for NEW bc-view code in a diff:
  1. Flag any relative import (`./X`, `../X`, `../../X`) that resolves under one of these alias roots — including same-directory siblings and `__tests__/../X`. Treat as 🟡 (consistency). Don't flag pre-existing relative imports outside the diff (CI churn).
  2. **`@lib/api/*` is BANNED — 🔴**. Even though tsconfig resolves it identically to `@utils/api/*`, Next.js's bundler uses an independent resolver and FAILS on `@lib/api/*` at runtime (returns 500). bc-view enforces this via `scripts/check-imports.js` (pre-commit hook). The canonical form for anything under `src/lib/utils/api/` is `@utils/api/...` (e.g., `@utils/api/fetchHelper`). The tsconfig-vs-runtime split is the trap.
  3. For other `src/lib/utils/<subdir>/...` targets (`assets`, `independence`, `broker`, …), either alias works. Codebase mixes both; prefer `@lib/<subdir>/...` in new code for consistency with newer files.
  4. **The literal form `@lib/utils/foo` is wrong — 🟠**. Because `@lib/*` already points inside `src/lib/utils/`, writing `@lib/utils/foo` resolves to `src/lib/utils/utils/foo` (non-existent). This is distinct from `@lib/api/foo`, which DOES resolve at compile time but breaks at runtime per rule 2.

  CodeRabbit recurringly suggests `@lib/api/...` and `@lib/utils/api/...` — reject both.
- **`types/beancounter` import path**: domain types live in `types/beancounter.d.ts` and must be imported as `"types/beancounter"` (bare, via `baseUrl`). Do NOT flag/suggest `@types/beancounter` — TS6137 reserves the `@types/*` prefix for DefinitelyTyped packages, and the alias is dead for `.d.ts` files. CodeRabbit gets this wrong; reject the suggestion if it appears.
- **Payload types belong in `types/beancounter.d.ts`**: callback payload interfaces (`XxxData` carrying domain shapes like `Asset` / portfolio) live alongside `QuickSellData`, `WeightClickData`, `SetCashBalanceData`, etc. Flag any new `XxxData` declared inside a component file (`Rows.tsx`, `CardView.tsx`, …) and re-exported — even if Rows is the only emitter today. PR #693 took this hit for `PriceChartData` + `PortfolioBreakdownData`. Treat as 🟡. Same rule: add a `makeXxx` fixture in `src/test-fixtures/beancounter.ts` so tests don't hand-roll the literal (also a recurring CodeRabbit nitpick).
- **React imports**: with React 17+ JSX transform, `import React from "react"` is unused noise unless the file references `React.X` directly — but the codebase still includes it; ESLint will flag dead imports, so don't double-flag.
- **Jest mocks**: Auth0 mocks live in `__mocks__/@auth0/nextjs-auth0/` (per CLAUDE.md memory) — flag inline `jest.mock("@auth0/nextjs-auth0", ...)` that duplicates the global mock.

### Project guardrails (apply when the diff matches)

- `**/db/migration/V*.sql` added → ensure entity changes in same diff are consistent; the migration must be staged with the entity change (BC incident: V16 dropped during commit-amend, bc-retire CrashLoopBackOff). Flag if entity diff exists without paired migration or vice versa.
- Kotlin entity files in `svc-retire`/`svc-rebalance` ownership columns → must store `SystemUser.id`, not `jwt.subject`. Existing tech debt — new code must be compliant.
- RabbitMQ topic strings → `application.yml` defaults to `-dev`; `-demo` overrides only in `bc-deploy/env/kauri.yaml`. Flag any cross-talk.
- Auth0 JWT scope check → required on every state-changing controller method.
- `--no-verify`, `--no-gpg-sign`, force-push to main, `git reset --hard` in scripts → 🔴 always.
- New `@Bean` with `@Profile` conditions → check Spring's user-config evaluates BEFORE auto-config (memory: ChatClientConfiguration silent skip trap); profile-qualified beans + concrete subtype injection avoids it.
- Spring AI / Anthropic / DeepSeek tool-loop changes → flag if `reasoning_content` handling missing on follow-up turns (memory: DeepSeek 400 on multi-turn).

## Severity calibration — examples to anchor judgement

The four severity tiers below have caused inconsistent grading in the past
(e.g. SQL injection downgraded to 🟡, secret-in-log marked ⚪). Use these
worked examples to calibrate before grading anything close to the boundary.
Each is drawn from the project's regression suite (see "Regression suite"
below). When a finding in front of you closely matches one of these patterns,
its severity should not be lower than shown.

**🔴 Critical**
- SQL/command injection via string concatenation into a query / shell call.
- Bearer token / API key / password written to a log statement (any level).
- New state-changing endpoint (`@PostMapping` / `@DeleteMapping` / `@PutMapping`)
  without a JWT scope check while sibling endpoints in the same controller
  carry one.
- Entity / `@Column` change shipped without a paired Flyway migration in
  `**/db/migration/V*.sql`.

**🟠 Bug**
- `catch (e: Exception) { }` (or with only a comment) that drops the stack
  trace and never logs / re-throws.
- Shared mutable state (`mutableSetOf`, `mutableMapOf`, `var counter`) accessed
  from a `@RabbitListener` / `@Async` / `@Scheduled` handler without a
  thread-safe wrapper.
- A unit test that mocks the unit under test (`mock<X>()` then asserts on the
  stub's return) — exercises nothing real.

**🟡 Cleanup**
- Three or more near-identical blocks (rule of three) without extraction —
  e.g. controller endpoints repeating scope-check + timer + log scaffolding.
- 5+ levels of `if`/`else` nesting where guard clauses with early returns
  would flatten the path. (3-4 levels alone — only flag if the happy path
  is genuinely buried.)
- Method named `get…` / `find…` / `is…` that mutates state (cache rewrite,
  side-effect timestamp, lazy-init that races).
- Missing KDoc on a non-trivial public method / class / `@Bean` /
  `@PostMapping` / `@RabbitListener` in the diff. The contract surface is
  exactly where readers reach for the docstring; absence is a real defect.

**⚪ Nit**
- Whitespace, missing trailing newline, formatter-fixable issues — only if
  not already enforced by lint/prettier/ktlint (CI catches those, don't
  duplicate).

## What to SKIP

- Anything caught by lint / prettier / ktlint / typecheck (CI runs these — do not duplicate)
- "Consider extracting", "perhaps", "you could" — speculative refactors with no concrete bug or readability cost
- Per-file summaries
- Praise
- Re-stating what the diff does
- Cosmetic comments on tests that exercise the contract correctly

## Output shape

Comments in severity order (🔴 first). End with a single-line verdict:

- `READY` — no 🔴 / 🟠 issues
- `BLOCKED` — at least one 🔴 / 🟠 issue
- `Codacy: <skipped|N findings>` — one line if Codacy ran or was skipped

Nothing else.

## Wrapping up: worktrees, PRs, and cleanup

A review is usually the **end of a work session**, not a standalone act. Once a
diff reviews clean the same session normally has to land it — open the PR and
tidy up. These are the BC ways of working, learned from multi-session
collisions. Run them only when wrapping up a change or when asked; a bare
`/code-review` still just reviews.

### Isolate work in a worktree / fresh branch

- **Multi-session hazard**: edits land on whatever branch happens to be checked
  out in the shared working tree, contaminating an unrelated in-progress
  branch. (Real incidents: wealth-view edits committed onto
  `fix/aggregate-holdings-reporting-currency`; IRR edits onto
  `chore/spring-ai-1.1.8` — both unrelated to the work.)
- **Rule**: any non-trivial change gets its own branch off `origin/main`. Agents
  that write files MUST use `isolation: "worktree"` (parallel **or** single) —
  the shared tree collides with in-progress work on other branches.
- **Worktree convention**: a sibling directory of the repo — `<repo>-wt-<topic>`
  (e.g. `beancounter-wt-cpf-ccy`, `svc-retire-wt-cpf`); bc-view also uses the
  shorter `<repo>-<topic>` (`bc-view-pension`). One branch per worktree. List
  with `git worktree list`.
- **If changes already landed on an unrelated branch**: don't commit them there.
  Branch off `origin/main`, then move only your files — first confirm they don't
  overlap the branch's own committed work with
  `git diff origin/main...HEAD --name-only`. Stage your files explicitly; leave
  foreign uncommitted files (work you didn't create) untouched, and restore the
  original branch when done.

### Open the PR (don't wait to be asked)

- After a clean review (`READY`), open the PR **in the same session** — don't
  leave the branch dangling for the user to chase.
- **App repos** (`beancounter`, `bc-view`, `svc-retire`, `svc-rebalance`): NEVER
  push to `main` — feature branch + PR only. Exceptions that commit straight to
  main: `bc-deploy` (rollout is the review gate) and `bc-claude` (docs-only).
- **Pre-push**: run this local review on the staged diff and fix 🔴/🟠 first —
  CodeRabbit auto-runs on every push and burns the user's 5/h quota.
- **Verify the branch before pushing** — after a merge you can land back on
  `main`.
- **PR + commit hygiene**: Conventional-Commits subject only (no body essays),
  no `Co-Authored-By` trailer, no PII (names / emails) in commit, PR body, or
  code.
- **`@coderabbitai ignore`** (on its own line in the PR body) ONLY when a local
  review actually ran. Manual eyeballing ≠ a local review — if none ran, omit it
  so CodeRabbit does the second pass.

### Clean up stale branches and worktrees

- Worktrees accumulate fast — each pins a branch and a full working copy. They
  go stale the moment their PR merges.
- **After a PR merges**: delete the branch and remove its worktree.
  - `git worktree remove <path>` (`--force` if it holds throwaway changes), then
    `git worktree prune`.
  - The `commit-commands:clean_gone` skill sweeps branches the remote has
    deleted (`[gone]`) **plus** their associated worktrees in one pass — prefer
    it for a bulk tidy.
- **Never** remove a worktree (or `--force` past) uncommitted work you didn't
  create — surface it to the user instead.

## Regression suite

A 10-case golden dataset lives in its own private repo at
`https://github.com/monowai/code-review-eval` — kept separate from any
deployable service repo because the synthetic vulnerable diffs would
otherwise trip security scanners and alarm anyone reading `git log`. Each
case exercises one anti-pattern this skill is required to catch at a
specific severity:

| # | Anti-pattern | Required severity |
|---|---|---|
| 01 | SQL injection via string concatenation | 🔴 |
| 02 | Missing `@PreAuthorize` on DELETE endpoint | 🔴 |
| 03 | Silent `catch (e: Exception)` swallowing errors | 🟠 |
| 04 | Race on shared mutable state in `@RabbitListener` | 🟠 |
| 05 | Entity column added without paired Flyway migration | 🔴 |
| 06 | DRY violation across 3 admin endpoints | 🟡 |
| 07 | Test mocks the unit under test | 🟠 |
| 08 | Bearer token written to logs | 🔴 |
| 09 | 5-level deep `if`/`else` pyramid | 🟡 |
| 10 | `get(…)` method hides state mutation | 🟡 (or 🟠) |
| 11 | `@Cacheable("foo.bar")` whose cache name is missing from `CacheConfig.cacheManager()` | 🟠 |
| 12 | `@Cacheable` key SpEL `"#a + '-' + #b"` where either component can contain `-` | 🟠 |
| 13 | Scalar property added under a `@ConfigurationProperties` prefix that also has map siblings (YAML binder break) | 🟠 |
| 14 | Kotlin 2.x `@Value($$"${prop:default}")` flagged as "invalid syntax" (false-positive regression) | NOT a finding — must NOT be flagged |
| 15 | `RestClient.uri("/api?from=$var")` string-interpolating into the URI template | 🟡 |
| 16 | Provider `catch` block doesn't bump cache/cooldown timestamp → next request retry-storms the upstream | 🟠 |
| 17 | Read-then-save against a unique-constrained column without `DataIntegrityViolationException` retry-as-merge | 🟠 |

After **any** edit to this `SKILL.md`, re-run the suite to confirm recall
hasn't regressed:

```bash
gh repo clone monowai/code-review-eval ~/code-review-eval  # first time only
cd ~/code-review-eval
# 1. For each cases/<NN-name>/diff.patch, run this skill and save the output
#    as cases/<NN-name>/actual.md
# 2. Score:
./run-eval.sh
```

Healthy bar: **≥ 9/10 PASS**. Below 8/10 = prompt regression; investigate
before merging the SKILL.md edit. New incidents that the skill misses in the
wild → add a corresponding case under `cases/NN-…/` (the suite is the
source of truth for what we expect this skill to catch).
