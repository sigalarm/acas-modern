---
spec_type: slice-spec
spec_version: 1
slice_id: pl080-payment-entry
title: Purchase Payment Data Entry
project_key: acas
cycle: 22
bounded_context: PurchaseLedger
entry_points: [PurchasePaymentDataEntry]   # Concho entry_point entity; program pl080
workflow: PurchasePaymentProcessing         # parent workflow; this slice is its first step
source:
  repo: sigalarm/acas-legacy
  revision: c601a4fead550bae7adbe1427d4a05a0a894c0aa
app_brief: specs/app-brief.md
target: {stack: "Java 17, Spring Boot 3 API, React 18 UI", package: org.acas.purchase.payment}
status: reviewed
composed_by: concho-web (example)
reviewed_by: <human reviewer>
content_hash: sha256:<computed at export>
---

# Slice Spec: pl080 Purchase Payment Data Entry

> Example of a future Concho export, built from the verified pl080 work in this repo (the implementation
> merged in https://github.com/sigalarm/acas-modern/pull/3). In a real package, §8 and parts of §6 would
> start as `suspected` / `inferred` and be updated by Devin's write-back.

## 1. Objective and definition of done

Replace pl080's terminal dialogue with a web screen. The operator enters a supplier payment and sees the
**appropriation across open invoices before committing**. The resulting supplier and open-item records
must be identical to what legacy pl080 writes.

- [ ] Every scenario in §9 produces byte-identical `purchled` + OTM5 dumps vs. legacy `pl080` (harness run)
- [ ] Every `observed` rule in §6 has at least one passing test that cites its ID
- [ ] UI flows in §7b work end to end in a browser
- [ ] Each §8 anomaly is handled as its decision says, and listed in the PR
- [ ] Verification report (§13) committed under `docs/`

## 2. Scope

- **In:**
  - posting gate
  - batch numbering and the batch-full limit
  - payment date and supplier entry
  - payment value
  - unapplied-credit allocation
  - appropriation across open items (discount, partial, settle-in-full prompt)
  - payment open-item write
  - leaving entry (batch advance)
- **Out:**
  - `pl085` amendment
  - `pl090`/`pl095` proof
  - `pl100` cash posting and GL
  - `pl9xx` cheque/BACS
  - MySQL mode
  - auth, persistence beyond an in-memory ledger, deployment
- **Adjacent seams:**
  - `pl100` reads the type-5/6 OTM5 records this slice writes, and the `oi-paid`/`oi-status` updates on
    type-2 invoices. Their layout must stay legacy-compatible.
  - `pl085` reverses them (`PaymentReversalRule`).

## 3. Dependency closure

```json
{
  "programs": ["purchase/pl080.cbl"],
  "copybooks": ["envdiv.cob", "wsmaps03.cob", "wsfnctn.cob", "wspl.cob", "plwsoi5B.cob", "plwsoi.cob",
                "screenio.cpy", "Test-Data-Flags.cob", "wscall.cob", "wssystem.cob", "wsnames.cob",
                "fdpl.cob"],
  "calls_static": ["maps04 (date validate/convert)", "CBL_CHECK_FILE_EXIST"],
  "file_handlers": ["Purch-Open/Read-Indexed/Rewrite/Close", "OTM5-Open/Start/Read-Next/Write/Rewrite/Close"],
  "not_called": [{"program": "maps09", "note": "No MOD-11 check in pl080, despite the review's BR-PAY-002"}],
  "unresolved": []
}
```

Breadcrumb (proposed tool): `get_dependency_closure {project_key: "acas", cycle: 22, program: "pl080"}`.
Fallback: `grep -n 'copy \|call ' purchase/pl080.cbl`.

## 4. Data contracts

**CRUD matrix**

| File / record | Op | Where | Notes |
|---|---|---|---|
| system record | R | entry | `P-Flag-I` gate; `BL-Next-Batch` |
| system record | U | 325 | `BL-Next-Batch` advances when leaving entry |
| `purchled` (supplier) | R | 386–388 | key = 7-char supplier, uppercased |
| `purchled` (supplier) | U | 458–459 | when unapplied credit is allocated, `purch-unapplied` goes down |
| OTM5 type-2 invoices | R | 543–548 | start at supplier key, read next |
| OTM5 type-2 invoices | U | 570, 694 | rewrites `oi-paid`, `oi-p-c`, `oi-cr`, `oi-status`, `oi-date-cleared` |
| OTM5 type 5 (payment) / 6 (unapplied) | C | 725–737 | key invoice = batch × 1000 + item |

**Fields this slice touches**

```json
[
  {"record": "OI-Header (plwsoi.cob)", "field": "OI-Invoice", "pic": "9(8)", "usage": "display", "java": "long", "note": "payment ref = batch*1000+item"},
  {"record": "OI-Header", "field": "OI-B-Nos", "pic": "9(5)", "usage": "comp", "java": "int"},
  {"record": "OI-Header", "field": "OI-B-Item", "pic": "999", "usage": "comp", "java": "int"},
  {"record": "OI-Header", "field": "OI-Paid", "pic": "s9(7)v99", "usage": "comp-3", "java": "BigDecimal(9,2)"},
  {"record": "OI-Header", "field": "OI-Approp", "pic": "s9(7)v99", "usage": "comp-3", "redefines": "OI-Net", "java": "BigDecimal(9,2)"},
  {"record": "OI-Header", "field": "OI-Deduct-Amt", "pic": "s999v99", "usage": "comp", "java": "BigDecimal(5,2)"},
  {"record": "OI-Header", "field": "OI-Deduct-Days", "usage": "binary-char", "java": "int"},
  {"record": "OI-Header", "field": "OI-CR", "usage": "binary-long", "java": "int", "note": "last payment × 100"},
  {"record": "OI-Header", "field": "OI-Status", "pic": "9", "levels88": {"S-Open": 0, "S-Closed": 1}, "java": "int"},
  {"record": "purch-record (fdpl.cob)", "field": "purch-unapplied", "pic": "s9(8)v99", "usage": "comp-3", "java": "BigDecimal(10,2)"},
  {"record": "WS (pl080)", "field": "pay-value", "pic": "9(7)v99", "usage": "comp-3", "java": "BigDecimal(9,2)"},
  {"record": "WS (pl080)", "field": "approp-amount", "pic": "9(6)v99", "usage": "comp-3", "java": "BigDecimal(8,2), truncating",
   "width_mismatch": "accumulates pay-paid values up to 9(7)v99; totals of £1,000,000 or more wrap (see A-4)"},
  {"record": "WS (pl080)", "field": "k", "pic": "999", "java": "int", "note": "batch item counter; full at 999"}
]
```

Breadcrumb (proposed tool): `get_record_layout {project_key: "acas", cycle: 22, record: "OI-Header"}`.
Today: `get_file_content_in_range {file_path: "copybooks/plwsoi.cob", start_line: 1, num_lines: 80}`.

## 5. Workflow

1. **Posting gate** (288–292). If `P-Flag-I = 1`: show `PL121`, wait, exit. No file is touched. → `R-GATE`
2. **Open files.**
3. **Batch item** (482–487). If the batch number is zero it becomes 1, then `k += 1`. → `R-BATCH`
4. **Batch full** (371–373). If `k = 999`: show "Batch Closed........Full!" and exit. → `R-BATCH`
5. **Date.** Accepted as `dd/mm/ccyy` and checked by `maps04`. An invalid date re-prompts. → `R-DATE`
6. **Supplier** (376–388). Accepted, uppercased, read by key. If unknown, re-prompt. → `R-SUPPLIER`
7. **Value / unapplied.**
   - If the supplier has unapplied credit, the operator may allocate it, up to `purch-unapplied`; this
     gives type 6.
   - Otherwise the operator enters the payment value; this gives type 5.
   - → `R-UNAPPLIED`
8. **Appropriate** (531–700). Walk the OTM5 type-2 items for this supplier in key order, skipping
   batched (`oi-b-nos ≠ 0`) and already-settled items. For each item:
   - compute the amount outstanding, applying the discount window → `R-DISCOUNT`
   - offer that amount; the operator may override it
   - add it to `approp-amount`; reject the line if the total exceeds `pay-value` → `R-TOO-HIGH`
   - settle-in-full prompt → `R-SETTLE`
   - update and rewrite the item
9. **Write the payment** (725–737). Write a type-5/6 OTM5 record with the encoded reference. → `R-PAYREF`
10. **More payments?** If Y, loop to step 3. If N, leave entry, which advances the batch number (325). → `R-BATCH`

## 6. Business rules

```json
[
  {"id": "R-GATE", "concho_entity": "PaymentEntryBlockedUntilInvoicesPosted", "status": "observed",
   "programs": ["pl080"], "spans": ["purchase/pl080.cbl:288-292"],
   "statement": "If P-Flag-I = 1, show 'PL121 Invoices Not Posted; Payment Entry Not Allowed' and exit without touching any file.",
   "acceptance": ["Given P-Flag-I=1 When entry starts Then PL121 is shown and the ledger is unchanged"],
   "scenarios": ["S-GATE"],
   "breadcrumb": {"tool": "get_entity", "args": {"project_key": "acas", "cycle": 22, "entity_type": "business_rule", "entity_name": "PaymentEntryBlockedUntilInvoicesPosted"}}},
  {"id": "R-BATCH", "concho_entity": "PaymentBatchEncodingAndRolloverRule", "status": "observed",
   "programs": ["pl080"], "spans": ["purchase/pl080.cbl:371-373", "purchase/pl080.cbl:482-487"],
   "statement": "Batch number 0 initialises to 1; the item counter k (pic 999) increments per payment; at 999 the batch is full; leaving entry advances the batch.",
   "acceptance": ["Given BL-Next-Batch=0 When the first payment is entered Then it is batch 1 item 1"],
   "scenarios": ["S-UNAPPLIED-BATCH"],
   "breadcrumb": {"tool": "get_entity", "args": {"project_key": "acas", "cycle": 22, "entity_type": "business_rule", "entity_name": "PaymentBatchEncodingAndRolloverRule"}}},
  {"id": "R-PAYREF", "concho_entity": "PaymentBatchEncodingAndRolloverRule", "status": "observed",
   "programs": ["pl080"], "spans": ["purchase/pl080.cbl:731-733"],
   "statement": "Payment open-item OI-Invoice = batch × 1000 + item; type 5 for payments, 6 for unapplied allocations.",
   "acceptance": ["Given batch 42 item 1 When saved Then OI-Invoice = 42001"],
   "scenarios": ["S-DISCOUNT-FULL", "S-UNAPPLIED-BATCH"]},
  {"id": "R-SUPPLIER", "concho_entity": null, "status": "observed",
   "programs": ["pl080"], "spans": ["purchase/pl080.cbl:376-388"],
   "statement": "Supplier key is 7 characters, uppercased, read from purchled; an unknown key re-prompts. No MOD-11 check.",
   "acceptance": ["Given NOSUCH1 then acme001 When entered Then ACME001 is selected after one re-prompt"],
   "scenarios": ["S-UNAPPLIED-BATCH"]},
  {"id": "R-DATE", "concho_entity": null, "status": "observed",
   "programs": ["pl080", "maps04"], "spans": [],
   "statement": "Payment date is dd/mm/ccyy validated by maps04; an invalid date re-prompts.",
   "scenarios": ["S-UNAPPLIED-BATCH"]},
  {"id": "R-DISCOUNT", "concho_entity": "EarlyPaymentDiscountWindowEnforcement", "status": "observed",
   "programs": ["pl080"], "spans": ["purchase/pl080.cbl:591-597"],
   "statement": "Discount applies only if invoice date + deduct days + 1 > payment date (strictly).",
   "acceptance": ["Given invoice day D, 10 deduct days When paid on D+10 Then discount taken; on D+11 Then not"],
   "scenarios": ["S-DISCOUNT-FULL"],
   "breadcrumb": {"tool": "get_entity", "args": {"project_key": "acas", "cycle": 22, "entity_type": "business_rule", "entity_name": "EarlyPaymentDiscountWindowEnforcement"}}},
  {"id": "R-TOO-HIGH", "concho_entity": null, "status": "observed",
   "programs": ["pl080"], "spans": ["purchase/pl080.cbl:639-642"],
   "statement": "If the cumulative appropriation exceeds the payment value, the line is rejected and re-offered.",
   "scenarios": ["S-PARTIAL-OVERRIDE"]},
  {"id": "R-SETTLE", "concho_entity": null, "status": "observed",
   "programs": ["pl080"], "spans": ["purchase/pl080.cbl:644-689"],
   "statement": "A part-payment prompts 'settle in full?'; Y clears the item and takes the discount. See A-3 for N.",
   "scenarios": ["S-PARTIAL-OVERRIDE"]},
  {"id": "R-UNAPPLIED", "concho_entity": null, "status": "observed",
   "programs": ["pl080"], "spans": ["purchase/pl080.cbl:440-470"],
   "statement": "An unapplied allocation cannot exceed purch-unapplied, reduces it, and is written as type 6.",
   "scenarios": ["S-UNAPPLIED-BATCH"]},
  {"id": "R-MOD11", "concho_entity": null, "status": "proposed",
   "programs": [], "spans": [],
   "statement": "Stakeholder review BR-PAY-002: validate the supplier MOD-11 check digit inline. NOT legacy behaviour.",
   "decision": "D-2"}
]
```

## 7. User experience

**7a Legacy dialogue (observed)**
- Prompt sequence: date (`dd/mm/ccyy`) → supplier at `0572` → value → per-invoice line with the offered
  amount and a Y/N settle prompt at column 65 → "Enter further payments? (Y/N) [Y]".
- Messages: `PL121`, `PL116 Note: and hit return to continue`, `PL119`, "Batch Closed........Full!".
- The screen shows the batch number and item at 0770/0776, and the running batch total at 1041/1055.

**7b Target UX intent**
- One page:
  - batch bar (batch no., items, total, close)
  - left: payment form (date, supplier lookup with name and balance, amount, "use unapplied credit")
  - right: open-invoice table showing the proposed appropriation, with an editable pay amount and a settle
    checkbox for each line
  - Preview, then Save
- Keep: the order of invoices, the offered amounts, and the settle semantics.
- Redesigned on purpose:
  - the whole appropriation is visible and editable **before** commit, instead of line-by-line prompts
  - errors appear inline

**7c Field mapping**

| Screen field | Record / WS field | API field |
|---|---|---|
| Date | `pay-date` → `OI-Date` | `date` |
| Supplier | `pay-customer` → `OI-Supplier` | `supplier` |
| Amount | `pay-value` → `OI-Paid` (type 5/6) | `amount` |
| Use unapplied | type 6, `purch-unapplied` | `allocateUnapplied` |
| Line pay | `pay-paid` → invoice `OI-Paid` += | `lines[].amount` |
| Line settle | `ws-reply` Y/N | `lines[].settle` |

## 8. Known anomalies

| ID | Description | Span | Evidence | Decision |
|---|---|---|---|---|
| A-1 | An already-paid invoice is marked cleared, but the rewrite uses the stale `WS-OTM5-Record`, so it never closes | 567–571 | runtime | preserve-for-parity |
| A-2 | A generated payment ref can collide with an existing OTM5 invoice for the same supplier (e.g. 42001) | 731–737 | runtime | fix-in-slice: reject in preview (D-3) |
| A-3 | Answering N to "settle in full" can still clear the item in `end-line` when paid = net minus deduction | 676–689 | runtime | preserve-for-parity |
| A-4 | `approp-amount 9(6)v99` wraps at £1,000,000 | 196, 639 | static + runtime | fix-in-slice: cap amount at 999,999.99 (D-4) |

## 9. Verification plan

- **Parity:** run the same fixture through the legacy `pl080` (via harness) and through the Java code.
  Compare dumps of `purchled` and OTM5 byte for byte.
- **Harness:**
  - `legacy-harness/pl080h.cbl` (seeds the files and dumps them)
  - `drive-pl080.py` (pty keystrokes)
  - `capture-golden-pl080.sh` (writes `src/test/resources/parity-pl080/*.expected`)

```json
[
  {"id": "S-DISCOUNT-FULL", "intent": "Full payment inside the discount window", "rules": ["R-DISCOUNT", "R-PAYREF"]},
  {"id": "S-PARTIAL-OVERRIDE", "intent": "Operator overrides a line; too-high rejection; settle prompt", "rules": ["R-TOO-HIGH", "R-SETTLE"]},
  {"id": "S-SKIP-RULES", "intent": "Only unbatched type-2 items for the supplier with something outstanding are offered", "rules": ["R-TOO-HIGH"]},
  {"id": "S-UNAPPLIED-BATCH", "intent": "Invalid date, unknown + lowercase supplier, over-limit unapplied allocation, batch 0→1", "rules": ["R-DATE", "R-SUPPLIER", "R-UNAPPLIED", "R-BATCH"]},
  {"id": "S-GATE", "intent": "P-Flag-I=1 blocks entry", "rules": ["R-GATE"]}
]
```

- **Non-parity tests:**
  - API validation (fractional pence, over-limit amounts, stale preview)
  - UI tests
  - browser end-to-end flow

## 10. Target design guidance

- Domain in `org.acas.purchase.payment` (`Appropriation`, `PurchaseLedger`, `OpenItem`). Web in
  `...payment.web`.
- API:
  - `GET /api/batch`
  - `GET /api/suppliers/{acct}`
  - `GET /api/suppliers/{acct}/open-items`
  - `POST /api/payments/preview`
  - `POST /api/payments`
  - `POST /api/batch/close`
- Money is `BigDecimal` at scale 2, with an explicit `Fixed.signed(value, digits)` helper to mirror COMP-3
  truncation.

| ID | Decision | Reason | Owner |
|---|---|---|---|
| D-1 | Spring Boot + React, deliberately not the review's FastAPI/PostgreSQL | Test stack independence | Bruce |
| D-2 | Don't implement MOD-11 (R-MOD11) | Not legacy behaviour; would be a new rule | Bruce |
| D-3 | Reject duplicate payment refs in preview | Silent data loss otherwise | Reviewer |
| D-4 | Cap payment at 999,999.99 | Avoid A-4 wrap without changing parity below the cap | Reviewer |
| D-5 | In-memory ledger, no auth | Exploratory slice | Bruce |

## 11. Open questions

| Question | Why it matters | Default | Blocking | Owner |
|---|---|---|---|---|
| Fix A-1 and A-3 after parity sign-off? | Changes ledger outcomes | Preserve | no | Business owner |
| Should a preview lock the supplier's items? | Concurrent operators | Revision check, 409 on stale | no | Architect |

## 12. Breadcrumbs

```json
{
  "orient": [
    {"tool": "get_entity", "args": {"project_key": "acas", "cycle": 22, "entity_type": "workflow", "entity_name": "PurchasePaymentProcessing"}},
    {"tool": "get_entity", "args": {"project_key": "acas", "cycle": 22, "entity_type": "entry_point", "entity_name": "PurchasePaymentDataEntry"}}
  ],
  "source": [
    {"why": "Posting gate", "tool": "get_file_content_in_range", "args": {"project_key": "acas", "cycle": 22, "file_path": "purchase/pl080.cbl", "start_line": 284, "num_lines": 10}},
    {"why": "Appropriation loop", "tool": "get_file_content_in_range", "args": {"project_key": "acas", "cycle": 22, "file_path": "purchase/pl080.cbl", "start_line": 531, "num_lines": 170}},
    {"why": "Payment write", "tool": "get_file_content_in_range", "args": {"project_key": "acas", "cycle": 22, "file_path": "purchase/pl080.cbl", "start_line": 725, "num_lines": 15}}
  ],
  "data": [
    {"tool": "get_file_content_in_range", "args": {"project_key": "acas", "cycle": 22, "file_path": "copybooks/plwsoi.cob", "start_line": 1, "num_lines": 80}},
    {"tool": "get_record_layout", "proposed": true, "args": {"project_key": "acas", "cycle": 22, "record": "OI-Header"}}
  ],
  "adjacent": [
    {"why": "Who consumes our type-5/6 records", "tool": "get_entity", "args": {"project_key": "acas", "cycle": 22, "entity_type": "business_rule", "entity_name": "PaymentReversalRule"}},
    {"tool": "get_file_access", "proposed": true, "args": {"project_key": "acas", "cycle": 22, "file": "OTM5"}}
  ]
}
```

## 13. Deliverables and write-back

- **PR description** must contain:
  - the rule IDs covered, with their tests
  - the anomalies and how each decision was applied
  - discrepancies found against this spec
  - the commands used to verify
- **Docs:** update `docs/concho/payment-processing-pl080.md` and the Concho usefulness assessment.
- **Verification report:** `docs/verification/pl080-payment-entry.json`, in this shape:

```json
{
  "slice_id": "pl080-payment-entry",
  "spec_hash": "sha256:...",
  "source_revision": "c601a4f...",
  "rules": [
    {"id": "R-DISCOUNT", "verdict": "confirmed", "evidence": "runtime", "test": "Pl080ParityTest#matchesLegacyOutput[discount-and-full-payment]"},
    {"id": "R-MOD11", "verdict": "refuted", "evidence": "static", "note": "pl080 never calls maps09"}
  ],
  "discrepancies": [
    {"type": "proposed-as-observed", "ref": "stakeholder review BR-PAY-002"},
    {"type": "wrong-value", "ref": "stakeholder review BR-PAY-008", "note": "limit is 999 (k pic 999), not 99"}
  ]
}
```

- **Return path:** a PR artifact today; `record_verification` (proposed) or `report_to_sage` later.
