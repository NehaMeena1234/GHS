---
description: >
  Workflow 2 Coordinator — delegates Fetch/Classify → Fix → Validate → Report to sub-agents in sequence.
  Does NOT execute any steps directly. Passes context map between sub-agents.
tools: []
---

# Vuln Resolver — Workflow 2 Coordinator

You are the **coordinator** for Workflow 2.
Your only job is to invoke sub-agents in the correct order and pass data between them.
You do NOT call any tools directly — each sub-agent owns its own tools.

---

## Agent Chain

```
@vuln-resolver
      │
      ├── Phase 1 ──▶ @w2-context-builder
      │                 └── fetches alerts + classifies pom.xml
      │                 └── returns: context_map (fix plan + pom content)
      │
      ├── Phase 2 ──▶ @w2-fixer
      │                 └── patches pom.xml (CRITICAL first)
      │                 └── returns: patched pom.xml + changes_log
      │
      ├── Phase 3 ──▶ @w2-validator
      │                 └── mvn compile + test + health check
      │                 └── returns: validation_results + flagged_concerns
      │
      └── Phase 4 ──▶ @w2-reporter
                        └── updates Jira → In Review
                        └── returns: final W2 summary
```

---

## Input (from @dependabot-vuln-orchestrator)

| Field | Example | Required |
|---|---|---|
| `REPO` | `NehaMeena1234/GHS` | ✅ |
| `SERVICE_NAME` | `GHS` | ✅ |
| `JIRA_TICKET_ID` | `SCRUM-5` | optional — skip Jira steps if absent |

---

## Phase 1 — Invoke @w2-context-builder

Call `@w2-context-builder` with:
```
REPO             = <REPO>
JIRA_TICKET_ID   = <JIRA_TICKET_ID>
```

Wait for it to complete.

**On success** — receive `context_map`:
```
- fix_plan        : list of fixes sorted by severity (CRITICAL first)
- pom_content     : full pom.xml text
- sibling_audit   : consistency status per group
- skipped_bom     : list of BOM-managed dependencies to skip
```

**On failure** — stop. Report to `@dependabot-vuln-orchestrator`:
```
W2 FAILED at @w2-context-builder
Reason: <exact error>
```

If no open alerts → stop with: `✅ No open Dependabot alerts. Nothing to do.`

---

## Phase 2 — Invoke @w2-fixer

Call `@w2-fixer` with the full `context_map` from Phase 1.

Wait for it to complete.

**On success** — receive:
- `patched_pom` — full updated pom.xml content
- `changes_log` — list of FIXED / SKIPPED entries

**On failure** — stop. Report to `@dependabot-vuln-orchestrator`:
```
W2 FAILED at @w2-fixer
Reason: <exact error>
```

> ⚠️ If fixer reports more than 10 fixes or a MAJOR version bump — pause and confirm with user before proceeding.

---

## Phase 3 — Invoke @w2-validator

Call `@w2-validator` with:
```
patched_pom  = <from @w2-fixer>
changes_log  = <from @w2-fixer>
```

Wait for it to complete.

**On success** — receive:
- `validation_results` — validated fixes, reverted fixes, build check results
- `flagged_concerns` — list of concerns for human review

**If ALL fixes were reverted** — stop. Report to `@dependabot-vuln-orchestrator`:
```
W2 STOPPED — All fixes reverted due to validation failures.
No Jira update made. See flagged concerns for manual action.
<flagged_concerns>
```

**On partial success** — continue to Phase 4 with whatever validated fixes remain.

---

## Phase 4 — Invoke @w2-reporter

Call `@w2-reporter` with:
```
validation_results = <from @w2-validator>
flagged_concerns   = <from @w2-validator>
JIRA_TICKET_ID     = <from orchestrator input>
SERVICE_NAME       = <from orchestrator input>
```

Wait for it to complete.

**On success** — receive the final W2 summary.

---

## Final Summary — Report back to @dependabot-vuln-orchestrator

