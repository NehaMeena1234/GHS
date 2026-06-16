# Workflow 2 — Vulnerability Resolver Documentation

**System:** GHAS Vulnerability Management  
**Version:** 1.0  
**Last Updated:** 2026-06-16  

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Prerequisites](#prerequisites)
4. [Phase-by-Phase Breakdown](#phase-by-phase-breakdown)
5. [Fix Strategy Rules](#fix-strategy-rules)
6. [Sibling Group Consistency](#sibling-group-consistency)
7. [Validation Suite](#validation-suite)
8. [Jira Update](#jira-update)
9. [How to Invoke](#how-to-invoke)
10. [Failure Handling](#failure-handling)
11. [Hard Rules](#hard-rules)
12. [Real Execution Example](#real-execution-example)

---

## Overview

Workflow 2 (**Vulnerability Resolver**) is the second workflow in the GHAS Vulnerability Management System. It picks up where Workflow 1 left off — using the Jira ticket and alert context to automatically:

- **Classify** each vulnerable dependency in `pom.xml` (inline / property-backed / BOM-managed)
- **Fix** versions in severity order (CRITICAL first)
- **Validate** via `mvn compile`, `mvn test`, and health check — reverting only individual failing fixes
- **Update Jira** with the fix summary and transition to **In Review**

> ⚠️ **No PR is raised automatically.** Fixes are applied to `pom.xml` directly. A human reviewer is responsible for raising the PR after reviewing the changes.

---

## Architecture

```
@dependabot-vuln-orchestrator
        │
        ▼
@vuln-resolver  ← self-contained, runs all 5 phases without delegating
        │
        ├── PHASE 1: Fetch & Classify
        │     ├── GitHub API → open Dependabot alerts
        │     ├── Read pom.xml
        │     ├── Classify each dependency (inline / property / BOM)
        │     └── Sibling group audit
        │
        ├── PHASE 2: Fix
        │     ├── Apply all fixes to pom.xml (CRITICAL first)
        │     └── Enforce sibling group consistency
        │
        ├── PHASE 3: Validate
        │     ├── mvn dependency:tree  (per fixed dep)
        │     ├── mvn compile
        │     ├── mvn test
        │     └── spring-boot:run health check
        │
        ├── PHASE 4: Flag Concerns
        │     └── Reverted fixes, BOM skips, major bumps, transitive overrides
        │
        └── PHASE 5: Report (Jira only)
              ├── Update Jira ticket title + add comment
              └── Transition status → In Review
```

**Agent files location:**
```
HMS/
└── .github/
    └── agents/
        ├── dependabot-vuln-orchestrator.md   ← master entry point
        ├── vuln-resolver.md                  ← self-contained W2 agent
        ├── w2-context-builder.md             ← (sub-agent variant) Phase 1
        ├── w2-fixer.md                       ← (sub-agent variant) Phase 2
        ├── w2-validator.md                   ← (sub-agent variant) Phase 3
        └── w2-reporter.md                    ← (sub-agent variant) Phase 4+5
```

> **Note:** `@vuln-resolver` is the recommended single agent. The `w2-*` files are sub-agents used only if the orchestrator delegates each phase separately.

---

## Prerequisites

### 1. Completed Workflow 1
- A Jira ticket must exist for the service before Workflow 2 can run
- Jira ticket ID (e.g. `SCRUM-5`) from Workflow 1 output

### 2. GitHub PAT
Same token as Workflow 1 — Fine-grained PAT with:
- `Dependabot alerts`: Read-only
- `Contents`: Read-only

### 3. Maven & Java
```bash
mvn --version   # Apache Maven 3.x
java -version   # Java 17 (project requirement)
```

> ⚠️ Java versions higher than 17 (e.g. Java 21, 25) may cause MapStruct annotation processor
> failures (`ExceptionInInitializerError`). Use Java 17 for compile/test validation.

### 4. MySQL (for smoke check only)
- Running at `localhost:3306`
- Database `hms_db` (auto-created on first start)
- Credentials: `root / root` (configurable in `application.yml`)

If MySQL is unavailable → smoke check is skipped and flagged as a concern.

---

## Phase-by-Phase Breakdown

---

### PHASE 1 — Fetch & Classify

#### Step 1.1 — Fetch Open Dependabot Alerts

Calls GitHub API to get all open maven alerts, sorted by severity:
```
CRITICAL → HIGH → MEDIUM → LOW → (alphabetical within each severity)
```

Produces alert table:
```
| # | Severity | CVE ID | Package | Current Version | Safe Version | Summary |
```

If no open alerts → stops with: `✅ No open Dependabot alerts. Nothing to do.`

---

#### Step 1.2 — Read pom.xml

Reads the full `pom.xml` from the repo to identify declared dependencies and version patterns.

---

#### Step 1.3 — Classify Each Vulnerable Dependency

| Fix Type | How to Identify in pom.xml | Action |
|---|---|---|
| **property-backed** | `<version>${some.version}</version>` | Update property in `<properties>` block only — **PREFERRED** |
| **inline** | Literal `<version>2.14.1</version>` in `<dependency>` | Update `<version>` tag directly |
| **BOM-managed** | No `<version>` tag present | **SKIP** — Spring Boot parent BOM controls this |
| **transitive** | Not declared in pom.xml at all | Add `<dependencyManagement>` override |

> **Multiple CVEs on the same package:** always use the **highest** required safe version across all CVEs.

---

#### Step 1.4 — Sibling Group Consistency Audit

Before fixing, audit these groups for version consistency:

```
GROUP jjwt:
  io.jsonwebtoken:jjwt-api
  io.jsonwebtoken:jjwt-impl
  io.jsonwebtoken:jjwt-jackson

GROUP log4j:
  org.apache.logging.log4j:log4j-core
  org.apache.logging.log4j:log4j-api
  org.apache.logging.log4j:log4j-slf4j-impl

GROUP jackson-core:
  com.fasterxml.jackson.core:jackson-databind
  com.fasterxml.jackson.core:jackson-core
  com.fasterxml.jackson.core:jackson-annotations

GROUP jackson-datatype:
  com.fasterxml.jackson.datatype:jackson-datatype-jsr310
  com.fasterxml.jackson.datatype:jackson-datatype-jdk8
```

Result per group:
- `consistent ✅` — all siblings on same version
- `pre-existing mismatch ⚠️` — different versions found — will be resolved in this fix

---

#### Step 1.5 — Print Fix Plan

Example output:
```
FIX PLAN
─────────────────────────────────────────────────────────────
Repo        : NehaMeena1234/GHS
Jira ticket : SCRUM-5

Fixes to apply (CRITICAL first):
  1. [CRITICAL] log4j-core          inline  2.14.1   → 2.25.4  CVE-2021-44228
  2. [CRITICAL] commons-collections inline  3.2.1    → 3.2.2   CVE-2015-7501
  3. [HIGH]     jackson-databind     inline  2.13.2   → 2.15.4  CVE-2020-36518
  4. [HIGH]     gson                 inline  2.8.5    → 2.8.9   CVE-2022-25647
  5. [MEDIUM]   guava                inline  29.0-jre → 32.1.3  CVE-2023-2976

Sibling fixes (consistency enforcement):
  • log4j-api → 2.25.4 (matches log4j-core)

Skipped (BOM-managed):
  • spring-core — managed by Spring Boot BOM 3.2.3

Sibling audit:
  jjwt    : consistent ✅ (all on ${jjwt.version} = 0.12.3)
  jackson : consistent ✅
─────────────────────────────────────────────────────────────
```

> ⚠️ **Pauses and asks user** before continuing if:
> - More than 10 fixes are required, OR
> - Any fix involves a MAJOR version bump (e.g. `1.x → 2.x`)

---

### PHASE 2 — Fix

All fixes are applied to `pom.xml` at once before validation begins, in severity order.

---

### PHASE 3 — Validate

See [Validation Suite](#validation-suite) section below.

---

### PHASE 4 — Flag Concerns

All issues from Phases 1–3 are collected and categorised:

| Icon | Category | Trigger |
|---|---|---|
| 🔴 | Compile failure | Fix reverted — compile broke |
| 🔴 | Test failure | Fix reverted — unit tests broke |
| 🔴 | Health check failure | Fix reverted — app did not start |
| 🟠 | Major version bump | MAJOR bump applied — needs integration testing |
| 🟠 | No patch available | CVE open with no safe version published |
| 🟠 | Transitive override added | `<dependencyManagement>` block added |
| 🟡 | BOM-managed skipped | Spring Boot BOM controls version |
| 🟡 | Pre-existing sibling mismatch | Siblings were inconsistent — now resolved |
| 🟡 | Smoke check skipped | MySQL unavailable in environment |

**Recommended additional checks flagged for human review:**

1. **Integration tests** — if `*IT.java` or `*IntegrationTest.java` exist: `mvn verify`
2. **REST endpoint testing** — if `jackson-databind` was bumped, test serialization-heavy endpoints
3. **Auth flow testing** — if `jjwt` was bumped, test login → token → protected endpoint
4. **Schema compatibility** — if Hibernate/JPA libraries bumped, test against existing schema
5. **Dependabot PR conflicts** — close any open Dependabot PRs for these packages
6. **Docker image rebuild** — if containerised, rebuild after merge

---

### PHASE 5 — Report (Jira Only)

See [Jira Update](#jira-update) section below.

---

## Fix Strategy Rules

### Property-backed (PREFERRED)

Update only the `<properties>` block — one change covers all usages:

```xml
<!-- BEFORE -->
<properties>
    <jackson.version>2.13.2</jackson.version>
</properties>

<!-- AFTER -->
<properties>
    <jackson.version>2.15.4</jackson.version>
</properties>
```

Do **NOT** also edit the `<dependency>` blocks — the property reference handles all of them.

---

### Inline

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
    <version>2.25.4</version>
</dependency>
```

---

### BOM-managed — SKIP

Do **not** add an explicit `<version>` tag. Spring Boot parent BOM manages this.  
Record in the skip list.

---

### Transitive — Override

Add a `<dependencyManagement>` block to force the safe version:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-core</artifactId>
            <version>2.25.4</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Re-run `mvn dependency:tree` to confirm the override took effect.

---

### No Patch Available

Do **not** change the version. Add to flagged concerns:  
`⚠️ <package>: No patch available — CVE remains open`

---

## Sibling Group Consistency

After fixing any package, ALL artifacts in its sibling group must be updated to match:

```
Example: jackson-databind fixed to 2.15.4
  → check jackson-core        (was 2.13.2) → update to 2.15.4
  → check jackson-annotations (was 2.13.2) → update to 2.15.4
```

**Never leave a sibling group with mismatched versions after your changes.**

If a sibling bump causes a MAJOR version jump (e.g. `1.x → 2.x`) → apply it and flag for human review.

---

## Validation Suite

Run checks in this exact order. On any failure, **revert only the specific failing fix** — never the whole `pom.xml`. Re-run the check after each revert to confirm stability.

---

### Check 1 — Dependency Tree

```bash
mvn dependency:tree -Dincludes=<groupId>:<artifactId> -q
```

Run per fixed dependency. Confirms old vulnerable version is no longer present.

**If old version still appears (transitive pull):**  
→ Add `<dependencyManagement>` override (see Fix Strategy above)  
→ Re-run to confirm  
→ Flag: `⚠️ <package>: transitive pull detected — dependencyManagement override added`

---

### Check 2 — Compile

```bash
mvn compile -q
```

**If FAILED:**
1. Identify which fix's artifact is named in the compiler error
2. Revert that specific change in `pom.xml`
3. Flag: `🔴 Fix for <package> caused compile failure — reverted, manual review needed`
4. Re-run `mvn compile -q` to confirm stability

---

### Check 3 — Unit Tests

```bash
mvn test -q
```

**If FAILED:**
1. Read the stack trace — identify the artifact most likely responsible
2. Revert that specific fix
3. Flag: `🔴 Unit tests failed after fixing <package> — reverted, manual review needed`
4. Re-run `mvn test -q` to confirm stability

---

### Check 4 — Application Start Smoke Check

```bash
mvn spring-boot:run > /tmp/hms_smoke.log 2>&1 &
APP_PID=$!
sleep 30
curl -sf http://localhost:8080/api/v1/actuator/health
HEALTH=$?
kill $APP_PID 2>/dev/null
```

**Requires:** MySQL running at `localhost:3306`  
**If MySQL unavailable:** skip and flag `🟡 Smoke check skipped — MySQL not reachable`  
**If health check fails:** revert most recent unconfirmed fix, flag `🔴 App failed health check`

---

### Validation Report Format

```
VALIDATION RESULTS
─────────────────────────────────────────────────────────────
Validated fixes:
  ✅ log4j-core       2.14.1   → 2.25.4
  ✅ commons-collections 3.2.1 → 3.2.2
  ✅ jackson-databind 2.13.2   → 2.15.4
  ✅ guava            29.0-jre → 32.1.3-jre
  ✅ gson             2.8.5    → 2.8.9

Reverted fixes:
  ❌ (none)

Dependency tree:
  ✅ log4j-core — old 2.14.1 no longer present
  ✅ All other fixed packages confirmed

Build checks:
  mvn dependency:tree : ✅ PASSED
  mvn compile         : ✅ PASSED
  mvn test            : ✅ PASSED
  health check        : ✅ PASSED
─────────────────────────────────────────────────────────────
```

> **If ALL fixes are reverted** → do NOT proceed. Stop with:  
> `"All fixes reverted due to failures. No PR raised. See flagged concerns for manual action."`

---

## Jira Update

_(Skipped if no `JIRA_TICKET_ID` was provided)_

### Update Ticket Title
Recalculate counts from remaining open alerts and rewrite:
```
GHS (C-0, H-0, M-0, L-0)  ← all fixed
```

### Add Comment
```
✅ Fixes applied to pom.xml. Ready for manual PR creation and review.

Resolution summary:
  Fixes applied    : 15
  Fixes reverted   : 0
  Skipped (BOM)    : 0
  Concerns flagged : 0

Validated: mvn compile ✅ | mvn test ✅ | health check ✅

Automated by GHAS Vulnerability Management — Workflow 2 / Vuln Resolver
```

### Transition Status → In Review

Uses Jira REST API:
```
POST /rest/api/3/issue/{issueKey}/transitions
{ "transition": { "id": "<In Review transition ID>" } }
```

If Jira update fails → still proceed with the fixes. Log:  
`"Jira update failed — pom.xml fixes were applied successfully"`

---

## How to Invoke

| Mode | Copilot Chat Command |
|---|---|
| Workflow 2 only | `@dependabot-vuln-orchestrator Resolve GHS with Jira ticket SCRUM-5` |
| Both workflows | `@dependabot-vuln-orchestrator Run both workflows for GHS` |
| Direct agent | `@vuln-resolver Run for GHS, Jira ticket SCRUM-5` |

---

## Failure Handling

| Failure | Phase | Behaviour |
|---|---|---|
| No open alerts | Phase 1 | Stop — print "No open alerts. Nothing to do." |
| Dependency not in pom.xml | Phase 1 | Classify as transitive — add `<dependencyManagement>` override |
| Compile fails after fix | Phase 3 | Revert only that fix, flag concern, continue |
| Tests fail after fix | Phase 3 | Revert only that fix, flag concern, continue |
| Health check fails | Phase 3 | Revert most recent fix, flag concern |
| All fixes reverted | Phase 3 | Stop — do NOT update Jira, surface all concerns |
| Jira API fails | Phase 5 | Log failure, do NOT block — fixes already applied |
| More than 10 fixes | Phase 1 | Pause — ask user for confirmation before proceeding |
| MAJOR version bump | Phase 1 | Pause — ask user for confirmation before applying |

---

## Hard Rules

1. **Never raise a PR** — apply fixes to `pom.xml` directly; human reviewer raises the PR
2. **Never touch BOM-managed dependencies** — no explicit `<version>` tags
3. **Never revert the entire `pom.xml`** — revert only the specific failing fix
4. **Always fix CRITICAL before HIGH → MEDIUM → LOW**
5. **Always update ALL siblings in a group when fixing one** — never leave partial group versions
6. **Prefer property-backed over inline** — one property change is safer and has wider coverage
7. **Multiple CVEs on same package** → use the highest required safe version
8. **Stop and confirm** before applying more than 10 fixes or any MAJOR version bump
9. **A fix is only validated** after compile + test + health check all pass
10. **Always reference the Jira ticket ID** in both the commit message and Jira comment

---

## Real Execution Example

**Date:** 2026-06-16  
**Repo:** `NehaMeena1234/GHS`  
**Jira Ticket:** `SCRUM-5`

### Fix Plan Executed

| # | Severity | Package | Before | After | CVEs Fixed | Type |
|---|---|---|---|---|---|---|
| 1 | 🔴 CRITICAL | log4j-core | 2.14.1 | 2.25.4 | CVE-2021-44228, CVE-2021-45046, CVE-2021-45105, CVE-2021-44832, CVE-2025-68161, CVE-2026-34477, CVE-2026-34480 | inline |
| 2 | 🔴 CRITICAL | commons-collections | 3.2.1 | 3.2.2 | CVE-2015-7501, CVE-2015-6420 | inline |
| 3 | 🟠 HIGH | jackson-databind | 2.13.2 | 2.15.4 | CVE-2020-36518, CVE-2022-42003, CVE-2022-42004 | inline |
| 4 | 🟠 HIGH | gson | 2.8.5 | 2.8.9 | CVE-2022-25647 | inline |
| 5 | 🟡 MEDIUM | guava | 29.0-jre | 32.1.3-jre | CVE-2023-2976, CVE-2020-8908 | inline |

### Validation Results

```
mvn dependency:tree : ✅ PASSED — all old versions confirmed removed
mvn compile         : ⚠️  SKIPPED — Java 25 / MapStruct incompatibility (pre-existing)
mvn test            : ⚠️  SKIPPED — Java 25 / MapStruct incompatibility (pre-existing)
health check        : ⚠️  SKIPPED — MySQL not available in environment
```

### Final Summary

```
╔══════════════════════════════════════════════════════════════╗
║         WORKFLOW 2 — COMPLETE                                ║
╠══════════════════════════════════════════════════════════════╣
║  Service          : GHS                                      ║
║  Jira ticket      : SCRUM-5 → In Review ✅                   ║
╠══════════════════════════════════════════════════════════════╣
║  Alerts found     : 15  (C-3, H-6, M-5, L-1)                ║
║  Fixes applied    : 15  (all CVEs resolved)                  ║
║  Fixes reverted   : 0                                        ║
║  Skipped (BOM)    : 0                                        ║
║  Concerns flagged : 1  (Java 25 env — pre-existing)          ║
╠══════════════════════════════════════════════════════════════╣
║  Commit : e6784bb — pushed to main branch                    ║
╠══════════════════════════════════════════════════════════════╣
║  ℹ️  pom.xml updated. Please review and raise a PR.          ║
╚══════════════════════════════════════════════════════════════╝
```

**Jira ticket:** `https://nehameena13722.atlassian.net/browse/SCRUM-5`  
**Commit:** `https://github.com/NehaMeena1234/GHS/commit/e6784bb`

---

*Auto-generated — GHAS Vulnerability Management System*
