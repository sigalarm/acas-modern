       >>source free
*>*******************************************************************
*> pl080h - parity harness for the unmodified ACAS module pl080
*>          (Purchase Ledger Payment Data Entry).
*>
*> Usage: pl080h <fixture-file> <dump-file>
*>
*> 1. Loads a pipe-delimited fixture into the ACAS Cobol (ISAM) files
*>    purchled.dat and openitm5.dat using the legacy file handlers
*>    acas022 and acas029.
*> 2. Calls pl080 as the purchase menu does. pl080 is interactive, so
*>    the keystrokes come from the KEY records of the fixture, typed
*>    by drive-pl080.py through a pseudo terminal.
*> 3. Dumps purchled.dat, openitm5.dat and the system record fields
*>    pl080 changes, in the same pipe-delimited format.
*>
*> Dates are dd/mm/ccyy in fixtures and dumps and are held as COBOL
*> integer-of-date day numbers in the files.
*>
*> Must be run from an empty working directory, as ACAS uses relative
*> file names in Cobol file mode.
*>*******************************************************************
 identification division.
 program-id.  pl080h.
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
 data division.
 file section.
 fd  Fixture-File.
 01  Fixture-Line          pic x(2048).
 fd  Dump-File.
 01  Dump-Line             pic x(2048).
 working-storage section.
 copy "wsfnctn.cob".
 copy "wsmaps03.cob".
 copy "wspl.cob".
 copy "plwsoi5B.cob".
 copy "plwsoi.cob".
 copy "Test-Data-Flags.cob".
 01  Dummies-4-Unused-ACAS-FH-Calls.
     03  Default-Record         pic x.
     03  Final-Record           pic x.
     03  System-Record-4        pic x.
     03  WS-Ledger-Record       pic x.
     03  WS-Posting-Record      pic x.
     03  WS-Batch-Record        pic x.
     03  WS-IRS-Posting-Record  pic x.
     03  WS-Stock-Audit-Record  pic x.
     03  WS-Stock-Record        pic x.
     03  WS-Sales-Record        pic x.
     03  WS-Value-Record        pic x.
     03  WS-Delivery-Record     pic x.
     03  WS-Analysis-Record     pic x.
     03  WS-Del-Inv-Nos-Record  pic x.
     03  WS-Pay-Record          pic x.
     03  WS-Invoice-Record      pic x.
     03  WS-OTM3-Record         pic x.
     03  WS-PInvoice-Record     pic x.
 copy "wsnames.cob".
 copy "wscall.cob".
 copy "wssystem.cob".
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
     03  Date-Text         pic x(10).
     03  Date-Bin          binary-long.
 01  Fields.
     03  Fld               pic x(64)  occurs 40.
 01  Out-Line              pic x(2048).
*>
 procedure division.
 aa000-Main section.
     accept   Cmd-Line from command-line.
     unstring Cmd-Line delimited by all space
              into Fixture-Name Dump-Name.
     initialize System-Record.
     set      Linux to true.
     move     zero to File-System-Used.
     move     1    to Date-Form.
     perform  ba000-Load-Fixture.
     move     "pl080"  to WS-Called.
     move     "pl900"  to WS-Caller.
     move     zero     to WS-Term-Code WS-Process-Func WS-Sub-Function.
     call     "pl080" using WS-Calling-Data
                            System-Record
                            To-Day
                            File-Defs.
     perform  ca000-Dump.
     stop     run.
*>
 ba000-Load-Fixture section.
     perform  Purch-Open-Output.
     perform  OTM5-Open-Output.
     open     input Fixture-File.
     if       FX-Status not = "00"
              display "pl080h: cannot open fixture " Fixture-Name upon syserr
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
     perform  Purch-Close.
     perform  OTM5-Close.
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
              move function numval (Fld (2)) to BL-Next-Batch
              move function numval (Fld (3)) to P-Flag-I
              move function numval (Fld (4)) to P-Flag-P
              move Fld (5) (1:1)             to PL-Delim
       when   "SUP"
              initialize WS-Purch-Record
              move Fld (2)                   to WS-Purch-Key
              move 1                         to Purch-Status
              move Fld (3)                   to Purch-Name
              move Fld (4)                   to Purch-Address
              move function numval (Fld (5)) to Purch-Current
              move function numval (Fld (6)) to Purch-Unapplied
              perform Purch-Write
              perform bb000-Check-Write
       when   "OI5"
              initialize OI-Header
              move Fld (2)                    to OI-Supplier
              move function numval (Fld (3))  to OI-Invoice
              move Fld (4)                    to Date-Text
              perform bc000-Date-To-Bin
              move Date-Bin                   to OI-Date
              move function numval (Fld (5))  to OI-B-Nos
              move function numval (Fld (6))  to OI-B-Item
              move function numval (Fld (7))  to OI-Type
              move Fld (8)                    to OI-Ref
              move Fld (9)                    to OI-Order
              move Fld (10)                   to OI-Hold-Flag
              move Fld (11)                   to OI-Unapl
              move function numval (Fld (12)) to OI-P-C
              move function numval (Fld (13)) to OI-Net
              move function numval (Fld (14)) to OI-Extra
              move function numval (Fld (15)) to OI-Carriage
              move function numval (Fld (16)) to OI-Vat
              move function numval (Fld (17)) to OI-Discount
              move function numval (Fld (18)) to OI-E-Vat
              move function numval (Fld (19)) to OI-C-Vat
              move function numval (Fld (20)) to OI-Paid
              move function numval (Fld (21)) to OI-Status
              move function numval (Fld (22)) to OI-Deduct-Days
              move function numval (Fld (23)) to OI-Deduct-Amt
              move function numval (Fld (24)) to OI-Deduct-Vat
              move function numval (Fld (25)) to OI-Days
              move function numval (Fld (26)) to OI-CR
              move Fld (27)                   to OI-Applied
              move Fld (28)                   to Date-Text
              perform bc000-Date-To-Bin
              move Date-Bin                   to OI-Date-Cleared
              move OI-Header to WS-OTM5-Record
              perform OTM5-Write
              perform bb000-Check-Write
       when   "KEY"
              continue
       when   other
              display "pl080h: unknown fixture record " Fld (1) upon syserr
              stop run returning 2
     end-evaluate.
