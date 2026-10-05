       >>source free
*>*******************************************************************
*> sl055h - parity harness for the unmodified ACAS module sl055.
*>
*> Usage: sl055h <fixture-file> <dump-file>
*>
*> 1. Loads a pipe-delimited fixture into the ACAS Cobol (ISAM) files
*>    invoice.dat, value.dat and analysis.dat using the legacy file
*>    handlers acas016, acas013 and acas015.
*> 2. Calls sl055 exactly as the sales menu does (WS-Caller = xl150
*>    suppresses the interactive "press return" prompts).
*> 3. Dumps invoice.dat, value.dat, analysis.dat, openitm2.dat and the
*>    sales ledger month totals in the same pipe-delimited format.
*>
*> Must be run from an empty working directory, as ACAS uses relative
*> file names in Cobol file mode.
*>*******************************************************************
 identification division.
 program-id.  sl055h.
 environment division.
 copy "envdiv.cob".
 input-output section.
 file-control.
     select  Fixture-File  assign  Fixture-Name
                           organization line sequential
                           status  FX-Status.
     select  Dump-File     assign  Dump-Name
                           organization line sequential
                           status  DP-Status.
 copy "seloi2.cob".
 data division.
 file section.
 fd  Fixture-File.
 01  Fixture-Line          pic x(2048).
 fd  Dump-File.
 01  Dump-Line             pic x(2048).
 copy "fdoi2.cob".
 working-storage section.
 copy "slwsoi.cob".
 copy "wsfnctn.cob".
 copy "wsval.cob".
 copy "wsanal.cob".
 copy "slwsinv2.cob".
 01  WS-Invoice-Record  redefines Invoice-Record  pic x(137).
 copy "Test-Data-Flags.cob".
 01  Dummies-4-Unused-ACAS-FH-Calls.
     03  Default-Record         pic x.
     03  Final-Record           pic x.
     03  WS-Ledger-Record       pic x.
     03  WS-Posting-Record      pic x.
     03  WS-Batch-Record        pic x.
     03  WS-IRS-Posting-Record  pic x.
     03  WS-Stock-Audit-Record  pic x.
     03  WS-Stock-Record        pic x.
     03  WS-Sales-Record        pic x.
     03  WS-Delivery-Record     pic x.
     03  WS-Del-Inv-Nos-Record  pic x.
     03  WS-Purch-Record        pic x.
     03  WS-Pay-Record          pic x.
     03  WS-OTM3-Record         pic x.
     03  WS-PInvoice-Record     pic x.
     03  WS-OTM5-Record         pic x.
 copy "wsnames.cob".
 copy "wscall.cob".
 copy "wssystem.cob".
 copy "wssys4.cob".
 01  To-Day                pic x(10)  value "02/10/2026".
 01  Harness-Data.
     03  FX-Status         pic xx.
     03  DP-Status         pic xx.
     03  Fixture-Name      pic x(512).
     03  Dump-Name         pic x(512).
     03  Cmd-Line          pic x(1100).
     03  FX-Eof            pic 9      value zero.
     03  Ptr               pic 9(4)   comp.
     03  Fld-Ix            pic 99     comp.
     03  Out-Ptr           pic 9(4)   comp.
     03  Txt-Len           pic 9(4)   comp.
     03  Fmt-Money         pic -(9)9.99.
     03  Fmt-Int           pic -(10)9.
     03  Fmt-Text          pic x(64).
 01  Fields.
     03  Fld               pic x(64)  occurs 40.
 01  Out-Line              pic x(2048).
*>
 procedure division.
 aa000-Main section.
     accept   Cmd-Line from command-line.
     unstring Cmd-Line delimited by all space
              into Fixture-Name Dump-Name.
     initialize System-Record System-Record-4.
     set      Linux to true.
     move     zero to File-System-Used.
     move     1    to Date-Form.
     perform  ba000-Load-Fixture.
     move     "sl055"  to WS-Called.
     move     "xl150"  to WS-Caller.
     move     zero     to WS-Term-Code WS-Process-Func WS-Sub-Function.
     call     "sl055" using WS-Calling-Data
                            System-Record
                            System-Record-4
                            To-Day
                            File-Defs.
     perform  ca000-Dump.
     stop     run.
*>
 ba000-Load-Fixture section.
     perform  Invoice-Open-Output.
     perform  Value-Open-Output.
     perform  Analysis-Open-Output.
     open     input Fixture-File.
     if       FX-Status not = "00"
              display "sl055h: cannot open fixture " Fixture-Name upon syserr
              stop run returning 2
     end-if.
     perform  until FX-Eof = 1
              read  Fixture-File at end
                    move 1 to FX-Eof
              not at end
                    perform ba010-Load-Line
              end-read
     end-perform.
     close    Fixture-File.
     perform  Invoice-Close.
     perform  Value-Close.
     perform  Analysis-Close.