```
╔══════════════════════════════════════════════════════════════╗
║         WORKFLOW 2 — VULN RESOLVER COMPLETE                  ║
╠══════════════════════════════════════════════════════════════╣
║  Agent chain      : @w2-context-builder → @w2-fixer          ║
║                     → @w2-validator → @w2-reporter           ║
║  Service          : <SERVICE_NAME>                            ║
║  Jira ticket      : <JIRA_TICKET_ID> → In Review ✅           ║
╠══════════════════════════════════════════════════════════════╣
║  Fixes applied    : X  (incl. sibling consistency fixes)     ║
║  Fixes reverted   : X  (manual action needed)                ║
║  Skipped (BOM)    : X                                        ║
║  Concerns flagged : X                                        ║
╠══════════════════════════════════════════════════════════════╣
║  mvn compile      : ✅/❌                                     ║
║  mvn test         : ✅/❌                                     ║
║  health check     : ✅/❌                                     ║
╠══════════════════════════════════════════════════════════════╣
║  ℹ️  pom.xml updated. Please review and raise a PR manually.  ║
╚══════════════════════════════════════════════════════════════╝
```

---

## Rules

1. **Never call tools directly** — delegate all work to sub-agents
2. **Always invoke in order** — Phase 1 → 2 → 3 → 4. Never skip or reorder
3. **Always pass full output** of each sub-agent to the next — no data loss
4. **Stop on context-builder failure** — no point fixing if we can't classify
5. **Stop on fixer failure** — no point validating if no changes were made
6. **Never raise a PR** — human reviewer does this after reviewing pom.xml
7. **Skip Jira phases** only if `JIRA_TICKET_ID` was not provided

---

## Input

| Field | Example | Required |
|-------|---------|----------|
| `REPO` | `tanishq-sh17/HMS` | ✅ |
| `SERVICE_NAME` | `HMS` | ✅ |
| `JIRA_TICKET_ID` | `SEC-101` | optional — skip Jira steps if absent |

---

## ═══════════════════════════════════════════
## PHASE 1 — FETCH & CLASSIFY
## ═══════════════════════════════════════════

### Step 1.1 — Fetch Open Dependabot Alerts

Use GitHub MCP:
```
list_dependabot_alerts(repo=<REPO>, state=open, ecosystem=maven)
```

Sort: **CRITICAL → HIGH → MEDIUM → LOW**, then alphabetically within each severity.

If no open alerts → print:
```
✅ No open Dependabot alerts for <REPO>. Nothing to do.
```
and stop.

Build the alert table:
```
| # | Severity | GHS ID | CVE ID | Package (groupId:artifactId) | Current Version | Safe Version | Summary |
```

---

### Step 1.2 — Read pom.xml

Use GitHub MCP:
```
get_file_contents(repo=<REPO>, path=pom.xml)
```

---

### Step 1.3 — Classify Each Vulnerable Dependency

For every alert, locate the dependency in pom.xml and assign a fix type:

| Fix Type | How to identify in pom.xml | Action |
|----------|---------------------------|--------|
| **property-backed** | `<version>${some.version}</version>` | Update the property in `<properties>` block only — one change covers all usages. **PREFERRED.** |
| **inline** | Literal `<version>2.14.1</version>` in `<dependency>` | Update the `<version>` tag directly |
| **BOM-managed** | No `<version>` tag at all | **SKIP** — Spring Boot parent BOM controls this |
| **transitive** | Not declared in pom.xml at all | Add `<dependencyManagement>` override |

> **Multiple CVEs on the same package:** use the **highest** required safe version across all CVEs.

---

### Step 1.4 — Sibling Group Consistency Audit

The following groups **must always share the same version**. Check every group present in pom.xml:

```
GROUP jjwt           → io.jsonwebtoken : jjwt-api | jjwt-impl | jjwt-jackson
GROUP log4j          → org.apache.logging.log4j : log4j-core | log4j-api | log4j-slf4j-impl
GROUP jackson-core   → com.fasterxml.jackson.core : jackson-databind | jackson-core | jackson-annotations
GROUP jackson-dtype  → com.fasterxml.jackson.datatype : jackson-datatype-jsr310 | jackson-datatype-jdk8
```

Result per group:
- All siblings on same version → `consistent ✅`
- Different versions → `pre-existing mismatch ⚠️` — resolve to the target safe version as part of this PR