*>
 bb000-Check-Write section.
     if       FS-Reply not = zero
              display "pl080h: write failed " FS-Reply " for "
                      Fixture-Line (1:60) upon syserr
              stop run returning 2
     end-if.
*>
 bc000-Date-To-Bin section.
     move     zero to Date-Bin.
     if       Date-Text = spaces
              exit section
     end-if.
     move     Date-Text to U-Date.
     move     zero to U-Bin.
     call     "maps04" using Maps03-WS.
     if       U-Bin = zero
              display "pl080h: bad date " Date-Text upon syserr
              stop run returning 2
     end-if.
     move     U-Bin to Date-Bin.
*>
 bd000-Bin-To-Date section.
     move     spaces to Date-Text.
     if       Date-Bin = zero
              exit section
     end-if.
     move     Date-Bin to U-Bin.
     call     "maps04" using Maps03-WS.
     move     U-Date to Date-Text.
*>
 ca000-Dump section.
     open     output Dump-File.
     perform  Purch-Open-Input.
     perform  until exit
              perform Purch-Read-Next
              if      FS-Reply not = zero
                      exit perform
              end-if
              perform cb000-Dump-Supplier
     end-perform.
     perform  Purch-Close.
     perform  OTM5-Open-Input.
     perform  until exit
              perform OTM5-Read-Next
              if      FS-Reply not = zero
                      exit perform
              end-if
              move    WS-OTM5-Record to OI-Header
              perform cb010-Dump-Open-Item
     end-perform.
     perform  OTM5-Close.
     perform  zz000-Begin.
     move     "SYS" to Fmt-Text.  perform zz010-Text.
     move     BL-Next-Batch to Fmt-Int.  perform zz030-Int.
     move     P-Flag-I      to Fmt-Int.  perform zz030-Int.
     move     P-Flag-P      to Fmt-Int.  perform zz030-Int.
     move     PL-Delim      to Fmt-Text. perform zz010-Text.
     move     Oi-5-Flag     to Fmt-Text. perform zz010-Text.
     perform  zz090-End.
     close    Dump-File.
*>
 cb000-Dump-Supplier section.
     perform  zz000-Begin.
     move "SUP"           to Fmt-Text.  perform zz010-Text.
     move WS-Purch-Key    to Fmt-Text.  perform zz010-Text.
     move Purch-Name      to Fmt-Text.  perform zz010-Text.
     move Purch-Address   to Fmt-Text.  perform zz010-Text.
     move Purch-Current   to Fmt-Money. perform zz020-Money.
     move Purch-Unapplied to Fmt-Money. perform zz020-Money.
     perform  zz090-End.
*>
 cb010-Dump-Open-Item section.
     perform  zz000-Begin.
     move "OI5"           to Fmt-Text.  perform zz010-Text.
     move OI-Supplier     to Fmt-Text.  perform zz010-Text.
     move OI-Invoice      to Fmt-Int.   perform zz030-Int.
     move OI-Date         to Date-Bin.  perform bd000-Bin-To-Date.
     move Date-Text       to Fmt-Text.  perform zz010-Text.
     move OI-B-Nos        to Fmt-Int.   perform zz030-Int.
     move OI-B-Item       to Fmt-Int.   perform zz030-Int.
     move OI-Type         to Fmt-Int.   perform zz030-Int.
     move OI-Ref          to Fmt-Text.  perform zz010-Text.
     move OI-Order        to Fmt-Text.  perform zz010-Text.
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
     move OI-CR           to Fmt-Int.   perform zz030-Int.
     move OI-Applied      to Fmt-Text.  perform zz010-Text.
     move OI-Date-Cleared to Date-Bin.  perform bd000-Bin-To-Date.
     move Date-Text       to Fmt-Text.  perform zz010-Text.
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
