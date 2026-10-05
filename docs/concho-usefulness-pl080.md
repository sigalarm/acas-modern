# Concho usefulness: Payment Data Entry (pl080) UX slice

Second Devin + Concho slice, after `docs/concho-usefulness-sl055.md` (sales invoice post
extract). This one has a user interface, and its input was a Concho *Modernization Stakeholder
Review* plus the Concho MCP graph. Details of what was checked are in
`docs/concho/payment-processing-pl080.md`.

## Worked well

- **Picking and framing the slice.** The review named Payment Data Entry as the entry point of
  Payment Processing, described the operator journey and gave a field inventory. That fixed the
  scope and the UI layout before any code was read.
- **UX intent.** The "appropriation preview before commit" idea from the review is the core of
  the new screen, and it maps cleanly onto the legacy loop.
- **Rule catalogue as a checklist.** BR-PAY-001/003/004/007 each pointed at real code and became
  tests. The MCP graph (cycle 22) found the same rules with line references that matched the
  checkout exactly, which made verification fast.
- **The MCP graph corrected the report.** Cycle 22's `PaymentBatchEncodingAndRolloverRule` has
  the 999-item limit; the review's BR-PAY-008 says 99.

## Needed more polish

- **Report vs graph drift.** The review was generated from cycle 8; line references were a few
  lines off. Stating the source revision (not just the cycle) in reports would let an agent
  detect drift immediately.
- **Proposed behaviour presented as legacy behaviour.** BR-PAY-002 (MOD-11) cites `maps09`, but
  pl080 never calls it. The review's "behavioralFidelity: verbatim" label is misleading for a
  rule that does not exist in the program. Separating *observed* rules from *recommended*
  rules in the report would avoid this.
- **Program-level scoping of rules.** BR-PAY-003 mixes pl080 (type 2 only) with pl090 (types 2
  and 4); BR-PAY-008 attributes `fdbatch`'s `pic 99` to pl080, which never uses that file.
- **Defects are invisible to the model.** As with sl055, the most important behaviour came from
  running the program: pl080 marks already-paid invoices as cleared but rewrites the unchanged
  record (missing `move OI-Header to WS-OTM5-Record`), so they are never closed. Static rule
  extraction describes the intent, not this.

## Net assessment

Concho was most valuable before coding (what to build, for whom, which rules to check) and as a
fast index into the source. Parity still came from the source and the legacy runtime. For UX
work specifically, the review's mockups and field inventory saved the most time; its rule
fidelity labels needed the most checking.
