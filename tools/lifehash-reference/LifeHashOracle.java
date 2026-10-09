import com.sparrowwallet.toucan.LifeHash;
import com.sparrowwallet.toucan.LifeHashVersion;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class LifeHashOracle {
    public static void main(String[] args) throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.US_ASCII));
        HexFormat hex = HexFormat.of();
        for(String line; (line = input.readLine()) != null;) {
            String[] fields = line.split(" ");
            byte[] data = fields[5].equals("-") ? new byte[0] : hex.parseHex(fields[5]);
            LifeHashVersion version = LifeHashVersion.values()[Integer.parseInt(fields[2])];
            int moduleSize = Integer.parseInt(fields[3]);
            boolean alpha = fields[4].equals("1");
            LifeHash.Image image = switch(fields[1]) {
                case "digest" -> LifeHash.makeFromDigest(data, version, moduleSize, alpha);
                case "utf8" -> LifeHash.makeFromUTF8(new String(data, StandardCharsets.UTF_8), version, moduleSize, alpha);
                default -> LifeHash.makeFromData(data, version, moduleSize, alpha);
            };
            byte[] colors = new byte[image.colors().size()];
            for(int i = 0; i < colors.length; i++) {
                colors[i] = image.colors().get(i);
            }
            System.out.println(fields[0] + " " + image.width() + " " + image.height() + " " + hex.formatHex(colors));
        }
    }
}