*>
 ba010-Load-Line section.
     if       Fixture-Line = spaces or Fixture-Line (1:1) = "#"
              exit section
     end-if.
     move     spaces to Fields.
     move     1 to Ptr.
     perform  varying Fld-Ix from 1 by 1
              until Fld-Ix > 40 or Ptr > length of Fixture-Line
              unstring Fixture-Line delimited by "|"
                       into Fld (Fld-Ix) with pointer Ptr
     end-perform.
     evaluate Fld (1)
       when   "SYS"
              move function numval (Fld (2)) to sl-invoices-this-month
              move function numval (Fld (3)) to sl-credit-notes-this-month
       when   "ANAL"
              initialize WS-Analysis-Record
              move Fld (2)                   to WS-Pa-Code
              move function numval (Fld (3)) to Pa-Gl
              move Fld (4)                   to Pa-Desc
              move Fld (5)                   to Pa-Print
              perform Analysis-Write
              perform bb000-Check-Write
       when   "VAL"
              initialize WS-Value-Record
              move Fld (2)                   to va-code
              move function numval (Fld (3)) to va-gl
              move Fld (4)                   to va-desc
              move Fld (5)                   to va-print
              move function numval (Fld (6))  to va-t-this
              move function numval (Fld (7))  to va-t-last
              move function numval (Fld (8))  to va-t-year
              move function numval (Fld (9))  to va-v-this
              move function numval (Fld (10)) to va-v-last
              move function numval (Fld (11)) to va-v-year
              perform Value-Write
              perform bb000-Check-Write
       when   "IH"
              initialize Invoice-Header
              move function numval (Fld (2))  to ih-invoice
              move zero                       to ih-test
              move Fld (3)                    to ih-customer
              move function numval (Fld (4))  to ih-date
              move Fld (5)                    to ih-order
              move function numval (Fld (6))  to ih-type
              move Fld (7)                    to ih-ref
              move Fld (8)                    to ih-description
              move function numval (Fld (9))  to ih-p-c
              move function numval (Fld (10)) to ih-net
              move function numval (Fld (11)) to ih-extra
              move function numval (Fld (12)) to ih-carriage
              move function numval (Fld (13)) to ih-vat
              move function numval (Fld (14)) to ih-discount
              move function numval (Fld (15)) to ih-e-vat
              move function numval (Fld (16)) to ih-c-vat
              move Fld (17)                   to ih-status
              move Fld (18)                   to ih-status-P
              move Fld (19)                   to ih-status-L
              move Fld (20)                   to ih-status-C
              move Fld (21)                   to ih-status-A
              move Fld (22)                   to ih-status-I
              move function numval (Fld (23)) to ih-lines
              move function numval (Fld (24)) to ih-deduct-days
              move function numval (Fld (25)) to ih-deduct-amt
              move function numval (Fld (26)) to ih-deduct-vat
              move function numval (Fld (27)) to ih-days
              move function numval (Fld (28)) to ih-cr
              move Fld (29)                   to ih-day-book-flag
              move Fld (30)                   to ih-update
              perform Invoice-Write
              perform bb000-Check-Write
       when   "IL"
              initialize Invoice-Line
              move function numval (Fld (2))  to il-invoice
              move function numval (Fld (3))  to il-line
              move Fld (4)                    to il-product
              move Fld (5)                    to il-pa
              move function numval (Fld (6))  to il-qty
              move Fld (7)                    to il-type
              move Fld (8)                    to il-description
              move function numval (Fld (9))  to il-net
              move function numval (Fld (10)) to il-unit
              move function numval (Fld (11)) to il-discount
              move function numval (Fld (12)) to il-vat
              move function numval (Fld (13)) to il-vat-code
              move Fld (14)                   to il-update
              move Fld (15)                   to il-Back-Ordered
              perform Invoice-Write
              perform bb000-Check-Write
       when   other
              display "sl055h: unknown fixture record " Fld (1) upon syserr
              stop run returning 2
     end-evaluate.
*>
 bb000-Check-Write section.
     if       FS-Reply not = zero
              display "sl055h: write failed " FS-Reply " for "
                      Fixture-Line (1:60) upon syserr
              stop run returning 2
     end-if.
