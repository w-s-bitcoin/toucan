package com.sparrowwallet.toucan.impl;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class NumericReferenceTest {
    @Test
    public void matchesCppFloatingPointBoundariesExactly() throws Exception {
        InputStream fixtures = getClass().getResourceAsStream("/com/sparrowwallet/toucan/numeric-reference.txt");
        assertNotNull("Missing C++ numeric reference fixtures", fixtures);
        int count = 0;
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(fixtures, StandardCharsets.US_ASCII))) {
            for(String line; (line = reader.readLine()) != null;) {
                if(line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                String[] fields = line.split(" ");
                switch(fields[0]) {
                    case "palette" -> {
                        // C++ divides in double precision, without a float intermediate.
                        Color color = Color.fromUint8Values(Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
                        assertBits(line, fields[4], color.r);
                        assertBits(line, fields[5], color.g);
                        assertBits(line, fields[6], color.b);
                    }
                    case "luminance" -> {
                        // C++ rounds each powf argument/result, both sums, and sqrtf.
                        Color color = new Color(Double.parseDouble(fields[1]), Double.parseDouble(fields[2]), Double.parseDouble(fields[3]));
                        assertBits(line, fields[4], color.luminance());
                    }
                    case "modulo" -> {
                        // Covers negative values, a non-exact divisor, and both
                        // nested fmodf argument conversions at the wrap boundary.
                        double actual = Utils.modulo(Double.parseDouble(fields[1]), Double.parseDouble(fields[2]));
                        assertBits(line, fields[3], actual);
                    }
                    case "hsb" -> {
                        // floorf rounds h just below 5 to the next hue sector.
                        Color color = new HSBColor(Double.parseDouble(fields[1])).color();
                        assertBits(line, fields[2], color.r);
                        assertBits(line, fields[3], color.g);
                        assertBits(line, fields[4], color.b);
                    }
                    default -> throw new AssertionError("Unknown numeric fixture: " + fields[0]);
                }
                count++;
            }
        }
        assertEquals("Truncated numeric reference fixture corpus", 12, count);
    }

    private static void assertBits(String context, String expected, double actual) {
        assertEquals(context, Double.doubleToLongBits(Double.parseDouble(expected)), Double.doubleToLongBits(actual));
    }
}
