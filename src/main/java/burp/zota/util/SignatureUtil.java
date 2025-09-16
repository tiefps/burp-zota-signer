package burp.zota.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SignatureUtil {
    private static final ObjectMapper mapper = new ObjectMapper();

    private SignatureUtil(){}

    public static String sha256HexLower(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode withField(JsonNode json, String key, String value) {
        if (json == null || json.isNull()) {
            ObjectNode on = mapper.createObjectNode();
            on.put(key, value);
            return on;
        }
        if (json instanceof ObjectNode) {
            ((ObjectNode) json).put(key, value);
            return json;
        } else {
            ObjectNode on = mapper.createObjectNode();
            on.set("request", json);
            on.put(key, value);
            return on;
        }
    }
}