---
spec_type: app-brief
spec_version: 1
project_key: acas
cycle: 22
source:
  repo: sigalarm/acas-legacy
  revision: c601a4fead550bae7adbe1427d4a05a0a894c0aa
  tag: concho-cycle-22
generated_at: 2026-10-02
generator: hand-composed example of a future Concho export
content_hash: sha256:<computed at export>
---

# App Brief: ACAS (Applewood Computers Accounting System)

> Example built by hand from this repo's verified work, showing what Concho could generate. Facts marked
> `observed` were checked against the source revision above.

## 1. What the system is

ACAS is an accounting suite for small and medium businesses: sales ledger, purchase ledger, stock control,
general ledger and IRS (an incomplete-records ledger). It started in the 1970s–90s. Users are bookkeepers
working in text-terminal screens and running batch postings and reports. It has about 150 COBOL programs;
Concho analysed 1.36M lines of input, including manuals and the SQL preprocessor.

## 2. System map

| Bounded context (Concho) | Programs | Main data | Responsibility |
|---|---|---|---|
| SalesLedger | `sl0xx`–`sl9xx` | customer master, OTM3 open items | invoices → posting → receipts |
| PurchaseLedger | `pl0xx`–`pl9xx` | `purchled` supplier master, OTM5 open items | supplier invoices → payments → cash posting |
| GeneralLedger | `gl0xx` | GL ledger, batch files | nominal accounts, batch posting |
| StockControl | `st0xx` | stock items | stock CRUD, valuation |
| IRS | `irs*` | IRS ledger | incomplete-records accounting |
| DualStorageArchitecture | `acas0xx` handlers, `*MT` modules | ISAM or MySQL | file-handler layer used by every program |

Main flows: invoice entry → proof → posting into the ledger and GL. Payments run entry (`pl080`) → proof
(`pl090`/`pl095`) → cash posting (`pl100`).

## 3. Technology and runtime

- COBOL built with **GnuCOBOL 3.2** (`observed`: Ubuntu's 3.1.2 doesn't work). Build it from source into
  `/opt/gnucobol-3.2`.
- Build: `./comp-all-no-rdbms.sh` (ISAM only) compiles about 150 programs in about 40 s.
- Storage: ISAM by default; MySQL/MariaDB is optional, through the same handler paragraphs.
- Known build failures (`observed`):
  - `irs/irs.cbl:442` (screen-section `UPPER`)
  - `sales` main program (`accept_numeric.c` vs `common/ACCEPT_NUMERIC.C`)
  - `sl970` (missing `an-accept.pl`)
- Interactive programs use SCREEN I/O, so drive them with a pty harness (pattern:
  `legacy-harness/drive-pl080.py`).

## 4. Data and coding conventions

- Copybooks: `fd*.cob` (file records), `ws*.cob` (working storage), `sel*.cob` (SELECTs), `plwsoi*.cob`
  (purchase open items).
- Money is mostly `COMP-3 s9(7)v99`. **Accumulators are sometimes narrower than inputs**, so check widths
  before choosing Java types.
- Dates are stored as binary day numbers. `maps04` validates and converts `dd/mm/ccyy`.
- Keys are composite (e.g. OTM5 = 7-char supplier + 8-digit invoice). Payment references are encoded as
  `batch × 1000 + item`.
- All file I/O goes through handler paragraphs (`Purch-Read-Indexed`, `OTM5-Rewrite`, ...). Port the
  semantics of the handler, not the SELECT/FD. A rewrite takes its data from `WS-OTM5-Record`, so a missing
  `move OI-Header to WS-OTM5-Record` is a real defect class.
- Messages have codes (`PL121`, `PL116`, ...) defined in working storage.

## 5. Cross-cutting rules

```json
[
  {"id": "X-POSTING-GATE", "concho_entity": "PaymentEntryBlockedUntilInvoicesPosted", "status": "observed",
   "programs": ["pl080", "sl080"],
   "statement": "Payment entry is refused while the ledger's invoices-entered-not-posted flag is set.",
   "spans": ["purchase/pl080.cbl:288-292"]},
  {"id": "X-BATCH-REF", "concho_entity": "PaymentBatchEncodingAndRolloverRule", "status": "observed",
   "programs": ["pl080", "sl080"],
   "statement": "Payment open-item invoice number = batch number × 1000 + item; at most 999 items per batch.",
   "spans": ["purchase/pl080.cbl:371-373", "purchase/pl080.cbl:731-733"]},
  {"id": "X-DOUBLE-ENTRY", "concho_entity": "DoubleEntryPaymentPostingRule", "status": "inferred",
   "programs": ["pl100", "sl100", "gl*"],
   "statement": "GL postings are double-entry, routed to GL or IRS by the irs-used flag.",
   "spans": []}
]
```

## 6. Repositories and target platform

- Legacy (read-only, authoritative): `sigalarm/acas-legacy` @ the revision in the front matter.
- Target: `sigalarm/acas-modern`.
  - Java 17, Maven, Spring Boot 3; React 18 + Vite for UI slices
  - Package `org.acas.<context>.<slice>`
- Parity harness:
  - `legacy-harness/` (COBOL driver programs plus capture scripts)
  - fixtures in `src/test/resources/parity-*/`
- Migrated slices to copy patterns from:
  - `sl055` (batch, `org.acas.sales.posting`)
  - `pl080` (UI, `org.acas.purchase.payment`)

## 7. Working agreements

- Source and observed runtime beat this brief and the Slice Spec. Report any disagreement; don't silently
  work around it.
- Parity first: match the unmodified legacy outputs byte for byte. Port legacy defects deliberately and list
  each one in the PR and in `docs/`. A fix is a separate, reviewed change.
- `proposed` rules are not legacy behaviour. Implement them only if the Slice Spec's decisions say so.
- Exploratory phase: no CI, auth or deployment work unless the Slice Spec asks for it.

## 8. Going deeper

```json
[
  {"why": "Project profile and analysed cycle", "tool": "get_project_metadata",
   "args": {"project_key": "acas", "cycle": 22}},
  {"why": "Layer tree with line counts", "tool": "get_project_architecture_tree_and_line_count",
   "args": {"project_key": "acas", "cycle": 22}},
  {"why": "All bounded contexts", "tool": "search_entities",
   "args": {"project_key": "acas", "cycle": 22, "entity_type": "bounded_context", "limit": 20}},
  {"why": "Screen entry points for a module", "tool": "search_entities",
   "args": {"project_key": "acas", "cycle": 22, "entity_type": "entry_point", "name_contains": "Payment"}}
]
```

## 9. Glossary

| Term | Meaning |
|---|---|
| Appropriation | Allocating a payment across a supplier's open invoices (`approp-amount`) |
| OTM5 / OTM3 | Purchase / sales open-item files (invoices, payments, credit notes) |
| Unapplied | Supplier credit not yet allocated to invoices (`purch-unapplied`) |
| Deduct days / amount | Early-payment discount terms on an invoice |
| BACS | UK electronic bank payment (vs cheque) |
| Posting | Batch step that moves proven transactions into the ledger and GL |