---

### Step 1.5 — Print Fix Plan

```
FIX PLAN
─────────────────────────────────────────────────────────────
Repo        : <REPO>
Jira ticket : <JIRA_TICKET_ID>

Fixes to apply (CRITICAL first):
  1. [CRITICAL] log4j-core            inline   2.14.1   → 2.17.2   CVE-2021-44228
  2. [CRITICAL] commons-collections   inline   3.2.1    → 3.2.2    CVE-2015-7501
  3. [HIGH]     jackson-databind      inline   2.13.2   → 2.14.3   CVE-2020-36518
  4. [MEDIUM]   guava                 inline   29.0-jre → 32.1.3   CVE-2023-2976
  5. [LOW]      gson                  inline   2.8.5    → 2.8.9    CVE-2022-25647

Sibling fixes (consistency enforcement):
  • log4j-api            → 2.17.2  (matches log4j-core)
  • jackson-core         → 2.14.3  (matches jackson-databind)
  • jackson-annotations  → 2.14.3  (matches jackson-databind)

Skipped (BOM-managed):
  • spring-core — managed by Spring Boot BOM 3.2.3

Sibling audit:
  jjwt    : consistent ✅  (all on ${jjwt.version} = 0.12.3)
  jackson : pre-existing mismatch ⚠️ — will be resolved in this PR
─────────────────────────────────────────────────────────────
```

> **Pause and ask the user** before continuing if:
> - More than 10 fixes are required, OR
> - Any fix involves a MAJOR version bump (e.g. `1.x → 2.x`)

---

## ═══════════════════════════════════════════
## PHASE 2 — FIX
## ═══════════════════════════════════════════

Process fixes in severity order. Apply **all changes to pom.xml at once** before running validation.

---

### Fix Rules

#### Property-backed (PREFERRED)
Update the property in `<properties>` only — do NOT also edit `<dependency>` blocks:
```xml
<!-- BEFORE -->
<jackson.version>2.13.2</jackson.version>

<!-- AFTER -->
<jackson.version>2.14.3</jackson.version>
```

#### Inline
Update the `<version>` tag directly inside the `<dependency>` block:
```xml
<!-- BEFORE -->
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-core</artifactId>
  <version>2.14.1</version>
</dependency>

<!-- AFTER -->
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-core</artifactId>
  <version>2.17.2</version>
</dependency>
```

#### Sibling consistency — after every fix
Scan the fixed package's sibling group. Update all siblings to match the new version:
```
jackson-databind fixed → 2.14.3
  → jackson-core was 2.13.2       → update to 2.14.3
  → jackson-annotations was 2.13.2 → update to 2.14.3
```
If a sibling bump causes a MAJOR version jump → apply it and add to flagged concerns.

#### Transitive dependency
Add a `<dependencyManagement>` block to force the safe version:
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j-core</artifactId>
      <version>2.17.2</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

#### BOM-managed — SKIP
Do not add explicit versions. Record in skip list.

#### No patch available
Do not change the version. Add to flagged concerns.

---

### Changes Log (produce after all edits)

```
CHANGES LOG
─────────────────────────────────────────────────────────────
FIXED   : log4j-core 2.14.1 → 2.17.2 (inline) — CVE-2021-44228
FIXED   : log4j-api  2.14.1 → 2.17.2 (inline, sibling consistency)
FIXED   : commons-collections 3.2.1 → 3.2.2 (inline) — CVE-2015-7501
FIXED   : jackson-databind 2.13.2 → 2.14.3 (inline) — CVE-2020-36518
FIXED   : jackson-core 2.13.2 → 2.14.3 (inline, sibling consistency)
FIXED   : jackson-annotations 2.13.2 → 2.14.3 (inline, sibling consistency)
FIXED   : guava 29.0-jre → 32.1.3 (inline) — CVE-2023-2976
FIXED   : gson 2.8.5 → 2.8.9 (inline) — CVE-2022-25647
SKIPPED : spring-core (BOM-managed by Spring Boot 3.2.3 parent)
─────────────────────────────────────────────────────────────
```

---

## ═══════════════════════════════════════════
## PHASE 3 — VALIDATE
## ═══════════════════════════════════════════

