package org.acas.sales.posting;

/** A record of the sales invoice file: either a header (item 00) or a line item. */
public sealed interface InvoiceRecord permits InvoiceHeader, InvoiceLine {

    InvoiceKey key();

    InvoiceRecord copy();
}
