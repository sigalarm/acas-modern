# Payment Data Entry (pl080): reviewed extract

Source material: *Concho Modernization Stakeholder Review – Applewood Computers Accounting System,
Payment Processing* (Concho.AI, May 2026, run 012), supplied as a Safari web archive. The full
archive is not committed; this file keeps only what applies to the Payment Data Entry slice, and
every claim below has been checked against `sigalarm/acas-legacy` (the code Concho analysed as
`concho-cycle-22`) and against the running legacy program.

Rule of thumb used throughout: **the checked-out source and the observed legacy runtime are the
truth; the review and the Concho graph are the map.**

## Provenance and drift

| Input | Analysed revision | How it was used |
|---|---|---|
| Stakeholder review | Concho cycle 8 (stated in the report) | Slice choice, UX intent, field inventory, candidate rules |
| Concho MCP graph (`acas`) | cycle 22, same code as acas-legacy | Rule and workflow lookup with file:line refs |
| `purchase/pl080.cbl` + copybooks | acas-legacy `main` | Authoritative behaviour |
| `legacy-harness/pl080h.cbl` runs | GnuCOBOL 3.2, Cobol-file (ISAM) mode | Authoritative outputs (`src/test/resources/parity-pl080/*.expected`) |

The cycle-22 graph's line references match the checkout exactly (e.g. `pl080.cbl:288` posting
gate, `:592` discount window, `:733` payment reference). The review's references are a few lines
off or broader ranges, consistent with an older cycle:

| Review reference | Actual (acas-legacy) |
|---|---|
| `accept pay-customer at 0572`, lines 372/374 | line 376 (371–372 is the batch-full check) |
| `accept-money2`, lines 585–640 | paragraph starts at line 519 |
| Discount window, lines 585–605 | lines 592–597 |
| Posting gate, lines 288–293 | lines 288–292 |
| Payment reference, line 731–735 | lines 731–733 |

## Verified workflow

1. **Posting gate** (288–292). If `P-Flag-I = 1` (purchase invoices entered but not posted),
   pl080 shows `PL121 Invoices Not Posted; Payment Entry Not Allowed` and exits without touching
   any file.
2. **Batch-full check** (371–372). If the item counter `k` (`pic 999`) has reached 999, it shows
   `Batch Closed........Full!` and stops taking payments.
3. **Payment date.** Accepted as `dd/mm/ccyy` and validated by `maps04`; an invalid date
   re-prompts.
4. **Supplier account** (376). Seven characters, upper-cased, read by key from `purchled`. An
   unknown key re-prompts. There is **no** check-digit validation: pl080 never calls `maps09`.
   The supplier's name, address (split on `PL-Delim`) and current balance are displayed.
5. **Unapplied balance** (427–466). If `purch-unapplied > 0` the operator is asked whether to
   allocate it. If yes, the amount (default: the whole unapplied balance) must not exceed it;
   it is subtracted from `purch-unapplied`, the supplier record is rewritten immediately and the
   payment becomes transaction type **6**. Otherwise a new payment amount is entered (type **5**).
6. **Batch numbering** (482–484). `BL-Next-Batch = 0` is replaced by 1; the item number is `k`.
7. **Appropriation loop** (545–700). Open items are read in key order (supplier, invoice) from
   the supplier's first key. Records that are not type 2 or that already carry a batch number are
   skipped; the first record for another supplier ends the loop. For each invoice:
   - `work-net = oi-net + oi-carriage + oi-vat + oi-c-vat`.
   - **Discount window:** `u-bin = oi-date + oi-deduct-days + 1`; if `u-bin > pay-date` the
     deduction `oi-deduct-amt` is offered and subtracted from the amount due.
   - Proposed amount = due − `oi-paid`, capped at what is left of the payment; the operator may
     change it. Zero means "No change".
   - If the running appropriation would exceed the payment value: `Payment Too High`, re-enter.
   - Paying exactly the amount due clears the invoice (`oi-status = 1`, `oi-date-cleared`), and
     inside the window takes the discount (`oi-net` reduced, `deduct-taken` accumulated).
   - Paying the amount due less the deduction outside the window prompts *settle in full?* (`Y`
     default); `Y` clears the invoice and takes the deduction.
   - `oi-paid` / `oi-p-c` are increased, `oi-cr = 100 × amount`, and the invoice is rewritten
     with this batch's number and item.
   - The loop ends when the payment is fully appropriated or the supplier's invoices run out.
8. **Payment record** (main-end, 720–740). A new open item of type 5/6 is written with
   `oi-invoice = BL-Next-Batch × 1000 + k`, `oi-paid` = payment value, `oi-net` = amount
   appropriated, `oi-deduct-amt` = discount taken.
9. **Batch close** (321–326). Leaving payment entry normally always increments `BL-Next-Batch`,
   even when no payment was entered, so an empty session still uses up a batch number.

## Field inventory (Payment Data Entry screen)

