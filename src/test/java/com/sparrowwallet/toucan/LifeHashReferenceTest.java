package com.sparrowwallet.toucan;

import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.Assert.*;

public class LifeHashReferenceTest {
    @Test
    public void matchesPinnedCppReference() throws Exception {
        InputStream fixtures = getClass().getResourceAsStream("lifehash-reference.txt");
        assertNotNull("Missing C++ reference fixtures", fixtures);
        HexFormat hex = HexFormat.of();
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        int count = 0;
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(fixtures, StandardCharsets.US_ASCII))) {
            for(String line; (line = reader.readLine()) != null;) {
                if(line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                String[] fields = line.split(" ");
                assertEquals("Invalid reference fixture: " + line, 8, fields.length);
                LifeHashVersion version = LifeHashVersion.values()[Integer.parseInt(fields[1])];
                int moduleSize = Integer.parseInt(fields[2]);
                boolean alpha = fields[3].equals("1");
                byte[] input = fields[4].equals("-") ? new byte[0] : hex.parseHex(fields[4]);
                String context = fields[0] + ":" + fields[4] + " " + version + " module=" + moduleSize + " alpha=" + alpha;
                LifeHash.Image image = switch(fields[0]) {
                    case "data" -> LifeHash.makeFromData(input, version, moduleSize, alpha);
                    case "digest" -> LifeHash.makeFromDigest(input, version, moduleSize, alpha);
                    case "utf8" -> LifeHash.makeFromUTF8(new String(input, StandardCharsets.UTF_8), version, moduleSize, alpha);
                    default -> throw new AssertionError("Unknown fixture mode: " + fields[0]);
                };
                assertEquals(context, Integer.parseInt(fields[5]), image.width());
                assertEquals(context, Integer.parseInt(fields[6]), image.height());
                assertEquals(context, alpha, image.hasAlpha());
                assertEquals(context, image.width() * image.height() * (alpha ? 4 : 3), image.colors().size());
                byte[] pixels = new byte[image.colors().size()];
                for(int i = 0; i < pixels.length; i++) {
                    pixels[i] = image.colors().get(i);
                }
                assertEquals(context, fields[7], hex.formatHex(sha256.digest(pixels)));
                // Check BufferedImage conversion outside the bulk fingerprint corpus.
                if(!fields[0].equals("data") || version != LifeHashVersion.VERSION2
                        || moduleSize != 1 || alpha || input.length != 4) {
                    assertBufferedImage(context, image, pixels);
                }
                count++;
            }
        }
        assertEquals("Truncated or unexpectedly changed reference fixture corpus", 4216, count);
    }

    private static void assertBufferedImage(String context, LifeHash.Image image, byte[] pixels) {
        BufferedImage bufferedImage = LifeHash.getBufferedImage(image);
        int components = image.hasAlpha() ? 4 : 3;
        for(int y = 0; y < image.height(); y++) {
            for(int x = 0; x < image.width(); x++) {
                int offset = (y * image.width() + x) * components;
                int alpha = image.hasAlpha() ? pixels[offset + 3] & 0xff : 255;
                int argb = (alpha << 24) | ((pixels[offset] & 0xff) << 16)
                        | ((pixels[offset + 1] & 0xff) << 8) | (pixels[offset + 2] & 0xff);
                assertEquals(context + " pixel(" + x + "," + y + ")", argb, bufferedImage.getRGB(x, y));
            }
        }
    }
}
