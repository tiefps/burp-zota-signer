package burp.zota.util;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility helpers for parsing and building {@code application/x-www-form-urlencoded} query strings.
 */
public final class QueryString {

    private QueryString(){}

    /**
     * Parses a query string into insertion-ordered key/value pairs. Missing values become empty strings.
     *
     * @param query the raw query component (without the leading {@code ?}).
     * @return ordered map of decoded parameters.
     */
    public static Map<String, String> parse(String query) {
        Map<String, String> out = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return out;

        String[] parts = query.split("&");
        for (String p : parts) {
            if (p.isEmpty()) continue;
            int i = p.indexOf('=');
            if (i < 0) {
                out.put(urlDecode(p), "");
            } else {
                String k = urlDecode(p.substring(0, i));
                String v = urlDecode(p.substring(i + 1));
                out.put(k, v);
            }
        }
        return out;
    }

    /**
     * Builds a query string from key/value pairs, properly URL-encoding each component.
     *
     * @param params ordered key/value pairs.
     * @return encoded query string without the leading {@code ?}.
     */
    public static String build(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append('&');
            first = false;
            sb.append(urlEncode(e.getKey()));
            sb.append('=');
            String v = e.getValue() == null ? "" : e.getValue();
            sb.append(urlEncode(v));
        }
        return sb.toString();
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
