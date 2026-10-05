package org.acas.purchase.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Compares the modern payment entry with output captured from the unmodified legacy pl080
 * (legacy-harness/capture-golden-pl080.sh), replaying the same keystrokes.
 */
class Pl080ParityTest {
    private static final Path PARITY = Path.of("src/test/resources/parity-pl080");

    static Stream<String> fixtures() throws IOException {
        try (Stream<Path> files = Files.list(PARITY)) {
            List<String> names = files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".fixture"))
                    .map(n -> n.substring(0, n.length() - ".fixture".length()))
                    .sorted()
                    .toList();
            assertFalse(names.isEmpty(), "no pl080 parity fixtures");
            return names.stream();
        }
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void matchesLegacyOutput(String name) throws IOException {
        List<String> fixture = Files.readAllLines(PARITY.resolve(name + ".fixture"), StandardCharsets.US_ASCII);
        List<String> expected = Files.readAllLines(PARITY.resolve(name + ".expected"), StandardCharsets.US_ASCII);
        List<String> keys = fixture.stream().filter(l -> l.startsWith("KEY|")).map(l -> l.substring(4)).toList();

        PurchaseLedger ledger = LedgerFormat.parse(fixture);
        LegacyKeyReplay.run(ledger, keys);

        assertEquals(String.join("\n", expected), String.join("\n", LedgerFormat.dump(ledger)));
    }
}
