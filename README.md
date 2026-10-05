# acas-modern

Java 17 re-implementation of [ACAS](https://github.com/sigalarm/acas-legacy) (Applewood Computers
Accounting System), migrated one slice at a time with behavioural parity against the legacy
COBOL.

## Slices

| Slice | Legacy | Java | Parity evidence |
|---|---|---|---|
| Sales invoice post extract | `sales/sl055.cbl` | `org.acas.sales.posting.InvoicePostExtract` | `src/test/resources/parity/*.expected`, captured from the legacy program |

sl055 is phase 1 of Concho's `SalesInvoicePostingPipeline` workflow. Phase 2, sl060 (posting the
open items to the ledger and GL), is not migrated yet; the hand-off between them is
`openitm2.dat`, modelled here as `SalesLedger.openItems()`.

## Build and test

```bash
mvn -B verify
```

`-Xlint:all -Werror` is on, so compiler warnings fail the build.

## Parity testing

Each `src/test/resources/parity/<name>.fixture` describes the starting contents of the ACAS files
(invoice, value, analysis) and the sales-ledger month totals. `<name>.expected` holds the files
after the **unmodified legacy program** has run against that fixture. `LegacyParityTest` runs the
same fixture through the Java port and requires the output to match byte for byte.

To regenerate the `.expected` files (needs GnuCOBOL 3.2 and an acas-legacy checkout):

```bash
ACAS_LEGACY=../acas-legacy legacy-harness/capture-golden.sh
```

`legacy-harness/sl055h.cbl` loads a fixture through the legacy file handlers (acas013, acas015,
acas016), calls sl055 as the sales menu does, and dumps the result. It runs ACAS in Cobol-file
(ISAM) mode, not RDBMS mode.

## Legacy defects (kept for parity)

The parity runs showed that sl055 has the defects below in Cobol-file mode. The Java port keeps
them so that the parity tests mean something. Each fix should be its own reviewed change that
updates the expected files on purpose.

1. **Missing value records post to a blank key.** If a line's analysis code (`"S" + il-pa`) has
   no value record, the failed read in acas013 blanks the whole working record, key included.
   sl055 then creates an "Emergency Name" analysis record and value record under the key
   `"   "`, and posts the line there instead of under its own code.
2. **Special totals collapse onto the blank key.** The VAT, receipt-VAT, carriage and deduction
   totals (`Svo`, `?vp`, `?zc`, `?zd`) hit the same blank-key problem whenever those value
   records are missing. In addition, every later code inherits a blank system letter, and
   totals whose write lands on an existing blank record are lost. In the
   `invoice-and-credit-note` fixture, the VAT is counted twice and the carriage once on the
   blank record.
3. **Lines are only marked analysed after the parent update.** `il-update = "Z"` is set only
   after both the code and its parent (second character blanked) have been updated. Lines with
   a single-character code, or whose parent value record is missing, stay unmarked. A rerun
   counts them again: see `skip-rules-rerun`, where `SB` goes from 20.00 to 40.00.

With every analysis and value record configured (the `specials-configured` fixture), none of
these show up and the extract posts correctly.

## Docs

- [docs/concho-usefulness-sl055.md](docs/concho-usefulness-sl055.md): where the Concho MCP helped
  during the sl055 migration, where the source and runtime had to decide, and suggested Concho
  improvements.
