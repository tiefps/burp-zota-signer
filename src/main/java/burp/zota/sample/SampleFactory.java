package burp.zota.sample;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.zota.profile.ZotaProfile;
import burp.zota.util.ZotaLogger;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Builds ready-to-send Burp requests targeting Zota APIs for the active profile.
 * Each factory instance shares a collaborator payload so generated emails and callback URLs
 * map to the same Burp collaborator host, making it easier to track asynchronous callbacks.
 */
public class SampleFactory {
    private final MontoyaApi api;
    private final ZotaProfile p;
    private String collaboratorHost;

    /**
     * @param api Montoya API for accessing Collaborator utilities and Repeater.
     * @param p   The profile whose merchant credentials populate sample payloads.
     */
    public SampleFactory(MontoyaApi api, ZotaProfile p){
        this.api = api;
        this.p = p;
    }

    /**
     * Sample : Deposit request (POST).
     */
    public HttpRequest deposit() {
        String endpoint = firstNonEmpty(p.getDefaultEndpointId(), "1050");
        String url = stripTrail(p.getApiBase()) + "/api/v1/deposit/request/" + endpoint + "/";
        String host = hostOf(url);
        HttpService svc = serviceOf(url);
        String path = pathWithQuery(url);
        long now = System.currentTimeMillis();
        String body = """
                {
                  "merchantID": "%s",
                  "merchantOrderID": "example-%d",
                  "merchantOrderDesc": "Test order",
                  "orderAmount": "500.00",
                  "orderCurrency": "THB",
                  "customerEmail": "%s",
                  "customerFirstName": "John",
                  "customerLastName": "Doe",
                  "customerAddress": "5/5 Moo",
                  "customerCountryCode": "TH",
                  "customerCity": "Bangkok",
                  "customerZipCode": "10000",
                  "customerPhone": "+66-77111111",
                  "customerIP": "1.2.3.4",
                  "redirectUrl": "%s",
                  "callbackUrl": "%s",
                  "checkoutUrl": "%s"
                }
                """.formatted(
                        firstNonEmpty(p.getMerchantId(), "EXAMPLE-MERCHANT-ID"),
                        now,
                        collaboratorEmail("customer"),
                        collaboratorUrl("/deposit-return"),
                        collaboratorUrl("/deposit-callback"),
                        collaboratorUrl("/deposit-checkout")
                );
        String req = "POST " + path + " HTTP/1.1\r\n" +
                "Host: " + host + "\r\n" +
                "Content-Type: application/json\r\n" +
                "\r\n" + body;
        return HttpRequest.httpRequest(svc, req);
    }

    /**
     * Sample : Payout request (POST).
     */
    public HttpRequest payout() {
        String endpoint = firstNonEmpty(p.getDefaultEndpointId(), "1050");
        String url = stripTrail(p.getApiBase()) + "/api/v1/payout/request/" + endpoint + "/";
        String host = hostOf(url);
        HttpService svc = serviceOf(url);
        String path = pathWithQuery(url);
        long now = System.currentTimeMillis();
        String body = """
                {
                  "merchantID": "%s",
                  "merchantOrderID": "example-%d",
                  "merchantOrderDesc": "Test payout",
                  "orderAmount": "100.00",
                  "orderCurrency": "THB",
                  "customerEmail": "%s",
                  "customerFirstName": "John",
                  "customerLastName": "Doe",
                  "customerPhone": "+66-77111111",
                  "customerIP": "1.2.3.4",
                  "callbackUrl": "%s",
                  "customerBankAccountNumber": "100200"
                }
                """.formatted(
                        firstNonEmpty(p.getMerchantId(), "EXAMPLE-MERCHANT-ID"),
                        now,
                        collaboratorEmail("customer"),
                        collaboratorUrl("/payout-callback")
                );
        String req = "POST " + path + " HTTP/1.1\r\n" +
                "Host: " + host + "\r\n" +
                "Content-Type: application/json\r\n" +
                "\r\n" + body;
        return HttpRequest.httpRequest(svc, req);
    }

    /**
     * Sample : Order status query (GET).
     */
    public HttpRequest orderStatus() {
        String url = stripTrail(p.getApiBase()) + "/api/v1/query/order-status/?merchantID=" +
                enc(firstNonEmpty(p.getMerchantId(), "EXAMPLE-MERCHANT-ID")) +
                "&merchantOrderID=example-moid&orderID=example-oid&timestamp=" + (System.currentTimeMillis() / 1000);
        String host = hostOf(url);
        HttpService svc = serviceOf(url);
        String path = pathWithQuery(url);
        String req = "GET " + path + " HTTP/1.1\r\n" +
                "Host: " + host + "\r\n\r\n";
        return HttpRequest.httpRequest(svc, req);
    }

