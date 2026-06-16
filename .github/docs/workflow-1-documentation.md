# Workflow 1 — Alert Ingestion Documentation

**System:** GHAS Vulnerability Management  
**Version:** 1.0  
**Last Updated:** 2026-06-16  

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Prerequisites](#prerequisites)
4. [Agent Chain](#agent-chain)
5. [Step-by-Step Execution](#step-by-step-execution)
6. [Output Artifacts](#output-artifacts)
7. [Excel Report Structure](#excel-report-structure)
8. [How to Invoke](#how-to-invoke)
9. [Failure Handling](#failure-handling)
10. [Real Execution Example](#real-execution-example)

---

## Overview

Workflow 1 (**Alert Ingestion**) is the first of two automated workflows in the GHAS Vulnerability Management System. Its purpose is to:

- Fetch all **open Dependabot security alerts** from configured GitHub repositories
- Export them to a **color-coded, sorted Excel report**
- Create or update a **Jira ticket per service** with a full grouped alert table

Workflow 1 feeds directly into Workflow 2 (Vulnerability Resolver) by providing the Jira ticket ID and alert context needed to patch `pom.xml`.

---

## Architecture

```
User / Orchestrator
        │
        ▼
@dependabot-vuln-orchestrator  ← entry point
        │
        ▼
@alert-ingestor  ← coordinates all 3 sub-agents internally
        │
        ├──▶ @w1-fetcher       Step 1: GitHub API → Excel file
        │
        ├──▶ @w1-sorter        Step 2: Sort Excel by service + severity
        │
        └──▶ @w1-jira-manager  Step 3: Dedup check → Create/Update Jira ticket
```

**Agent files location:**
```
HMS/
└── .github/
    ├── agents/
    │   ├── dependabot-vuln-orchestrator.md   ← master entry point
    │   ├── alert-ingestor.md                 ← W1 coordinator
    │   ├── w1-fetcher.md                     ← Sub-Agent 1
    │   ├── w1-sorter.md                      ← Sub-Agent 2
    │   └── w1-jira-manager.md                ← Sub-Agent 3
    └── scripts/
        └── fetch_dependabot_alerts.py        ← Python fetch script
```

---

## Prerequisites

### 1. GitHub Personal Access Token (PAT)

Create a **Fine-grained PAT** at `https://github.com/settings/tokens` with:

| Permission | Level |
|---|---|
| Repository access | All repositories (or select GHS) |
| Dependabot alerts | Read-only |
| Contents | Read-only |

Store in `.env` file at repo root:
```
GITHUB_TOKEN=github_pat_your_token_here
```

> ⚠️ The `.env` file is gitignored — never commit it.

### 2. Python Dependencies
```bash
pip install requests openpyxl python-dotenv
```

### 3. Jira Setup
- Atlassian account with a Jira project
- Jira API token from `https://id.atlassian.com/manage-profile/security/api-tokens`
- Jira base URL (e.g. `https://yourname.atlassian.net`)

### 4. Dependabot Enabled on Repository
Verify at: `https://github.com/<owner>/<repo>/settings/security_analysis`

---

## Agent Chain

### @dependabot-vuln-orchestrator (Entry Point)

The master orchestrator. Receives the user command and delegates to `@alert-ingestor`.

**Triggers Workflow 1 when invoked with:**
- `ingest` — run Workflow 1 only
- `both` — run Workflow 1 then Workflow 2

**Collects on completion:**
- Excel file path
- Services with newly created Jira tickets → passes to Workflow 2 if mode is `both`

---

### @alert-ingestor (W1 Coordinator)

Internally coordinates all three sub-agents in sequence. Handles the full pipeline from fetch → sort → Jira.

---

### @w1-fetcher (Sub-Agent 1)

**Responsibility:** Run the Python fetch script and produce the Excel file.

**Steps:**
1. Install dependencies: `pip install requests openpyxl`
2. Run: `python .github/scripts/fetch_dependabot_alerts.py`
3. Verify output file exists and has data rows

**Script behavior (`fetch_dependabot_alerts.py`):**
- Reads `GITHUB_TOKEN` from `.env`
- Calls GitHub REST API: `GET /repos/{owner}/{repo}/dependabot/alerts`
- Filters to `maven` ecosystem only (configurable via `ECOSYSTEM_FILTER`)
- Paginates via `Link` header (cursor-based)
- Maps each alert to a row with 14 columns
- Exports to `dependabot_alerts_<YYYYMMDD_HHMMSS>.xlsx`

**Passes to @w1-sorter:**
- Excel file path
- Total alert count
- Count per severity (CRITICAL / HIGH / MEDIUM / LOW)

**Failure conditions:**

| Condition | Action |
|---|---|
| `GITHUB_TOKEN` not set | Stop — tell user to set env variable |
| 403 Access Denied | Stop — token missing `Dependabot alerts` scope |
| 404 Not Found | Stop — repo not found or Dependabot not enabled |
| Output file empty | Stop — report "No open Maven Dependabot alerts found" |

---

### @w1-sorter (Sub-Agent 2)

**Responsibility:** Sort and group alerts for Jira ticket creation.

**Sort order:**
- **Primary:** Service name (A → Z)
- **Secondary:** Severity (CRITICAL → HIGH → MEDIUM → LOW)

**Severity sort values:**
```
CRITICAL = 0
HIGH     = 1
MEDIUM   = 2
LOW      = 3
```

**Produces:**
- Sorted "Alerts" sheet written back to Excel
- Updated "Summary" sheet with per-service counts
- Grouped structure: `{ "GHS": [alert1, alert2, ...], ... }`

**Passes to @w1-jira-manager:**
- Updated Excel file path
- Grouped alerts per service
- List of unique service names
- Alert counts per service

---

### @w1-jira-manager (Sub-Agent 3)

**Responsibility:** Create or update one Jira ticket per service.

#### Step 1 — Dedup Check
Searches Jira for an existing open ticket:
```
project = "<PROJECT_KEY>"
AND labels = "<SERVICE_NAME>"
AND labels = "dependabot"
AND statusCategory in ("To Do", "In Progress")
```
- **Found** → update existing ticket (Step 2b)
- **Not found** → create new ticket (Step 2a)

#### Step 2a — Create New Ticket

| Field | Value |
|---|---|
| Summary | `GHS (C-3, H-6, M-5, L-1)` |
| Issue Type | Task (or Bug if available) |
| Priority | Highest (CRITICAL) → Critical (HIGH) → Major (MEDIUM) → Minor (LOW) |
| Labels | `GHAS`, `<SERVICE_NAME>`, `dependabot`, `security` |
| Description | Full grouped alert table |

**Title format:** `<SERVICE> (C-<n>, H-<n>, M-<n>, L-<n>)`  
Only includes severity labels with at least 1 alert.

**Description template:**
```
## Dependabot Security Alerts — <SERVICE>

Repo: <REPO>
Scan Date: <YYYY-MM-DD>
Total Alerts: <N>  (C-<n> | H-<n> | M-<n> | L-<n>)

### 🔴 CRITICAL
| Alert # | CVE ID | Package | Safe Version | Issue Summary |

### 🟠 HIGH
| Alert # | CVE ID | Package | Safe Version | Issue Summary |

### 🟡 MEDIUM
| Alert # | CVE ID | Package | Safe Version | Issue Summary |

### 🟢 LOW
| Alert # | CVE ID | Package | Safe Version | Issue Summary |
```

#### Step 2b — Update Existing Ticket
1. Recalculate severity counts → rewrite title
2. Regenerate description with latest alert table
3. Add comment: `🔄 Re-scanned on <date> — Alert counts updated`

#### Step 3 — Update Excel
After all services processed:
- **Column M (Jira Key):** e.g. `SCRUM-5`
- **Column N (Jira Status):** `CREATED` or `UPDATED`

---

## Step-by-Step Execution

### Via Copilot Chat (Recommended)

Open Copilot Chat in VS Code and type:
```
@dependabot-vuln-orchestrator Run ingest only
```
or
```
@dependabot-vuln-orchestrator Run both workflows for GHS
```

### Via CLI (Manual)

```bash
# Step 1 — Install dependencies
pip install requests openpyxl python-dotenv

# Step 2 — Create .env with your GitHub PAT
echo "GITHUB_TOKEN=github_pat_your_token" > .env

# Step 3 — Run fetcher (produces sorted Excel + Summary sheet)
cd HMS
python .github/scripts/fetch_dependabot_alerts.py

# Step 4 — Jira ticket creation via REST API
# POST https://<your-domain>.atlassian.net/rest/api/3/issue
# Auth: Basic base64(email:api_token)
```

---

## Output Artifacts

### Excel Report: `dependabot_alerts_<YYYYMMDD_HHMMSS>.xlsx`

**Location:** `HMS/` root directory

**Sheet 1 — Alerts** (color-coded rows):

| Column | Name | Description |
|---|---|---|
| A | Service | Repo name (e.g. `GHS`) |
| B | Repo | Full repo path (e.g. `NehaMeena1234/GHS`) |
| C | Alert # | GitHub Dependabot alert number |
| D | Severity | `CRITICAL` / `HIGH` / `MEDIUM` / `LOW` |
| E | CVE ID | e.g. `CVE-2021-44228` |
| F | Package | e.g. `org.apache.logging.log4j:log4j-core` |
| G | Vulnerable Range | e.g. `>= 2.13.0, < 2.15.0` |
| H | Safe Version | e.g. `2.15.0` |
| I | Manifest | e.g. `pom.xml` |
| J | Scope | e.g. `runtime` |
| K | Summary | Short CVE description |
| L | Alert URL | Direct link to GitHub alert |
| M | Jira Key | Populated by @w1-jira-manager (e.g. `SCRUM-5`) |
| N | Jira Status | `CREATED` or `UPDATED` |

**Row color coding:**

| Color | Severity |
|---|---|
| 🔴 Red `#FF4C4C` | CRITICAL |
| 🟠 Orange `#FF944C` | HIGH |
| 🟡 Yellow `#FFD700` | MEDIUM |
| 🟢 Green `#90EE90` | LOW |

**Sheet 2 — Summary:**

| Severity | Count |
|---|---|
| CRITICAL | 3 |
| HIGH | 6 |
| MEDIUM | 5 |
| LOW | 1 |

---

### Jira Ticket

- **One ticket per service** — never one per CVE
- All CVEs for a service grouped in a single ticket description
- Automatically added to the active sprint
- Labeled: `GHAS`, `GHS`, `dependabot`, `security`

---

## How to Invoke

| Mode | Copilot Chat Command |
|---|---|
| Workflow 1 only | `@dependabot-vuln-orchestrator Run ingest only` |
| Workflow 2 only | `@dependabot-vuln-orchestrator Resolve GHS with Jira ticket SCRUM-5` |
| Both workflows | `@dependabot-vuln-orchestrator Run both workflows for GHS` |

---

## Failure Handling

| Failure | Agent | Behaviour |
|---|---|---|
| `GITHUB_TOKEN` missing | @w1-fetcher | Stop entire workflow, surface error to user |
| 403 from GitHub API | @w1-fetcher | Skip that repo, warn user, continue with next |
| 404 from GitHub API | @w1-fetcher | Skip that repo, warn user, continue with next |
| Excel file empty | @w1-fetcher | Stop — no alerts to process |
| Jira search fails | @w1-jira-manager | Skip that service, log error, continue with next |
| Jira ticket creation fails | @w1-jira-manager | Log failure, continue with remaining services |

---

## Real Execution Example

**Date:** 2026-06-16  
**Repo:** `NehaMeena1234/GHS`  
**Token:** Fine-grained PAT with `Dependabot alerts: Read-only`

### Results

```
W1 COMPLETE
─────────────────────────────────────────
Excel file     : dependabot_alerts_20260616_162140.xlsx
Services found : 1 (GHS)
Total alerts   : 15  (CRITICAL: 3, HIGH: 6, MEDIUM: 5, LOW: 1)

Alerts breakdown:
  CRITICAL : CVE-2015-7501, CVE-2021-44228, CVE-2021-45046
  HIGH     : CVE-2015-6420, CVE-2020-36518, CVE-2021-45105,
             CVE-2022-25647, CVE-2022-42003, CVE-2022-42004
  MEDIUM   : CVE-2021-44832, CVE-2023-2976, CVE-2025-68161,
             CVE-2026-34477, CVE-2026-34480
  LOW      : CVE-2020-8908

Jira results:
  CREATED : 1 → SCRUM-5
  UPDATED : 0
  FAILED  : 0

Services with tickets (for Workflow 2):
  - GHS → SCRUM-5
```

**Jira ticket:** `https://nehameena13722.atlassian.net/browse/SCRUM-5`

---

*Auto-generated — GHAS Vulnerability Management System*
