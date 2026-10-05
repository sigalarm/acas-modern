# Was the Concho MCP useful for the ACAS sl055 migration?

**Short answer:** Yes, for *finding and framing* the slice. Not for *implementing* it to parity. Concho saved the up-front discovery work and supplied a good checklist. Every behaviour that the parity tests actually check came from the COBOL source and from running the legacy program.

## Where Concho clearly helped

| Step | What Concho gave | Value |
|---|---|---|
| Picking the slice | `SalesInvoicePostingPipeline` workflow (confidence 0.91, 13 steps), with sl055 as phase 1 and sl060 as phase 2, linked through `openitm2.dat` | High. It identified a batch boundary with file I/O on both sides, which is the ideal first slice. Finding that by reading 150 programs would take hours. |
| Scoping | The list of programs/copybooks involved (sl055, sl060, acas013/015/016/019, valueMT, otm3MT, slwsinv2, slwsoi, wsval) | High. It told me which file handlers to compile for the harness. |
| Test plan | State sequence UNPOSTED → LINE_ITEMS_ANALYSED → HEADER_EXTRACTED → OTM2_WRITTEN → SPECIAL_VALUES_STORED, plus business rules (credit-note sign reversal, duplicate-analysis guard, proforma skip) | Medium-high. These became the fixture cases. All of them checked out in the source. |
| Matching source to graph | The missing `an-accept.pl` is absent from Concho's graph too | Medium. It confirmed the repo matches what Concho analysed for cycle 22. |

## Where the source and the runtime had to decide

| Finding | Why Concho could not supply it |
|---|---|
| A failed indexed read in acas013 blanks the whole record, key included, so lines with no value record post to key `"   "` ("Emergency Name") | This only shows up from file-handler semantics plus a real run. The rule-level model describes intended behaviour. |
| `il-update = "Z"` is set only after the parent analysis code is updated, so single-level codes are re-counted on every rerun | The rule "already-analysed lines are skipped" is true but incomplete. The rerun fixture proved the gap. |
| Special totals (`Svo`/`?vp`/`?zc`/`?zd`) collapse onto the blank key, and the system letter is lost | Same cause, at the level of exact control flow. |
| COBOL PIC truncation (s9(7)v99 comp-3, 9(5) comp) | Storage detail. You need the copybooks. |
| GnuCOBOL 3.1.2 vs 3.2, Berkeley DB, `-o` with module builds, the harness needing a TTY (`script`) | Toolchain behaviour. Outside Concho's scope. |

## Practical guidance for the customer

1. Use Concho to **choose and bound** each slice, and to draft the fixture list from its workflow states and rules.
2. Treat every Concho rule as a **hypothesis**: confirm it in the checkout, then pin it with a fixture run through the *legacy* binary.
3. Expect the parity runs to surface **latent defects** that the model doesn't show. Port them as-is, then fix each one as its own reviewed change.
4. Possible Concho improvements suggested by this run: expose file-handler/record-lifecycle semantics (what a failed READ leaves in the record), and link rules to the exact paragraph where state changes (e.g. where `il-update` is set).
