# Concho → Devin hand-off spec: proposed format

A Concho-generated package that gives Devin what it needs to convert one slice of a legacy system. It
replaces the giant stakeholder-review HTML with two documents:

| Document | Size target | Read by Devin | Changes |
|---|---|---|---|
| **App Brief**: "here is the overall application" | ≤ 4k tokens (well under the 16 KiB Devin auto-loads from `AGENTS.md`) | at the start of every slice session | Per analysis cycle |
| **Slice Spec**: "here is the slice / bounded context we are converting" | ≤ 25k tokens; everything else is reached through breadcrumbs | at the start of that slice's session | Per slice, versioned |

Worked examples, built from what was actually verified on ACAS:
- [`example-app-brief-acas.md`](example-app-brief-acas.md)
- [`example-slice-spec-pl080.md`](example-slice-spec-pl080.md)

---

## 1. Format choice: Markdown with YAML front matter and fenced JSON blocks

- **Markdown prose** carries intent, rationale and caveats. LLMs read it well and humans can review it in a PR.
- **Fenced `json` blocks** carry anything Devin will turn into code, tests or tool calls: record layouts,
  rules, scenarios, breadcrumbs. Keys are stable, so the blocks can be parsed, diffed and validated with a
  JSON Schema.
- **YAML front matter** carries identity and provenance (project, cycle, **source revision**, spec version,
  hash), so both Devin and tooling can find drift without reading the body.
- The package should be a **single file per document**, so it can be attached to a session, committed to
  the repo, or pasted into a prompt without losing structure.

Pure JSON was rejected because the reasoning (why a rule matters, what is uncertain) gets lost or becomes
awkward strings. Pure Markdown was rejected because tables of PIC clauses and rules turn into prose that
Devin then has to parse back into structure.

## 2. Design principles (lessons from the sl055 and pl080 slices)

1. **The source is the truth; the spec is a set of claims about it.** Every factual item carries a source
   span (`file:start-end`) and the spec pins one source revision. Devin verifies rather than trusts.
2. **Status on every rule.** Each rule is `observed` (in the code), `inferred` (from naming, docs or
   patterns) or `proposed` (a modernization addition such as MOD-11). Mixing these cost the most time on pl080.
3. **Compact and addressable.** Inline only what Devin needs to plan. For everything else, give a
   **breadcrumb**: a ready-to-run MCP call that pulls the detail into context when Devin needs it.
4. **Stable IDs.** Rules, records, screens and scenarios have IDs that survive re-analysis, so commits,
   tests and PR descriptions can cite them, and Concho can map Devin's write-back to them.
5. **Executable acceptance.** Rules come with testable acceptance statements and scenarios, so Devin turns
   them into fixtures rather than into prose to interpret.
6. **Decisions are explicit.** Stack choices, "preserve this defect for parity" and out-of-scope items are
   recorded as decisions with an owner. Undecided items are listed as open questions, not left implicit.
7. **There is a write-back contract.** The spec says what Devin should report back (rule verdicts,
   discrepancies), so Concho gets better with every slice.

## 3. Breadcrumbs

A breadcrumb is an MCP call Devin can make verbatim, plus a one-line reason. Concho knows the project key,
cycle, entity names and exact spans, so it can pre-stage these for free.

```json
{
  "id": "bc-rule-batch-encoding",
  "why": "Full rule text, evidence and line refs for batch reference encoding",
  "server": "concho",
  "tool": "get_entity",
  "args": {"project_key": "acas", "cycle": 22, "entity_type": "business_rule",
           "entity_name": "PaymentBatchEncodingAndRolloverRule"}
}
```

Conventions:
- `server` is a logical name. The composer can render the Devin-side slug (e.g. `concho-d40d`) at export time.
- Use existing tools today: `get_entity`, `search_entities`, `get_entities_by_subject`,
  `get_file_content_in_range`, `get_file_metadata`, `get_architecture_element`, `get_artifact`,
  `get_document_contents_in_range`.
- Breadcrumbs that need tools proposed in `docs/concho-atlas-for-devin.md` (https://github.com/sigalarm/acas-modern/pull/4) (`get_record_layout`,
  `get_dependency_closure`, `get_file_access`, `get_screen`) are marked `"proposed": true`, so Devin
  falls back to the source.
