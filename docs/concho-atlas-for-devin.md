# Concho as a legacy-system atlas for Devin: what would speed up COBOL → Java

This is based on two Devin + Concho slices of ACAS: `sl055`, a batch posting extract ported to Java, and `pl080`,
interactive Payment Data Entry ported to a Spring Boot API + React UI. Both were checked against the unmodified
COBOL running under GnuCOBOL 3.2. The detailed write-ups are `docs/concho-usefulness-sl055.md` and
`docs/concho-usefulness-pl080.md`.

**Bottom line:** today Concho is a very good *atlas for orienting*: which slice, which programs, which rules and where
they live. Devin's time in both slices went into what an atlas doesn't yet record: exact data layouts, the
precise control and data flow inside a program, how files and records behave at runtime, how to build and run
the system, and turning rules into fixtures. Those are the gaps to close. The rule from the start still holds:
**the checked-out source and the observed runtime are the truth; Concho is the map.** So everything below is about
making the map more precise, more traceable to source, and quicker to check against it, not about Concho replacing
the source.

---

## 1. Where the time actually went

| Activity in the slices | Concho's contribution today | What was missing |
|---|---|---|
| Choosing and bounding the slice | **High.** Workflows such as `SalesInvoicePostingPipeline` and the payment-processing review gave the slice, the programs involved and the operator journey | Program dependency closure (calls, copybooks, files) as a list Devin can act on |
| Finding the code | **High.** `search_entities` → `get_entity` → `get_file_content_in_range`, with cycle-22 line refs that matched the checkout | The source revision analysed; exported reports (cycle 8) drifted |
| Drafting tests | **Medium-high.** Rules and workflow states became the fixture list | Rules weren't scoped to a program, and observed and proposed behaviour were mixed (MOD-11 attributed to pl080) |
| Getting byte-for-byte output parity | **Low** | PIC/COMP-3 layouts, truncation and overflow, paragraph-level flow, what a failed READ leaves in the record |
| Building and running legacy code | **None** | Compiler and dialect, build order, runtime dependencies, programs known not to compile |
| Building the UI | **High** (stakeholder review mockups and field list) | Screen-section layouts tied to the fields they read and write |
| Hardening the port | **None** | Field-width mismatches (input `9(7)V99` vs accumulator `9(6)V99`), key-space collisions, silent write failures. Automated code review found these, not the model |

Recurring pattern: **every behaviour the parity tests pin down came from the source or from a run**, often a
latent defect the rule model described as intended behaviour, e.g.:
- sl055 posts to key `"   "` after a failed READ;
- pl080 rewrites an unchanged buffer, so an already-paid invoice never closes;
- answering N to "settle in full" still closes the invoice.

---

## 2. Datasets: the highest-value additions, in priority order

### Tier 1: these make parity work dramatically faster

1. **Data dictionary resolved through copybooks (record layouts).** For every 01/FD/SD record: each field's
   level, name, `PIC`, `USAGE` (DISPLAY/COMP/COMP-3/COMP-5), signedness, digits and scale, byte offset and length,
   `REDEFINES`, `OCCURS` (and `DEPENDING ON`), 88-level condition names with their values, and the copybook and
   line each came from.
   - *Why:* this is most of what a Java port needs. It gives `BigDecimal` scale, overflow limits, fixed-width
     serialisation and enums from 88-levels. The pl080 million-pound wrap was a width mismatch that would have
     been obvious from it.
   - *Bonus:* emit a suggested Java mapping per field (`BigDecimal(7,2)`, unsigned, truncates on overflow, with
     ROUNDED or not).
2. **Program call graph and dependency closure.** Static `CALL` targets, dynamic `CALL` with likely values,
   `COPY` includes (with `REPLACING`), and which entry points reach which programs. Mark **unresolved dependencies**
   explicitly (e.g. `sl970` → missing `an-accept.pl`).
   - *Why:* "pl080 never calls `maps09`" took a manual grep. With a call graph, an agent could tell at once that
     MOD-11 isn't legacy behaviour, and could size and compile a slice without trial and error.
3. **File and record CRUD matrix.** For each program, the files, records and keys it opens, reads, writes,
   rewrites or deletes, with the paragraph and line, plus the file organisation (indexed/relative/sequential),
   primary and alternate keys, and file status handling.
   - *Why:* this defines slice boundaries (sl055 → `openitm2` → sl060), fixture shape, and what parity has to
     compare. It would also have pointed to the pl080 payment-reference key collision (payments and invoices
     share the open-item key space).