*>
 ca000-Dump section.
     open     output Dump-File.
     perform  Invoice-Open-Input.
     perform  until exit
              perform Invoice-Read-Next
              if      FS-Reply not = zero
                      exit perform
              end-if
              if      ih-test = zero
                      perform cb000-Dump-Header
              else
                      perform cb010-Dump-Line
              end-if
     end-perform.
     perform  Invoice-Close.
     perform  Value-Open-Input.
     perform  until exit
              perform Value-Read-Next
              if      FS-Reply not = zero
                      exit perform
              end-if
              perform cb020-Dump-Value
     end-perform.
     perform  Value-Close.
     perform  Analysis-Open-Input.
     perform  until exit
              perform Analysis-Read-Next
              if      FS-Reply not = zero
                      exit perform
              end-if
              perform cb030-Dump-Analysis
     end-perform.
     perform  Analysis-Close.
     open     input open-item-file-2.
     if       FS-Reply = zero
              perform until exit
                      read open-item-file-2 into OI-Header at end
                           exit perform
                      end-read
                      perform cb040-Dump-OTM2
              end-perform
              close open-item-file-2
     end-if.
     perform  zz000-Begin.
     move     "SYS" to Fmt-Text.  perform zz010-Text.
     move     sl-invoices-this-month     to Fmt-Money. perform zz020-Money.
     move     sl-credit-notes-this-month to Fmt-Money. perform zz020-Money.
     perform  zz090-End.
     close    Dump-File.
*>
 cb000-Dump-Header section.
     perform  zz000-Begin.
     move "IH"            to Fmt-Text.  perform zz010-Text.
     move ih-invoice      to Fmt-Int.   perform zz030-Int.
     move ih-customer     to Fmt-Text.  perform zz010-Text.
     move ih-date         to Fmt-Int.   perform zz030-Int.
     move ih-order        to Fmt-Text.  perform zz010-Text.
     move ih-type         to Fmt-Int.   perform zz030-Int.
     move ih-ref          to Fmt-Text.  perform zz010-Text.
     move ih-description  to Fmt-Text.  perform zz010-Text.
     move ih-p-c          to Fmt-Money. perform zz020-Money.
     move ih-net          to Fmt-Money. perform zz020-Money.
     move ih-extra        to Fmt-Money. perform zz020-Money.
     move ih-carriage     to Fmt-Money. perform zz020-Money.
     move ih-vat          to Fmt-Money. perform zz020-Money.
     move ih-discount     to Fmt-Money. perform zz020-Money.
     move ih-e-vat        to Fmt-Money. perform zz020-Money.
     move ih-c-vat        to Fmt-Money. perform zz020-Money.
     move ih-status       to Fmt-Text.  perform zz010-Text.
     move ih-status-P     to Fmt-Text.  perform zz010-Text.
     move ih-status-L     to Fmt-Text.  perform zz010-Text.
     move ih-status-C     to Fmt-Text.  perform zz010-Text.
     move ih-status-A     to Fmt-Text.  perform zz010-Text.
     move ih-status-I     to Fmt-Text.  perform zz010-Text.
     move ih-lines        to Fmt-Int.   perform zz030-Int.
     move ih-deduct-days  to Fmt-Int.   perform zz030-Int.
     move ih-deduct-amt   to Fmt-Money. perform zz020-Money.
     move ih-deduct-vat   to Fmt-Money. perform zz020-Money.
     move ih-days         to Fmt-Int.   perform zz030-Int.
     move ih-cr           to Fmt-Int.   perform zz030-Int.
     move ih-day-book-flag to Fmt-Text. perform zz010-Text.
     move ih-update       to Fmt-Text.  perform zz010-Text.
     perform  zz090-End.
*>
 cb010-Dump-Line section.
     perform  zz000-Begin.
     move "IL"            to Fmt-Text.  perform zz010-Text.
     move il-invoice      to Fmt-Int.   perform zz030-Int.
     move il-line         to Fmt-Int.   perform zz030-Int.
     move il-product      to Fmt-Text.  perform zz010-Text.
     move il-pa           to Fmt-Text.  perform zz010-Text.
     move il-qty          to Fmt-Int.   perform zz030-Int.
     move il-type         to Fmt-Text.  perform zz010-Text.
     move il-description  to Fmt-Text.  perform zz010-Text.
     move il-net          to Fmt-Money. perform zz020-Money.
     move il-unit         to Fmt-Money. perform zz020-Money.
     move il-discount     to Fmt-Money. perform zz020-Money.
     move il-vat          to Fmt-Money. perform zz020-Money.
     move il-vat-code     to Fmt-Int.   perform zz030-Int.
     move il-update       to Fmt-Text.  perform zz010-Text.
     move il-Back-Ordered to Fmt-Text.  perform zz010-Text.
     perform  zz090-End.
