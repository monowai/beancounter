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
- **Path aliases**: `@lib/*` resolves to `src/lib/utils/*`, `@components/*` to `src/components/*`, `@hooks/*` to `src/hooks/*` (per `tsconfig.json` paths) — flag relative `../../../` imports that an alias would shorten.
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

## Regression suite

A 10-case golden dataset lives **outside any deployable repo** at
`bc-claude/code-review-eval/` (synthetic vulnerable diffs must not be
committed to a service repo where they'd trip security scanners). Each case
exercises one anti-pattern this skill is required to catch at a specific
severity:

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

After **any** edit to this `SKILL.md`, re-run the suite to confirm recall
hasn't regressed:

```bash
cd ~/IdeaProjects/monowai/bc-claude/code-review-eval
# 1. For each cases/<NN-name>/diff.patch, run this skill and save the output
#    as cases/<NN-name>/actual.md
# 2. Score:
./run-eval.sh
```

Healthy bar: **≥ 9/10 PASS**. Below 8/10 = prompt regression; investigate
before merging the SKILL.md edit. New incidents that the skill misses in the
wild → add a corresponding case under `cases/NN-…/` (the suite is the
source of truth for what we expect this skill to catch).