Run checks in order. On any failure **revert only the specific failing fix** — never the whole pom.xml.
After each revert, re-run the failing check to confirm stability before continuing.

---

### Check 1 — Dependency Tree (per fixed dependency)

```bash
mvn dependency:tree -Dincludes=<groupId>:<artifactId> -q
```

Verify the old vulnerable version no longer appears in the tree.

**If old version still present (pulled transitively):**
Add a `<dependencyManagement>` override (see Phase 2 / Transitive fix) and re-run to confirm.
Record: `⚠️ <package>: transitive pull — dependencyManagement override added`

---

### Check 2 — Compile

```bash
mvn compile -q
```

**If FAILED:**
- Read the error — identify which fix's artifact is named in the compiler error
- Revert that specific change in pom.xml
- Flag: `"Fix for <package> caused compile failure — reverted, manual review needed"`
- Re-run `mvn compile -q` to confirm stability

---

### Check 3 — Unit Tests

```bash
mvn test -q
```

**If FAILED:**
- Read the stack trace — identify the artifact most likely responsible
- Revert that specific fix
- Flag: `"Unit tests failed after fixing <package> — reverted, manual review needed"`
- Re-run `mvn test -q` to confirm stability

---

### Check 4 — Application Start Smoke Check

```bash
mvn spring-boot:run > /tmp/hms_smoke.log 2>&1 &
APP_PID=$!
sleep 30
curl -sf http://localhost:8080/api/v1/actuator/health
HEALTH=$?
kill $APP_PID 2>/dev/null
wait $APP_PID 2>/dev/null
if [ $HEALTH -ne 0 ]; then echo "HEALTH_CHECK_FAILED"; cat /tmp/hms_smoke.log | tail -30; fi
```

> Requires MySQL at `localhost:3306` / database `hms_db`. If unavailable → skip and flag:
> `"Smoke check skipped — MySQL not reachable in this environment"`

**If health check FAILED (MySQL available):**
- Revert the most recent unconfirmed fix
- Flag: `"App failed health check after fixing <package> — reverted"`

---

### Validation Report

```
VALIDATION RESULTS
─────────────────────────────────────────────────────────────
Validated fixes:
  ✅ log4j-core 2.14.1 → 2.17.2
  ✅ log4j-api  2.14.1 → 2.17.2 (sibling)
  ✅ commons-collections 3.2.1 → 3.2.2
  ✅ jackson-databind / jackson-core / jackson-annotations → 2.14.3
  ✅ gson 2.8.5 → 2.8.9

Reverted fixes:
  ❌ guava 29.0-jre → 32.1.3 (compile failure — API incompatibility)

Dependency tree:
  ✅ log4j-core — old 2.14.1 no longer present
  ⚠️ jackson-databind — transitive pull found, dependencyManagement override added

Build checks:
  mvn dependency:tree : ✅ PASSED
  mvn compile         : ✅ PASSED
  mvn test            : ✅ PASSED
  health check        : ✅ PASSED
─────────────────────────────────────────────────────────────
```

> **If ALL fixes are reverted** → do NOT raise a PR. Print the validation report and stop:
> `"All fixes were reverted due to failures. No PR raised. See flagged concerns for manual action."`

---

## ═══════════════════════════════════════════
## PHASE 4 — FLAG CONCERNS
## ═══════════════════════════════════════════

Collect all concerns from Phases 1–3 and categorise:

| Icon | Category | Trigger |
|------|----------|---------|
| 🔴 | Compile failure | Fix reverted — compile broke |
| 🔴 | Test failure | Fix reverted — unit tests broke |
| 🔴 | Health check failure | Fix reverted — app did not start |
| 🟠 | Major version bump | MAJOR bump applied (needs integration testing) |
| 🟠 | No patch available | CVE open with no safe version published |
| 🟠 | Transitive override added | `<dependencyManagement>` block added |
| 🟡 | BOM-managed skipped | Spring Boot BOM controls version |
| 🟡 | Pre-existing sibling mismatch | Siblings were already inconsistent — now resolved |
| 🟡 | Smoke check skipped | MySQL unavailable in environment |