*>
 cb020-Dump-Value section.
     perform  zz000-Begin.
     move "VAL"           to Fmt-Text.  perform zz010-Text.
     move va-code         to Fmt-Text.  perform zz010-Text.
     move va-gl           to Fmt-Int.   perform zz030-Int.
     move va-desc         to Fmt-Text.  perform zz010-Text.
     move va-print        to Fmt-Text.  perform zz010-Text.
     move va-t-this       to Fmt-Int.   perform zz030-Int.
     move va-t-last       to Fmt-Int.   perform zz030-Int.
     move va-t-year       to Fmt-Int.   perform zz030-Int.
     move va-v-this       to Fmt-Money. perform zz020-Money.
     move va-v-last       to Fmt-Money. perform zz020-Money.
     move va-v-year       to Fmt-Money. perform zz020-Money.
     perform  zz090-End.
*>
 cb030-Dump-Analysis section.
     perform  zz000-Begin.
     move "ANAL"          to Fmt-Text.  perform zz010-Text.
     move WS-Pa-Code      to Fmt-Text.  perform zz010-Text.
     move Pa-Gl           to Fmt-Int.   perform zz030-Int.
     move Pa-Desc         to Fmt-Text.  perform zz010-Text.
     move Pa-Print        to Fmt-Text.  perform zz010-Text.
     perform  zz090-End.
*>
 cb040-Dump-OTM2 section.
     perform  zz000-Begin.
     move "OI"            to Fmt-Text.  perform zz010-Text.
     move OI-Customer     to Fmt-Text.  perform zz010-Text.
     move OI-Invoice      to Fmt-Int.   perform zz030-Int.
     move OI-Date         to Fmt-Int.   perform zz030-Int.
     move OI-B-Nos        to Fmt-Int.   perform zz030-Int.
     move OI-B-Item       to Fmt-Int.   perform zz030-Int.
     move OI-Type         to Fmt-Int.   perform zz030-Int.
     move OI-Description  to Fmt-Text.  perform zz010-Text.
     move OI-Hold-Flag    to Fmt-Text.  perform zz010-Text.
     move OI-Unapl        to Fmt-Text.  perform zz010-Text.
     move OI-P-C          to Fmt-Money. perform zz020-Money.
     move OI-Net          to Fmt-Money. perform zz020-Money.
     move OI-Extra        to Fmt-Money. perform zz020-Money.
     move OI-Carriage     to Fmt-Money. perform zz020-Money.
     move OI-Vat          to Fmt-Money. perform zz020-Money.
     move OI-Discount     to Fmt-Money. perform zz020-Money.
     move OI-E-Vat        to Fmt-Money. perform zz020-Money.
     move OI-C-Vat        to Fmt-Money. perform zz020-Money.
     move OI-Paid         to Fmt-Money. perform zz020-Money.
     move OI-Status       to Fmt-Int.   perform zz030-Int.
     move OI-Deduct-Days  to Fmt-Int.   perform zz030-Int.
     move OI-Deduct-Amt   to Fmt-Money. perform zz020-Money.
     move OI-Deduct-Vat   to Fmt-Money. perform zz020-Money.
     move OI-Days         to Fmt-Int.   perform zz030-Int.
     move OI-Cr           to Fmt-Int.   perform zz030-Int.
     move OI-Applied      to Fmt-Text.  perform zz010-Text.
     move OI-Date-Cleared to Fmt-Int.   perform zz030-Int.
     perform  zz090-End.
*>
 zz000-Begin section.
     move     spaces to Out-Line.
     move     1 to Out-Ptr.
*>
 zz010-Text section.
     move     length of Fmt-Text to Txt-Len.
     perform  until Txt-Len = zero
                 or Fmt-Text (Txt-Len:1) not = space
              subtract 1 from Txt-Len
     end-perform.
     if       Txt-Len > zero
              string Fmt-Text (1:Txt-Len) delimited by size
                     into Out-Line with pointer Out-Ptr
     end-if.
     string   "|" delimited by size into Out-Line with pointer Out-Ptr.
*>
 zz020-Money section.
     move     function trim (Fmt-Money) to Fmt-Text.
     perform  zz010-Text.
*>
 zz030-Int section.
     move     function trim (Fmt-Int) to Fmt-Text.
     perform  zz010-Text.
*>
 zz090-End section.
     subtract 1 from Out-Ptr.
     move     space to Out-Line (Out-Ptr:1).
     write    Dump-Line from Out-Line.
*>
 copy "Proc-ACAS-FH-Calls.cob".
