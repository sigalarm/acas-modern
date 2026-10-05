package org.acas.purchase.payment.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {
    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void reset() throws Exception {
        mvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
    }

    private static String payment(String body) {
        return "{\"date\":\"2026-10-05\",\"supplier\":\"acme001\"," + body + "}";
    }

    @Test
    void reportsTheOpenBatch() throws Exception {
        mvc.perform(get("/api/batch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchNumber").value(42))
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.maxItems").value(999))
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    void looksUpSuppliers() throws Exception {
        mvc.perform(get("/api/suppliers/beta002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Beta Parts"))
                .andExpect(jsonPath("$.addressLines[0]").value("9 Low Road"))
                .andExpect(jsonPath("$.unappliedBalance").value(150.00));
        mvc.perform(get("/api/suppliers/NOSUCH1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/suppliers").param("query", "corn"))
                .andExpect(jsonPath("$[0].account").value("CORN003"));
    }

    @Test
    void previewsWithoutSaving() throws Exception {
        mvc.perform(post("/api/payments/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(payment("\"amount\":700.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].invoice").value(1001))
                .andExpect(jsonPath("$.lines[0].discount").value(9.60))
                .andExpect(jsonPath("$.lines[0].status").value("CLEARED"))
                .andExpect(jsonPath("$.lines[1].proposal").value(300.00))
                .andExpect(jsonPath("$.lines[1].applied").value(229.60))
                .andExpect(jsonPath("$.lines[1].status").value("PART_PAID"))
                .andExpect(jsonPath("$.lines[2].status").value("NOT_REACHED"))
                .andExpect(jsonPath("$.lines[3].status").value("NOT_REACHED"))
                .andExpect(jsonPath("$.unappropriated").value(0.00));
        mvc.perform(get("/api/batch")).andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void rejectsAnAppropriationThatIsTooHigh() throws Exception {
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(payment("\"amount\":100.00,\"lines\":[{\"invoice\":1001,\"amount\":150.00}]")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPROPRIATION_ERRORS"))
                .andExpect(jsonPath("$.appropriation.lines[0].status").value("TOO_HIGH"));
    }

    @Test
    void savesPaymentsAndClosesTheBatch() throws Exception {
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(payment("\"amount\":200.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.reference").value(42001))
                .andExpect(jsonPath("$.payment.transactionType").value(5))
                .andExpect(jsonPath("$.batch.itemCount").value(1))
                .andExpect(jsonPath("$.batch.batchTotal").value(200.00));
        mvc.perform(get("/api/suppliers/ACME001/open-items"))
                .andExpect(jsonPath("$[0].batchNumber").value(42))
                .andExpect(jsonPath("$[4].invoice").value(42001));
        mvc.perform(post("/api/batch/close"))
                .andExpect(jsonPath("$.batchNumber").value(43))
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void allocatesUnappliedBalance() throws Exception {
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-10-05\",\"supplier\":\"BETA002\",\"amount\":200.00,"
                                + "\"allocateUnapplied\":true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXCEEDS_UNAPPLIED"));
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-10-05\",\"supplier\":\"BETA002\",\"amount\":100.00,"
                                + "\"allocateUnapplied\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.transactionType").value(6))
                .andExpect(jsonPath("$.batch.batchTotal").value(0.00));
        mvc.perform(get("/api/suppliers/BETA002")).andExpect(jsonPath("$.unappliedBalance").value(50.00));
    }
}