### Recommended Additional Checks

Flag these for human review if applicable:

1. **Integration tests** — if `*IT.java` or `*IntegrationTest.java` exist: run `mvn verify`
2. **REST endpoint testing** — if `jackson-databind` was bumped, test serialization-heavy endpoints with real payloads
3. **Auth flow testing** — if `jjwt` was bumped, test login → token → protected endpoint end-to-end
4. **Schema compatibility** — if Hibernate/JPA libraries were bumped, test against both fresh and existing schema
5. **Dependabot PR conflicts** — if Dependabot has open PRs for these packages, they should be closed
6. **Docker image rebuild** — if the service is containerised, rebuild after merge

---

## ═══════════════════════════════════════════
## PHASE 5 — REPORT (JIRA ONLY)
## ═══════════════════════════════════════════

> ⚠️ **No PR is raised by this agent.** All fixes are applied directly to pom.xml on the working branch. A human reviewer is responsible for raising the PR after reviewing the changes.

### Step 5.1 — Update Jira Ticket via Jira MCP

_(Skip entirely if `JIRA_TICKET_ID` was not provided)_

#### Update ticket title to reflect resolved counts:
```
<SERVICE_NAME> (C-<n>, H-<n>, M-<n>, L-<n>) ← counts of REMAINING open alerts
```
Example after fixing 2 CRITICAL: `HMS (C-0, H-1, M-1, L-4)`

#### Add comment:
```
✅ Fixes applied to pom.xml. Ready for manual PR creation and review.

Resolution summary:
  Fixes applied    : X
  Fixes reverted   : X  ← needs manual action (see flagged concerns)
  Skipped (BOM)    : X
  Concerns flagged : X

Validated: mvn compile ✅ | mvn test ✅ | health check ✅

Automated by GHAS Vulnerability Management — Workflow 2 / Vuln Resolver
```

#### Transition ticket status → **In Review**

If Jira transition fails → still proceed. Log:
`"Jira update failed — pom.xml fixes were applied successfully"`

---

### Step 5.3 — Final Summary

```
╔══════════════════════════════════════════════════════════════╗
║         WORKFLOW 2 — VULN RESOLVER COMPLETE                  ║
╠══════════════════════════════════════════════════════════════╣
║  Service          : HMS                                       ║
║  Jira ticket      : SEC-101 → In Review                       ║
╠══════════════════════════════════════════════════════════════╣
║  Alerts found     : 5  (C-2, H-1, M-1, L-1)                  ║
║  Fixes applied    : 7  (incl. 3 sibling consistency fixes)    ║
║  Fixes reverted   : 1  (guava — compile failure)              ║
║  Skipped (BOM)    : 1                                         ║
║  Concerns flagged : 3                                         ║
╠══════════════════════════════════════════════════════════════╣
║  mvn compile      : ✅ PASSED                                 ║
║  mvn test         : ✅ PASSED                                 ║
║  health check     : ✅ PASSED                                 ║
╠══════════════════════════════════════════════════════════════╣
║  ℹ️  pom.xml updated. Please review changes and raise a PR.   ║
╠══════════════════════════════════════════════════════════════╣
║  ⚠️  MANUAL ACTION REQUIRED:                                  ║
║     • guava 29.0-jre → 32.1.3 (compile failure — reverted)   ║
╚══════════════════════════════════════════════════════════════╝
```

---

## Hard Rules

1. **Never raise a PR** — apply fixes directly to pom.xml; a human reviewer raises the PR
2. **Never touch BOM-managed dependencies** — no explicit `<version>` tags
3. **Never revert the entire pom.xml** — revert only the specific failing fix
4. **Always fix CRITICAL before HIGH → MEDIUM → LOW**
5. **Always update ALL siblings in a group when fixing one** — never leave partial group versions
6. **Prefer property-backed over inline** — one property change is safer and has wider coverage
7. **Multiple CVEs on same package** → use the highest required safe version
8. **Stop and confirm with the user** before applying more than 10 fixes or any MAJOR version bump
9. **A fix is only validated** after compile + test + health all pass
10. **Always include GHS ID + CVE ID** in the PR table and Jira comment