- Put breadcrumbs **next to the item they expand** (a rule, a record) and collect the most useful ones in a
  final section.
- A source breadcrumb (`get_file_content_in_range`) is also the cheapest way for Devin to check a span
  against its local checkout.

## 4. App Brief: sections

Each section lists **why Devin needs it**, **what to put in it**, and a short example. The full example is
[`example-app-brief-acas.md`](example-app-brief-acas.md).

### 0. Front matter
- **Why:** identity and drift detection. Devin compares `source.revision` with its checkout before trusting
  any line number.
- **Contents:** `spec_type`, `spec_version`, `project_key`, `cycle`, the source repo and **commit SHA**,
  `generated_at`, `generator`, `content_hash`.
- **Example:** `source: {repo: sigalarm/acas-legacy, revision: <sha>, tag: concho-cycle-22}`

### 1. What the system is
- **Why:** orientation. Devin needs the business purpose and the users to make sensible naming and UX calls.
- **Contents:** 3–6 sentences covering the domain, the users, era and lineage, and scale (programs, lines).
  Don't list features.
- **Example:** "ACAS is a small-business accounting suite (sales, purchase, stock, GL, IRS) written in COBOL
  from the 1970s–90s, now built with GnuCOBOL…"

### 2. System map
- **Why:** lets Devin place the slice and see its neighbours, i.e. what it must not break.
- **Contents:** a table of bounded contexts or modules with their program prefixes, main data stores and
  one-line responsibility, plus a one-line description of the main flows between them. Keep it to the top 10–20.
- **Example:** `| PurchaseLedger | pl0xx–pl9xx | purchled, OTM5 open items | supplier invoices → payments → cash posting |`

### 3. Technology and runtime
- **Why:** this is where Devin lost the most time with no help from Concho: compiler version, storage modes,
  what doesn't build.
- **Contents:**
  - language and dialect
  - compiler and version
  - build entry points
  - storage modes (ISAM / RDBMS) and how they are selected
  - required runtime libraries
  - **known build failures**
  - how to run one program
- **Example:** "GnuCOBOL **3.2** required (3.1.2 fails); `./comp-all-no-rdbms.sh`; `irs.cbl:442` doesn't
  compile on 3.2."

### 4. Data and coding conventions
- **Why:** these apply to every slice and drive the Java mapping.
- **Contents:**
  - copybook naming (`fd*`, `ws*`, `sel*`)
  - numeric storage habits (COMP-3 money, `9(7)V99`)
  - date formats
  - key composition patterns
  - file-status / error-handling idioms
  - the I/O handler layer (e.g. `Purch-Read-Indexed` wrappers)
  - screen I/O style
- **Example:** "File access always goes through handler paragraphs (`OTM5-Read-Next`, `Purch-Rewrite`)
  that work in both ISAM and MySQL mode, so never port the SELECT/FD directly."

### 5. Cross-cutting rules and invariants
- **Why:** rules that span slices and that every slice must honour.
- **Contents:** at most 10 rules in the rule JSON shape (§5.6), each with a status and a breadcrumb.
- **Example:** `PaymentEntryBlockedUntilInvoicesPosted` (posting-flag gates across ledgers).

### 6. Repositories and target platform
- **Why:** tells Devin where to read, where to write, and what "modern" means for this customer.
- **Contents:**
  - legacy repo and revision
  - target repo
  - target stack and versions
  - package / module conventions
  - test frameworks
  - the parity harness location
  - which slices have already been migrated (so Devin can copy their patterns)
- **Example:** "Target: Java 17 / Maven / Spring Boot 3, package `org.acas.<context>.<slice>`. Pattern to
  copy: `sl055` in `org.acas.sales.posting`."

### 7. Working agreements
- **Why:** sets Devin's defaults on ambiguous judgement calls.
- **Contents:**
  - source over spec
  - parity first
  - preserve legacy defects and log them, unless a decision says otherwise
  - how to report discrepancies
  - PR conventions
  - what needs human sign-off