4. **Paragraph-level control and data flow.** The PERFORM graph, GO TO edges, and for each paragraph the fields
   it reads and writes and the conditions guarding it. Attach rules to the **exact paragraph and line where
   state changes** (e.g. where `oi-status` is set, where `il-update = "Z"` is set).
   - *Why:* the defects we found were all about exact flow: which buffer is rewritten, and what runs after
     `end-line`.
5. **Rule provenance and scope.** For each business rule:
   - **status**: `observed-in-code` / `inferred` / `proposed-for-modernization`;
   - the program(s) it applies to;
   - an exact source span and a content hash;
   - the cycle and source revision;
   - a fidelity statement that can't say "verbatim" for a rule that isn't in the code.
   - *Why:* most of the pl080 checking was untangling proposed (MOD-11), cross-program (pl080 vs pl090 record
     types) and mis-attributed (99 vs 999 items) rules.

### Tier 2: these turn discovery into executable specs

6. **Screen and dialogue inventory.** For each SCREEN SECTION / `DISPLAY ... AT` / `ACCEPT ... AT`: position,
   prompt text, the field bound, its validation, the error message, and the order of prompts, including re-prompt
   loops and function keys. The stakeholder review had a hand-made version of this, and it was the biggest time
   saver for the UI.
7. **Message catalogue.** Every literal shown to the user (`PL121 Invoices Not Posted…`), with its trigger
   condition and program/line. These become UI copy and assertions directly.
8. **Candidate test scenarios per rule.** For each rule, the input conditions that exercise it (boundary
   values, both branches), in a structured form Devin can turn into fixtures and drive keystrokes from.
9. **Suspicious-pattern findings** (latent-defect hints): REWRITE from a buffer other than the one last read,
   READ with no INVALID KEY / file status check, a numeric MOVE into a narrower PIC, unchecked WRITE status,
   arithmetic without `ON SIZE ERROR`, uninitialised working storage used before it is set.
   - *Why:* these are exactly the defects parity uncovered the slow way. Flagging them up front lets the agent
     decide early whether to preserve or fix each one.

### Tier 3: operations and traceability

10. **Build and run recipe.** Compiler and dialect (GnuCOBOL version, `-std`, flags), compile order, the
    module-versus-executable split, required runtime (ISAM handler, BDB, MySQL), environment variables, and
    known non-compiling units (`irs.cbl:442` `UPPER` on 3.2). This could feed Devin's environment setup
    (blueprint) directly.
11. **Sample data and data profiles.** Representative (or synthetic) records per file, value ranges and
    distributions, so fixtures look like production data without anyone hand-writing pipe-delimited rows.
12. **Analysed source revision.** A commit SHA or content hash for every cycle and every exported artifact. This
    is the simplest change with the highest return: an agent can then detect drift instead of discovering it.