    /**
     * Sample : Orders report CSV query (GET).
     */
    public HttpRequest ordersReport() {
        String url = stripTrail(p.getApiBase()) + "/api/v1/query/orders-report/csv/?" +
                "merchantID=" + enc(firstNonEmpty(p.getMerchantId(), "EXAMPLE-MERCHANT-ID")) +
                "&dateType=created&endpointIds=1001,1002&fromDate=2019-11-01&requestID=req-1" +
                "&statuses=APPROVED,DECLINED&timestamp=" + (System.currentTimeMillis() / 1000) +
                "&toDate=2019-11-01&types=SALE,PAYOUT&includeAllColumns=true";
        String host = hostOf(url);
        HttpService svc = serviceOf(url);
        String path = pathWithQuery(url);
        String req = "GET " + path + " HTTP/1.1\r\n" +
                "Host: " + host + "\r\n\r\n";
        return HttpRequest.httpRequest(svc, req);
    }

    /**
     * Sample : Exchange rates query (GET).
     */
    public HttpRequest exchangeRates() {
        String url = stripTrail(p.getApiBase()) + "/api/v1/query/exchange-rates/?" +
                "merchantID=" + enc(firstNonEmpty(p.getMerchantId(), "EXAMPLE-MERCHANT-ID")) +
                "&requestID=req-2&date=&timestamp=" + (System.currentTimeMillis() / 1000) +
                "&orderType=SALE&orderID=32452684";
        String host = hostOf(url);
        HttpService svc = serviceOf(url);
        String path = pathWithQuery(url);
        String req = "GET " + path + " HTTP/1.1\r\n" +
                "Host: " + host + "\r\n\r\n";
        return HttpRequest.httpRequest(svc, req);
    }

    /**
     * Sample : Current balance query (GET).
     */
    public HttpRequest currentBalance() {
        String url = stripTrail(p.getApiBase()) + "/api/v1/query/current-balance/?" +
                "merchantID=" + enc(firstNonEmpty(p.getMerchantId(), "EXAMPLE-MERCHANT-ID")) +
                "&requestID=req-3&timestamp=" + (System.currentTimeMillis() / 1000);
        String host = hostOf(url);
        HttpService svc = serviceOf(url);
        String path = pathWithQuery(url);
        String req = "GET " + path + " HTTP/1.1\r\n" +
                "Host: " + host + "\r\n\r\n";
        return HttpRequest.httpRequest(svc, req);
    }

    private static String firstNonEmpty(String... xs) {
        for (String x : xs) if (x != null && !x.isEmpty()) return x;
        return "";
    }

    /**
     * Retrieves or caches the collaborator host used for generated payloads. The same host is reused
     * for the lifetime of the factory so related requests map back to one collaborator client.
     */
    private String collaboratorHost() {
        if (collaboratorHost != null) {
            return collaboratorHost;
        }
        try {
            CollaboratorPayload payload = api.collaborator().createClient().generatePayload();
            if (payload != null) {
                collaboratorHost = payload.toString();
            }
        } catch (Exception e) {
            ZotaLogger.error("Failed to obtain collaborator payload: " + e.getMessage());
        }
        if (collaboratorHost == null || collaboratorHost.isBlank()) {
            collaboratorHost = "example-callback.invalid";
        }
        return collaboratorHost;
    }

    private String collaboratorUrl(String path) {
        String host = collaboratorHost();
        String normalized = (path == null || path.isEmpty()) ? "/" : (path.startsWith("/") ? path : "/" + path);
        return "https://" + host + normalized;
    }

    private String collaboratorEmail(String localPart) {
        String lp = (localPart == null || localPart.isEmpty()) ? "user" : localPart;
        return lp + "@" + collaboratorHost();
    }

    private static String stripTrail(String s) {
        if (s == null) return "";
        if (s.endsWith("/")) return s.substring(0, s.length()-1);
        return s;
    }

    private static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * @return host extracted from the provided URL or empty string if parsing fails.
     */
    private static String hostOf(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /**
     * Builds a Montoya {@link HttpService} that matches the URL's host/port/scheme.
     */
    private static HttpService serviceOf(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return HttpService.httpService("", false);
            }
            boolean tls = "https".equalsIgnoreCase(uri.getScheme());
            int port = uri.getPort();
            if (port == -1) {
                port = tls ? 443 : 80;
            }
            return HttpService.httpService(host, port, tls);
        } catch (IllegalArgumentException e) {
            return HttpService.httpService("", false);
        }
    }

    /**
     * @return raw path plus query component suitable for the first line of an HTTP request.
     */
    private static String pathWithQuery(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String query = uri.getRawQuery();
            return (query == null || query.isEmpty()) ? path : path + "?" + query;
        } catch (IllegalArgumentException e) {
            return url; // fallback
        }
    }
}
