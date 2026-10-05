package org.acas.sales.posting;

/** Product analysis record ({@code WS-Analysis-Record} in wsanal.cob). */
public record AnalysisRecord(String code, int gl, String desc, String print) {

    public AnalysisRecord {
        code = Pic.text(code, 3);
        desc = Pic.text(desc, 24);
        print = Pic.text(print, 3);
    }
}