13. **Legacy → modern traceability.** Index the *target* repo too, and keep links from each rule and paragraph to
    the Java class/method and the test that covers it. That gives migration coverage ("412 of 525 rules have a
    covering parity test") and makes reviews concrete.

---

## 3. Tools: MCP surface changes that would help Devin most

Devin used these existing tools heavily: `find_project`, `search_entities`, `get_entity` and
`get_file_content_in_range`. The suggestions below either extend that path or remove friction from it.

### New tools

| Proposed tool | Returns | Replaces today's work of |
|---|---|---|
| `get_slice_packet(entry_point \| workflow)` | In **one** response: dependency closure, files/records touched, record layouts, rules scoped to those programs (with status and spans), screens and messages, the build recipe and suggested fixtures | Dozens of calls plus manual grep |
| `get_record_layout(record \| copybook)` | The resolved field tree with PIC, usage, offsets and a Java type mapping | Hand-reading copybooks for every field |
| `get_call_graph(program, depth)` / `get_dependency_closure(program)` | Calls, copybooks, unresolved items | grep, plus compile-and-fail |
| `get_file_access(program \| file)` | The CRUD matrix with paragraph and line | Reading every OPEN/READ/WRITE |
| `get_paragraph(program, paragraph)` | Source span, PERFORM in/out edges, fields read and written, the rules anchored there | Navigating GO TO spaghetti |
| `get_screen(program)` | The prompt sequence, field bindings, validations, messages | Reconstructing the dialogue from DISPLAY/ACCEPT |
| `verify_reference(entity, file_sha \| content)` | Whether the entity's span still matches the given source, and where it moved | Manual drift checking |
| `get_cycle_diff(from, to)` | Entities added, removed or changed between cycles | Re-reading everything after re-analysis |
| `record_verification(entity, verdict, evidence)` | Writes the agent's verdict back: `confirmed` / `refuted` / `refined`, with file:line and test name | Unstructured `report_to_sage` text |

### Changes to existing tools

- **Every entity carries stable IDs, the source revision and a content hash.** IDs should survive re-analysis
  so Devin's commits, tests and PR descriptions can cite them.
- **Rule records get `status`, `programs[]` and `spans[]`** (see Tier 1 #5), and `search_entities` filters on
  them (e.g. "observed rules in pl080").
- **Size-aware responses by default.** Metadata, architecture-tree and artifact listings overflowed inline
  output. Default to summaries with cursors, and offer a `fields=` projection.
- **`get_guide_for_tool` should cover all 30 tools** (today it accepts 7), or the guidance should move into
  each tool's description.
- **Make `report_to_sage` structured.** Use a discrepancy type (line drift, wrong program, proposed-as-observed,
  missing dependency, runtime contradicts rule) plus evidence, so agent feedback can be aggregated and fed into
  the next cycle.
- **Machine-readable output** (JSON with stable keys) alongside Markdown for anything an agent will turn into
  code or tests.

---

## 4. How this plugs into Devin

- **MCP**: everything above, served by Concho's hosted HTTP endpoint with per-user OAuth, as today.
- **Playbook**: a reusable Devin playbook "migrate one COBOL slice with Concho":
  1. `get_slice_packet`
  2. pin the source revision
  3. build the legacy code with the recipe
  4. generate fixtures from the scenarios
  5. capture golden output from the legacy binary
  6. port to Java
  7. reach byte parity
  8. call `record_verification` for every rule
  9. open a PR that cites the rule IDs

  Steps 1, 3, 5 and 8 are where better Concho data removes the most effort.
- **Environment (blueprint)**: generate it from the build/run recipe (Tier 3 #10).
- **Knowledge**: export project-wide conventions (dialect quirks, file handler semantics, numeric mapping
  rules) as short repo notes Devin loads automatically.
- **Parallel sessions**: with slice packets and a dependency closure, slices whose closures don't overlap can be
  handed to separate Devin sessions with little risk of collision.

---

## 5. Suggested roadmap

| Order | Item | Effort (Concho side, guess) | Impact on Devin |
|---|---|---|---|
| 1 | Source revision and content hash on cycles, entities and artifacts | Small | Ends the drift guesswork; makes everything else verifiable |
| 2 | Rule `status` / `programs[]` / `spans[]` | Small–medium | Removes most of the rule-checking churn |
| 3 | Resolved record layouts + Java type mapping | Medium | Biggest single accelerator for parity |
| 4 | Call graph + CRUD matrix + unresolved dependencies | Medium | Slice sizing, harness building, parallel planning |
| 5 | `get_slice_packet` composed from 1–4 | Small once 1–4 exist | Fewer calls and tokens; a natural playbook step |
| 6 | Structured `report_to_sage` / `record_verification` | Small | Closes the loop and gives a measurable quality signal for Concho |
| 7 | Paragraph-level flow + suspicious-pattern findings | Medium–large | Finds the latent defects before the parity runs do |
| 8 | Screen/dialogue inventory + message catalogue | Medium | UX slices without hand-made reviews |
| 9 | Build/run recipe → blueprint; sample data profiles | Medium | Faster setup on new customer codebases |
| 10 | Target-repo indexing and traceability | Large | Coverage dashboards and auditable migrations |

## 6. How to measure whether Concho is speeding Devin up

Run the same slice with and without each improvement and compare:
- Concho calls and tokens per slice;
- wall-clock time to first passing parity fixture;
- the number of manual greps/reads Devin needs to make;
- rules refuted or refined during verification, per 100 rules (a Concho accuracy metric fed by
  `record_verification`);
- latent defects found by suspicious-pattern hints compared with defects found by parity runs;
- the share of slice PRs that need no reviewer correction to business-rule interpretation.

ACAS is a good benchmark for this: it compiles and runs under GnuCOBOL, so every claim can be checked against a
real execution.