| Modern field | Legacy item | Notes |
|---|---|---|
| Payment date | `pay-date` via `maps04` | `dd/mm/ccyy`; ISO date in the API |
| Supplier account | `pay-customer pic x(7)` | Upper-cased; keyed read of `purchled` |
| Supplier name/address | `purch-name`, `purch-address` | Address split on `PL-Delim` |
| Current balance | `purch-current` | Read-only |
| Unapplied balance | `purch-unapplied` | Read-only; drives the allocate option |
| Allocate unapplied | `ws-reply` at 1601 | Checkbox, only shown when unapplied > 0 |
| Payment amount | `pay-value` via `accept-money` | Up to 9,999,999.99 |
| Invoice / date / ref | `oi-invoice`, `oi-date`, `oi-ref` | Appropriation rows |
| Outstanding | `work-1` before discount | `work-net − oi-paid` |
| Discount | `display-5` | `oi-deduct-amt` inside the window, else 0 |
| Amount to apply | `pay-paid` via `accept-money2` | Editable per row |
| Settle in full | `ws-reply` at col 65 | Only when the settle prompt would appear |
| Batch / item / total | `BL-Next-Batch`, `k`, batch total | Header bar |

## Business rules: review vs source

| Review rule | Verdict | Implementation |
|---|---|---|
| BR-PAY-001 Payment posting validation | Confirmed (`P-Flag-I`, 288–292) | `INVOICES_NOT_POSTED`, UI blocked |
| BR-PAY-002 Supplier account format (MOD-11) | **Not legacy behaviour.** pl080 only does a keyed lookup; `maps09` is never called | Not implemented. Unknown accounts are rejected as in pl080. Adding MOD-11 would be a new rule needing a business decision, and existing accounts may not carry check digits |
| BR-PAY-003 Payment type filtering | Confirmed for pl080 as "type 2 only"; the review's "type 2 and 4" applies to the proof sort (pl090), not entry | Appropriation filter |
| BR-PAY-004 Early payment discount | Confirmed, including the `+ 1` day and strict `>` (the review's scene 3 omits the `+ 1`) | `Appropriation` |
| BR-PAY-005 BACS vs cheque | Out of slice (pl940) | — |
| BR-PAY-006 Batch status lifecycle | pl080 has no batch status; only `BL-Next-Batch` and the item counter | Batch number/count/total only |
| BR-PAY-007 Unapplied balance allocation | Confirmed, type 6, supplier rewritten before appropriation | `startUnappliedAllocation` |
| BR-PAY-008 Batch control limits (99-item cap) | **Wrong limit for pl080.** `fdbatch.cob Items pic 99` is not used by pl080; its counter is `k pic 999` with a check at 999. Concho cycle 22 (`PaymentBatchEncodingAndRolloverRule`) has the 999 limit right | 999-item limit |
| BR-PAY-009 Reversal | Out of slice (pl085) | — |
| BR-PAY-010 Remittance | Out of slice (pl960) | — |

## Legacy defects found by running pl080 (kept for parity)

1. **"Fully paid" invoices are never actually closed.** At line 571, an invoice whose paid amount
   already equals its gross amount is marked cleared in `OI-Header`, but `OTM5-Rewrite` writes
   `WS-OTM5-Record`, which still holds the record as read (the `move OI-Header to
   WS-OTM5-Record` used everywhere else is missing). The stored invoice stays open, and the same
   no-op happens on every later payment. See invoice 202 in `skip-rules` and invoice 4001
   (DELT004) in the demo data.
2. **The proposed amount can exceed what is left of the payment.** pl080 proposes the full
   amount due and only reports `Payment Too High` after the operator accepts it. The API keeps
   that value as `proposal` and adds a `suggested` amount capped at the remaining payment, which
   the UI uses as its default. Behaviour on save is unchanged.
3. **Payments of 1,000,000.00 or more wrap the appropriation total.** The payment value is
   `PIC 9(7)V99`, but `approp-amount` is `PIC 9(6)V99`, so the accumulated appropriation loses its
   millions digit and the payment record shows the wrong net. The domain model keeps the field
   widths; the API rejects payments above 999,999.99, and line amounts above the remaining
   payment are reported as `Payment Too High` before they reach the accumulator.
4. **A payment reference that clashes with an existing item is silently lost.** The payment
   record's invoice number (`batch × 1000 + item`) shares the open-item key space with real
   invoices, and pl080 ignores a failed `OTM5-Write` after the invoices have been updated. The
   API rejects such a payment instead.

## Deliberate differences from the review

- **Stack:** Spring Boot 3 (Java 17) API + React 18/Vite instead of FastAPI/Pydantic/PostgreSQL,
  chosen on purpose to show the approach does not depend on the stack.
- **Scope:** Payment Data Entry only. The Payment Cycle Workbench, SAROC/RabbitMQ bridge,
  inline GL posting (pl100), cheque/BACS (pl940), remittance (pl960), PostgreSQL schema and AWS
  deployment are out of scope. State is in memory, loaded from a pl080h-format fixture.
- **UX:** the line-by-line terminal dialogue becomes a live appropriation preview that the
  operator edits before saving; the screen dialogue's per-line answers (amount, settle Y/N) are
  sent with the save request and replayed through the same domain code as the legacy loop.

## Open questions for review

1. Should defect 1 (fully paid invoices never closed) be fixed? Fixing it changes which invoices
   pl090/pl100 see, so it should be its own change with updated expected files.
2. Is MOD-11 (BR-PAY-002) wanted as a *new* rule? If so, which existing supplier accounts fail it?
3. Should the modern batch keep the 999-item limit, or does the 99-item `fdbatch` limit apply
   downstream (pl090/pl100) and need enforcing earlier?