- **Example:** "Port defects bug-for-bug; list each in the PR and `docs/`; a fix is a separate PR."

### 8. Going deeper
- **Why:** the starting points for MCP exploration beyond the slice.
- **Contents:** 5–10 breadcrumbs (project metadata, architecture tree, the bounded contexts list), plus any
  tool usage tips.

### 9. Glossary
- **Why:** legacy systems use terms (folio, appropriation, OTM5, BACS) that LLM priors get wrong.
- **Contents:** each term with a one-line definition, plus the code identifier where relevant.

## 5. Slice Spec: sections

The full example is [`example-slice-spec-pl080.md`](example-slice-spec-pl080.md).

### 0. Front matter
- **Why:** identity, provenance, and links to the App Brief and the previous spec version.
- **Contents:**
  - `slice_id`, `title`
  - `bounded_context`
  - `entry_points[]`
  - `source.revision`
  - `app_brief` reference
  - `target` (stack override if any)
  - `status` (`draft` / `reviewed` / `in-progress` / `done`)
  - `composed_by`, `reviewed_by`
  - `spec_version`, `content_hash`

### 1. Objective and definition of done
- **Why:** tells Devin when it's finished, and what "finished" has to prove.
- **Contents:** one paragraph of outcome, then a checklist of verifiable postconditions (parity fixtures
  pass, UI flows work, docs updated, rule verdicts recorded).
- **Example:** "☐ All scenarios in §9 produce byte-identical supplier and open-item dumps vs legacy pl080."

### 2. Scope
- **Why:** stops scope creep in both directions, and shows where the seams are.
- **Contents:**
  - **In:** programs and behaviours
  - **Out:** explicit exclusions, each with a reason
  - **Adjacent:** slices that read from or write to this one, and the **seam contract** (files and records
    handed over)
- **Example:** "Out: pl090 proof sort. Adjacent: pl100 cash posting reads type-5/6 OTM5 records that this
  slice writes."

### 3. Dependency closure
- **Why:** lets Devin compile the legacy slice for golden capture and know which code to read. On sl055
  this was trial and error.
- **Contents:** a JSON block listing programs, copybooks, called subprograms (static and dynamic), file
  handlers, **unresolved dependencies**, and **things it does *not* call** where a nearby rule suggests it
  might (e.g. `maps09`).

### 4. Data contracts
- **Why:** the largest source of parity bugs and overflow bugs. The Java model and API validation come
  from here.
- **Contents:**
  - a CRUD matrix (program × file/record × operation with spans)
  - for each record touched, the fields this slice reads or writes, with `pic`, `usage`, `signed`,
    `digits`, `scale`, offsets, 88-levels, and a suggested Java type and overflow behaviour
  - **width mismatches flagged** (e.g. a `9(7)V99` input accumulated into a `9(6)V99` total)
  - a breadcrumb to the full layout

### 5. Workflow
- **Why:** gives the order of operations and the state changes, which is what parity tests replay.
- **Contents:** numbered steps, each with its paragraph, span, inputs, outputs and the rule IDs it applies.
  For interactive programs, include the prompt loop (where it re-prompts, what exits).

### 6. Business rules
- **Why:** these become the test list.
- **Contents:** a JSON array. Each rule has:
  - `id`, `concho_entity`, `status`, `programs[]`
  - `statement`
  - `spans[]`
  - `confidence`
  - `acceptance[]` (Given/When/Then, short)
  - `scenarios[]` (IDs from §9)
  - `breadcrumb`

  Keep observed, inferred and proposed rules in **one** list, distinguished by `status`, so Devin can
  filter them.

### 7. User experience
- **Why:** UX slices need both the legacy dialogue (for behaviour) and the target intent (for design).
  These are different things and must not be mixed.
- **Contents:**
  - **7a Legacy dialogue:** the prompt sequence, field positions, validation, messages (text plus code
    such as `PL121`) and keys
  - **7b Target UX intent:** a task description, a layout sketch (ASCII or a link), what must stay
    familiar, and what is deliberately redesigned (e.g. "appropriation preview before commit")
  - **7c Field mapping:** screen field → record field → API field

