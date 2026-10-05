package org.acas.sales.posting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs every parity fixture through the Java port and compares the resulting files with the
 * output the unmodified legacy sl055 produced for the same fixture
 * (see legacy-harness/capture-golden.sh).
 */
class LegacyParityTest {

    static Stream<Path> fixtures() throws IOException, URISyntaxException {
        Path dir = Path.of(LegacyParityTest.class.getResource("/parity").toURI());
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".fixture")).sorted().toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void matchesLegacyOutput(Path fixture) throws IOException {
        Path expected = fixture.resolveSibling(
                fixture.getFileName().toString().replace(".fixture", ".expected"));
        SalesLedger ledger = ParityFormat.parse(Files.readAllLines(fixture));

        InvoicePostExtract.run(ledger);

        List<String> actual = ParityFormat.dump(ledger);
        assertEquals(String.join("\n", Files.readAllLines(expected)), String.join("\n", actual));
    }
}