### 8. Known anomalies and suspected defects
- **Why:** Devin needs to know early whether to reproduce or fix each one. Parity runs found these the
  slow way.
- **Contents:** each item with an ID, description, span, evidence (`static` / `runtime` / `suspected`) and a
  **decision** (`preserve-for-parity` / `fix-in-slice` / `fix-later` / `undecided`).

### 9. Verification plan
- **Why:** turns the spec into proof.
- **Contents:**
  - the parity strategy (what to dump and compare, and the tolerance, normally byte-identical)
  - the harness approach and its location
  - the scenario list in JSON (`id`, intent, setup data, inputs or keystrokes, rules covered, expected
    observable)
  - the golden-capture command
  - non-parity tests (UI, API validation)

### 10. Target design guidance
- **Why:** keeps slices consistent, and records decisions so Devin doesn't re-argue them.
- **Contents:**
  - packages and modules
  - API or interface sketch
  - type-mapping decisions
  - persistence for this slice
  - **decisions** in the form ID, decision, reason, owner
  - patterns to copy from earlier slices

### 11. Open questions
- **Why:** Devin should ask instead of guessing, and know which questions block work.
- **Contents:** a question, why it matters, a default if unanswered, `blocking: true/false`, and an owner.

### 12. Breadcrumbs
- **Why:** a single list of everything worth pulling in, grouped by purpose.
- **Contents:** JSON breadcrumbs grouped as orient / rules / data / source / UX / adjacent slices.

### 13. Deliverables and write-back
- **Why:** closes the loop with the reviewer and with Concho.
- **Contents:**
  - the expected PR shape (what the description must contain: rule IDs covered, defects preserved,
    discrepancies)
  - docs to update
  - the **verification report** format: for each rule `{id, verdict: confirmed|refuted|refined|untested,
    evidence, test}`, plus discrepancies typed as line drift / wrong program / proposed-as-observed /
    missing dependency / runtime contradicts rule
  - where to send it (a PR artifact today; `record_verification` / `report_to_sage` later)

## 6. Composing packages in the Concho web app

Suggested flow:

1. **Pick the anchor:** an entry point, workflow or bounded context. Concho pre-fills the dependency
   closure, CRUD matrix, rules and screens.
2. **Curate:**
   - include or exclude rules
   - set each rule's status (default: what analysis found)
   - attach the UX intent (from a stakeholder review or a mockup upload)
   - record decisions and out-of-scope items
3. **Validate before export:**
   - the source revision is pinned
   - every rule has a span and a status
   - every `proposed` rule is labelled as such
   - the budget meter is under target (tokens per section)
   - open questions have owners
   - breadcrumbs resolve against the cycle
4. **Export:**
   - **Download / copy:** Markdown file(s)
   - **Commit to the target repo:** `specs/app-brief.md` plus `specs/<slice_id>.md`, linked from
     `AGENTS.md` (Devin auto-loads up to 16 KiB of `AGENTS.md`, so the App Brief can be included or linked
     there)
   - **"Open in Devin":** start a session through Devin's API (`POST /v1/sessions` with a kickoff prompt;
     spec files uploaded through the attachments endpoint, or referenced by repo path)
   - **Playbook:** keep the generic "migrate a slice from a Concho spec" procedure as a Devin playbook, so
     the spec only carries slice-specific content
5. **Round-trip:** import Devin's verification report and update rule statuses and confidence. A new
   cycle produces a **spec diff**, not a fresh document.

Kickoff prompt the composer could generate:

```text
Convert slice pl080-payment-entry. Read specs/app-brief.md and specs/pl080-payment-entry.md.
Check source.revision against the acas-legacy checkout first. Treat every rule as a claim to verify
against the source. Follow §13 for the PR and verification report. Ask about blocking open questions (§11)
before implementing the parts they affect.
```

## 7. Sections ranked by value (if the first version must be small)

1. Front matter with source revision; rules with status, spans and breadcrumbs; scope; definition of done
2. Data contracts with PIC → Java mapping and width-mismatch flags; dependency closure
3. Verification scenarios; known anomalies with decisions
4. UX (legacy dialogue + target intent); target design decisions
5. Glossary, going deeper, the write-back format
