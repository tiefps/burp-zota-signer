package burp.zota.signer;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.HighlightColor;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.zota.profile.ProfileManager;
import burp.zota.profile.ZotaProfile;
import burp.zota.util.QueryString;
import burp.zota.util.SignatureUtil;
import burp.zota.util.ZotaLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.*;

/**
 * Central signing engine for Zota API requests. Handles auto-detection of endpoint types, profile
 * selection (including manual overrides), signature generation, and helper transformations such as
 * applying default headers for Repeater previews.
 */
public class ZotaSigner {

    private final MontoyaApi api;
    private final ProfileManager profiles;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<EndpointRule> endpointRules;

    public ZotaSigner(MontoyaApi api, ProfileManager profiles) {
        this.api = api;
        this.profiles = profiles;
        this.endpointRules = List.of(
                new EndpointRule("POST", List.of("/api/v1/deposit/request/", "/api/v1/deposit/request/direct/"), this::signDeposit),
                new EndpointRule("POST", List.of("/api/v1/payout/request/"), this::signPayout),
                new EndpointRule("GET", List.of("/api/v1/query/order-status/"), this::signOrderStatus),
                new EndpointRule("GET", List.of("/api/v1/query/orders-report/csv/"), this::signOrdersReport),
                new EndpointRule("GET", List.of("/api/v1/query/current-balance/"), this::signCurrentBalance),
                new EndpointRule("GET", List.of("/api/v1/query/exchange-rates/"), this::signExchangeRates)
        );
    }

    /**
     * Wrapper around a (possibly modified) request and optional annotations used to explain signing actions.
     */
    public record Result(HttpRequest request, Annotations annotations) {}

    private static final String MANUAL_PROFILE_HEADER = "X-Zota-Profile";

    public Result sign(HttpRequest request, ZotaProfile profileOverride) {
        return signInternal(request, profileOverride, true, true);
    }

    public HttpRequest applyProfileDefaults(HttpRequest request, ZotaProfile profile) {
        if (profile == null) {
            return request;
        }
        HttpRequest updated = request;
        updated = updateServiceForProfile(updated, profile);
        updated = updateEndpointInPath(updated, profile);
        updated = updateQueryForProfile(updated, profile);
        updated = updateJsonBodyForProfile(updated, profile);
        return updated;
    }

    /**
     * Adds a transient header that signals which profile was used for manual re-sign actions in the UI.
     * The header is automatically stripped before a request is sent by {@link #signInternal}.
     */
    public HttpRequest markManualProfile(HttpRequest request, ZotaProfile profile) {
        if (request == null) {
            return null;
        }
        HttpRequest cleared = request.withRemovedHeader(MANUAL_PROFILE_HEADER);
        if (profile == null || profile.getName() == null || profile.getName().isBlank()) {
            return cleared;
        }
        return cleared.withAddedHeader(MANUAL_PROFILE_HEADER, profile.getName());
    }

    private static Annotations note(String kind, String signature, List<String> warnings) {
        String msg = "Zota: signed " + kind + " sig=" + signature + (warnings == null || warnings.isEmpty() ? "" : " warnings=" + String.join("/", warnings));
        return (warnings == null || warnings.isEmpty()) ? Annotations.annotations(msg) : Annotations.annotations(msg, HighlightColor.YELLOW);
    }

    private boolean isKnownZotaPath(String path) {
        if (path == null) {
            return false;
        }
        return endpointRules.stream().anyMatch(rule -> rule.matchesPath(path));
    }

    public Result signIfZota(HttpRequestToBeSent req) {
        return signInternal(req, null, false, false);
    }

    private Result signInternal(HttpRequest req, ZotaProfile profileOverride, boolean allowUnknownHost, boolean refreshDynamicValues) {
        String host = req.httpService() != null ? req.httpService().host() : "";
        String path = req.path();
        String method = req.method().toUpperCase(Locale.ROOT);

        boolean looksZotaHost = host != null && (host.contains("zota") || host.contains("zotapay"));

        boolean stripManualHeader = !allowUnknownHost;

        String manualHeader = req.headerValue(MANUAL_PROFILE_HEADER);
        boolean hasManualHeader = manualHeader != null && !manualHeader.trim().isEmpty();

        if (!refreshDynamicValues && hasManualHeader) {
            return finalizeResult(new Result(req, null), stripManualHeader);
        }

        ZotaProfile manualProfile = null;
        if (hasManualHeader) {
            String name = manualHeader.trim();
            manualProfile = profiles.byName(name);
            if (manualProfile == null) {
                ZotaLogger.error("Zota profile referenced in request not found: " + name);
            }
        }

        if (profileOverride == null && manualProfile != null) {
            profileOverride = manualProfile;
        }

        if (!allowUnknownHost && !looksZotaHost && !isKnownZotaPath(path)) {
            return finalizeResult(new Result(req, null), stripManualHeader);
        }

        try {
            EndpointRule matched = endpointRules.stream()
                    .filter(rule -> rule.matches(method, path))
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                Result signed = matched.handler().apply(req, profileOverride, refreshDynamicValues);
                return finalizeResult(signed, stripManualHeader);
            }

            if (!allowUnknownHost && method.equals("GET")) {
                Result ver = verifyFinalRedirect(req);
                if (ver.annotations() != null) return finalizeResult(ver, stripManualHeader);
            } else if (!allowUnknownHost && method.equals("POST")) {
                Result ver = verifyCallback(req);
                if (ver.annotations() != null) return finalizeResult(ver, stripManualHeader);
            }
        } catch (Exception e) {
            ZotaLogger.error("Signing error: " + e.getMessage());
            Annotations notes = Annotations.annotations("Zota signing error: " + e.getClass().getSimpleName() + ": " + e.getMessage(), HighlightColor.RED);
            return finalizeResult(new Result(req, notes), stripManualHeader);
        }

        return finalizeResult(new Result(req, null), stripManualHeader);
    }

    private Result finalizeResult(Result result, boolean stripManualHeader) {
        if (!stripManualHeader) {
            return result;
        }
        HttpRequest req = result.request();
        String header = req.headerValue(MANUAL_PROFILE_HEADER);
        if (header == null || header.isEmpty()) {
            return result;
        }
        HttpRequest stripped = req.withRemovedHeader(MANUAL_PROFILE_HEADER);
        return new Result(stripped, result.annotations());
    }

    /**
     * Functional contract for endpoint-specific signing implementations.
     */
    @FunctionalInterface
    private interface SignFunction {
        Result apply(HttpRequest req, ZotaProfile profileOverride, boolean refreshDynamicValues) throws Exception;
    }

    /**
     * Declarative description of an endpoint family (HTTP method + path prefixes) and the associated signer.
     */
    private record EndpointRule(String method, List<String> pathPrefixes, SignFunction handler) {
        boolean matches(String actualMethod, String path) {
            return method.equalsIgnoreCase(actualMethod) && matchesPath(path);
        }

        boolean matchesPath(String path) {
            if (path == null) {
                return false;
            }
            return pathPrefixes.stream().anyMatch(path::startsWith);
        }
    }

    private ZotaProfile resolveProfile(ZotaProfile override) {
        if (override != null) {
            return override;
        }
        return profiles.getActiveProfileOrWarn();
    }

    private Result noProfile(HttpRequest req) {
        return new Result(req, Annotations.annotations("Zota: no active profile", HighlightColor.RED));
    }

    private Result signCurrentBalance(HttpRequest req, ZotaProfile profileOverride, boolean refreshDynamicValues) {
        ZotaProfile p = resolveProfile(profileOverride);
        if (p == null) { return noProfile(req); }

        String basePath = req.pathWithoutQuery();
        Map<String, String> params = QueryString.parse(req.query());
        if (refreshDynamicValues) {
            params.put("merchantID", valueOrEmpty(p.getMerchantId()));
            params.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            params.put("requestID", UUID.randomUUID().toString());
        } else {
            params.putIfAbsent("merchantID", valueOrEmpty(p.getMerchantId()));
            params.putIfAbsent("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            params.putIfAbsent("requestID", UUID.randomUUID().toString());
        }

        String merchantId = params.getOrDefault("merchantID", "");
        String requestID = params.getOrDefault("requestID", "");
        if (requestID == null || requestID.isEmpty()) {
            String camel = params.getOrDefault("requestId", "");
            if (camel != null && !camel.isEmpty()) {
                requestID = camel;
                params.put("requestID", camel);
            }
        }
        String timestamp = params.getOrDefault("timestamp", "");

        String sigSource = valueOrEmpty(merchantId)
                + valueOrEmpty(requestID)
                + valueOrEmpty(timestamp)
                + valueOrEmpty(p.getMerchantSecretKey());

        String signature = SignatureUtil.sha256HexLower(sigSource);
        params.put("signature", signature);

        String newQuery = QueryString.build(params);
        HttpRequest out = req.withPath(basePath + "?" + newQuery);

        List<String> warnings = missingFields(Arrays.asList(
                field("merchantID", merchantId),
                field("requestID", requestID),
                field("timestamp", timestamp),
                field("MerchantSecretKey", p.getMerchantSecretKey())
        ));
        return new Result(out, note("current-balance", signature, warnings));
    }

    private Result verifyFinalRedirect(HttpRequest req) {
        Map<String, String> params = QueryString.parse(req.query());
        String status = params.get("status");
        String orderID = params.get("orderID");
        String merchantOrderID = params.get("merchantOrderID");
        String sig = params.get("signature");
        if (status == null || orderID == null || merchantOrderID == null || sig == null) {
            return new Result(req, null);
        }
        ZotaProfile p = profiles.getActiveProfileOrWarn();
        if (p == null) return new Result(req, Annotations.annotations("Zota: no active profile", HighlightColor.RED));
        String source = valueOrEmpty(status) + valueOrEmpty(orderID) + valueOrEmpty(merchantOrderID) + valueOrEmpty(p.getMerchantSecretKey());
        String expected = SignatureUtil.sha256HexLower(source);
        boolean ok = expected.equalsIgnoreCase(sig);
        Annotations ann = ok
                ? Annotations.annotations("Zota final-redirect signature: VALID", HighlightColor.GREEN)
                : Annotations.annotations("Zota final-redirect signature: INVALID", HighlightColor.RED);
        return new Result(req, ann);
    }

    private Result verifyCallback(HttpRequest req) {
        try {
            JsonNode json = safeJson(req.bodyToString());
            String endpointId = getJsonText(json, "EndpointID");
            String orderID = getJsonText(json, "orderID");
            String merchantOrderID = getJsonText(json, "merchantOrderID");
            String status = getJsonText(json, "status");
            String amount = getJsonText(json, "amount");
            String customerEmail = getJsonText(json, "customerEmail");
            String sig = getJsonText(json, "signature");
            if (sig == null || sig.isEmpty()) return new Result(req, null);
            ZotaProfile p = profiles.getActiveProfileOrWarn();
            if (p == null) return new Result(req, Annotations.annotations("Zota: no active profile", HighlightColor.RED));
            String source = valueOrEmpty(endpointId) + valueOrEmpty(orderID) + valueOrEmpty(merchantOrderID)
                    + valueOrEmpty(status) + valueOrEmpty(amount) + valueOrEmpty(customerEmail) + valueOrEmpty(p.getMerchantSecretKey());
            String expected = SignatureUtil.sha256HexLower(source);
            boolean ok = expected.equalsIgnoreCase(sig);
            Annotations ann = ok
                    ? Annotations.annotations("Zota callback signature: VALID", HighlightColor.GREEN)
                    : Annotations.annotations("Zota callback signature: INVALID", HighlightColor.RED);
            return new Result(req, ann);
        } catch (Exception e) {
            return new Result(req, Annotations.annotations("Zota callback verify error: " + e.getMessage(), HighlightColor.RED));
        }
    }

    // For previewing in UI (e.g., Repeater tab creation) so users see modifications
    public HttpRequest signForPreview(HttpRequest req) {
        Result result = signInternal(req, null, true, true);
        return result.request();
    }

    private Result signDeposit(HttpRequest req, ZotaProfile profileOverride, boolean refreshDynamicValues) throws Exception {
        ZotaProfile p = resolveProfile(profileOverride);
        if (p == null) {
            return noProfile(req);
        }

        String path = req.path();
        String endpointIdOrGroup = extractIdFromPath(path, "/api/v1/deposit/request/");
        String body = req.bodyToString();
        JsonNode json = safeJson(body);

        // Collect required fields (missing become empty strings)
        String merchantOrderID = getJsonText(json, "merchantOrderID");
        String orderAmount = getJsonText(json, "orderAmount");
        String customerEmail = getJsonText(json, "customerEmail");

        String sigSource = (endpointIdOrGroup == null ? "" : endpointIdOrGroup)
                + valueOrEmpty(merchantOrderID)
                + valueOrEmpty(orderAmount)
                + valueOrEmpty(customerEmail)
                + valueOrEmpty(p.getMerchantSecretKey());

        String signature = SignatureUtil.sha256HexLower(sigSource);

        JsonNode updated = SignatureUtil.withField(json, "signature", signature);
        String updatedBody = mapper.writeValueAsString(updated);

        List<String> warnings = missingFields(Arrays.asList(
                field("merchantOrderID", merchantOrderID),
                field("orderAmount", orderAmount),
                field("customerEmail", customerEmail),
                field("MerchantSecretKey", p.getMerchantSecretKey())
        ));

        HttpRequest out = req.withBody(updatedBody);
        return new Result(out, note("deposit", signature, warnings));
    }

    private Result signPayout(HttpRequest req, ZotaProfile profileOverride, boolean refreshDynamicValues) throws Exception {
        ZotaProfile p = resolveProfile(profileOverride);
        if (p == null) {
            return noProfile(req);
        }

        String path = req.path();
        String endpointId = extractIdFromPath(path, "/api/v1/payout/request/");
        String body = req.bodyToString();
        JsonNode json = safeJson(body);

        String merchantOrderID = getJsonText(json, "merchantOrderID");
        String orderAmount = getJsonText(json, "orderAmount");
        String customerEmail = getJsonText(json, "customerEmail");
        String customerBankAccountNumber = getJsonText(json, "customerBankAccountNumber");

        String sigSource = valueOrEmpty(endpointId)
                + valueOrEmpty(merchantOrderID)
                + valueOrEmpty(orderAmount)
                + valueOrEmpty(customerEmail)
                + valueOrEmpty(customerBankAccountNumber)
                + valueOrEmpty(p.getMerchantSecretKey());

        String signature = SignatureUtil.sha256HexLower(sigSource);

        JsonNode updated = SignatureUtil.withField(json, "signature", signature);
        String updatedBody = mapper.writeValueAsString(updated);

        List<String> warnings = missingFields(Arrays.asList(
                field("merchantOrderID", merchantOrderID),
                field("orderAmount", orderAmount),
                field("customerEmail", customerEmail),
                field("customerBankAccountNumber", customerBankAccountNumber),
                field("MerchantSecretKey", p.getMerchantSecretKey())
        ));

        HttpRequest out = req.withBody(updatedBody);
        return new Result(out, note("payout", signature, warnings));
    }

    private Result signOrderStatus(HttpRequest req, ZotaProfile profileOverride, boolean refreshDynamicValues) {
        ZotaProfile p = resolveProfile(profileOverride);
        if (p == null) {
            return noProfile(req);
        }

        String path = req.path();
        String basePath = req.pathWithoutQuery();
        String query = req.query();

        Map<String, String> params = QueryString.parse(query);
        if (refreshDynamicValues) {
            params.put("merchantID", valueOrEmpty(p.getMerchantId()));
            params.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        } else {
            params.putIfAbsent("merchantID", valueOrEmpty(p.getMerchantId()));
            params.putIfAbsent("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        }

        String merchantOrderID = params.getOrDefault("merchantOrderID", "");
        String orderID = params.getOrDefault("orderID", "");
        String timestamp = params.getOrDefault("timestamp", "");

        String sigSource = valueOrEmpty(p.getMerchantId())
                + valueOrEmpty(merchantOrderID)
                + valueOrEmpty(orderID)
                + valueOrEmpty(timestamp)
                + valueOrEmpty(p.getMerchantSecretKey());

        String signature = SignatureUtil.sha256HexLower(sigSource);
        params.put("signature", signature);

        String newQuery = QueryString.build(params);
        HttpRequest out = req.withPath(basePath + "?" + newQuery);

        List<String> warnings = missingFields(Arrays.asList(
                field("merchantID", p.getMerchantId()),
                field("merchantOrderID", merchantOrderID),
                field("orderID", orderID),
                field("timestamp", timestamp),
                field("MerchantSecretKey", p.getMerchantSecretKey())
        ));
        return new Result(out, note("order-status", signature, warnings));
    }

    private Result signOrdersReport(HttpRequest req, ZotaProfile profileOverride, boolean refreshDynamicValues) {
        ZotaProfile p = resolveProfile(profileOverride);
        if (p == null) {
            return noProfile(req);
        }

        String basePath = req.pathWithoutQuery();
        Map<String, String> params = QueryString.parse(req.query());
        if (refreshDynamicValues) {
            params.put("merchantID", valueOrEmpty(p.getMerchantId()));
            params.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            params.put("requestID", UUID.randomUUID().toString());
        } else {
            params.putIfAbsent("merchantID", valueOrEmpty(p.getMerchantId()));
            params.putIfAbsent("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            params.putIfAbsent("requestID", UUID.randomUUID().toString());
        }

        String dateType = params.getOrDefault("dateType", "");
        String endpointIds = params.getOrDefault("endpointIds", "");
        String fromDate = params.getOrDefault("fromDate", "");
        String requestID = params.getOrDefault("requestID", "");
        String statuses = params.getOrDefault("statuses", "");
        String timestamp = params.getOrDefault("timestamp", "");
        String toDate = params.getOrDefault("toDate", "");
        String types = params.getOrDefault("types", "");

        String sigSource = valueOrEmpty(p.getMerchantId())
                + valueOrEmpty(dateType)
                + valueOrEmpty(endpointIds)
                + valueOrEmpty(fromDate)
                + valueOrEmpty(requestID)
                + valueOrEmpty(statuses)
                + valueOrEmpty(timestamp)
                + valueOrEmpty(toDate)
                + valueOrEmpty(types)
                + valueOrEmpty(p.getMerchantSecretKey());

        String signature = SignatureUtil.sha256HexLower(sigSource);
        params.put("signature", signature);

        String newQuery = QueryString.build(params);
        HttpRequest out = req.withPath(basePath + "?" + newQuery);

        List<String> warnings = missingFields(Arrays.asList(
                field("merchantID", p.getMerchantId()),
                field("dateType", dateType),
                field("endpointIds", endpointIds),
                field("fromDate", fromDate),
                field("requestID", requestID),
                field("statuses", statuses),
                field("timestamp", timestamp),
                field("toDate", toDate),
                field("types", types),
                field("MerchantSecretKey", p.getMerchantSecretKey())
        ));
        return new Result(out, note("orders-report", signature, warnings));
    }

    private Result signExchangeRates(HttpRequest req, ZotaProfile profileOverride, boolean refreshDynamicValues) {
        ZotaProfile p = resolveProfile(profileOverride);
        if (p == null) {
            return noProfile(req);
        }

        String basePath = req.pathWithoutQuery();
        Map<String, String> params = QueryString.parse(req.query());
        if (refreshDynamicValues) {
            params.put("merchantID", valueOrEmpty(p.getMerchantId()));
            params.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            params.put("requestID", UUID.randomUUID().toString());
        } else {
            params.putIfAbsent("merchantID", valueOrEmpty(p.getMerchantId()));
            params.putIfAbsent("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            params.putIfAbsent("requestID", UUID.randomUUID().toString());
        }

        String requestID = params.getOrDefault("requestID", "");
        String date = params.getOrDefault("date", "");
        String timestamp = params.getOrDefault("timestamp", "");
        String orderType = params.getOrDefault("orderType", "");
        String orderID = params.getOrDefault("orderID", "");

        String sigSource = valueOrEmpty(p.getMerchantId())
                + valueOrEmpty(p.getMerchantSecretKey())
                + valueOrEmpty(requestID)
                + valueOrEmpty(date)
                + valueOrEmpty(timestamp)
                + valueOrEmpty(orderID);

        String signature = SignatureUtil.sha256HexLower(sigSource);
        params.put("signature", signature);

        String newQuery = QueryString.build(params);
        HttpRequest out = req.withPath(basePath + "?" + newQuery);

        List<String> warnings = missingFields(Arrays.asList(
                field("merchantID", p.getMerchantId()),
                field("requestID", requestID),
                field("date", date),
                field("timestamp", timestamp),
                field("orderType", orderType),
                field("orderID", orderID),
                field("MerchantSecretKey", p.getMerchantSecretKey())
        ));
        return new Result(out, note("exchange-rates", signature, warnings));
    }

    private HttpRequest updateServiceForProfile(HttpRequest request, ZotaProfile profile) {
        String apiBase = profile.getApiBase();
        if (apiBase == null || apiBase.isBlank()) {
            return request;
        }
        try {
            URI uri = normalizeApiBase(apiBase.trim());
            String host = uri.getHost();
            boolean tls = "https".equalsIgnoreCase(uri.getScheme());
            int port = uri.getPort();
            if (port == -1) {
                port = tls ? 443 : 80;
            }
            HttpService service = HttpService.httpService(host, port, tls);
            HttpRequest updated = request.withService(service);
            String hostHeader = isDefaultPort(tls, port) ? host : host + ":" + port;
            return updated.withUpdatedHeader("Host", hostHeader);
        } catch (Exception e) {
            ZotaLogger.error("Invalid API base for profile " + profile.getName() + ": " + e.getMessage());
            return request;
        }
    }

    private HttpRequest updateEndpointInPath(HttpRequest request, ZotaProfile profile) {
        String endpointId = profile.getDefaultEndpointId();
        if (endpointId == null || endpointId.isBlank()) {
            return request;
        }
        String trimmed = endpointId.trim();
        HttpRequest updated = request;
        updated = replaceEndpointSegment(updated, "/api/v1/deposit/request/direct/", trimmed);
        updated = replaceEndpointSegment(updated, "/api/v1/deposit/request/", trimmed);
        updated = replaceEndpointSegment(updated, "/api/v1/payout/request/", trimmed);
        return updated;
    }

    private HttpRequest replaceEndpointSegment(HttpRequest request, String prefix, String endpointId) {
        String basePath = request.pathWithoutQuery();
        if (basePath == null || !basePath.startsWith(prefix)) {
            return request;
        }
        String remainder = basePath.substring(prefix.length());
        String trailing;
        int slashIndex = remainder.indexOf('/');
        if (slashIndex >= 0) {
            trailing = remainder.substring(slashIndex);
        } else {
            trailing = basePath.endsWith("/") ? "/" : "";
        }
        String newBase = prefix + endpointId + trailing;
        String query = request.query();
        String newPath = (query == null || query.isEmpty()) ? newBase : newBase + "?" + query;
        if (newPath.equals(request.path())) {
            return request;
        }
        return request.withPath(newPath);
    }

    private HttpRequest updateQueryForProfile(HttpRequest request, ZotaProfile profile) {
        String query = request.query();
        String basePath = request.pathWithoutQuery();
        if ((query == null || query.isEmpty()) && !shouldForceMerchantId(basePath)) {
            return request;
        }

        Map<String, String> params = new LinkedHashMap<>(QueryString.parse(query));
        boolean changed = false;

        String merchantId = profile.getMerchantId();
        if (merchantId != null && !merchantId.isBlank() && (shouldForceMerchantId(basePath) || params.containsKey("merchantID"))) {
            params.put("merchantID", merchantId.trim());
            changed = true;
        }

        String endpointId = profile.getDefaultEndpointId();
        if (endpointId != null && !endpointId.isBlank() && params.containsKey("endpointIds")) {
            params.put("endpointIds", endpointId.trim());
            changed = true;
        }

        if (!changed) {
            return request;
        }

        String newQuery = QueryString.build(params);
        String newPath = (newQuery == null || newQuery.isEmpty()) ? basePath : basePath + "?" + newQuery;
        return request.withPath(newPath);
    }

    private HttpRequest updateJsonBodyForProfile(HttpRequest request, ZotaProfile profile) {
        if (!"POST".equalsIgnoreCase(request.method())) {
            return request;
        }
        String body = request.bodyToString();
        if (body == null || body.isEmpty()) {
            return request;
        }
        try {
            JsonNode json = safeJson(body);
            if (!(json instanceof ObjectNode obj)) {
                return request;
            }
            boolean changed = false;
            String merchantId = profile.getMerchantId();
            if (merchantId != null && !merchantId.isBlank()) {
                if (obj.has("merchantID")) {
                    obj.put("merchantID", merchantId.trim());
                    changed = true;
                }
                if (obj.has("MerchantID")) {
                    obj.put("MerchantID", merchantId.trim());
                    changed = true;
                }
            }
            String endpoint = profile.getDefaultEndpointId();
            if (endpoint != null && !endpoint.isBlank()) {
                if (obj.has("endpointID")) {
                    obj.put("endpointID", endpoint.trim());
                    changed = true;
                }
                if (obj.has("EndpointID")) {
                    obj.put("EndpointID", endpoint.trim());
                    changed = true;
                }
            }
            if (!changed) {
                return request;
            }
            return request.withBody(mapper.writeValueAsString(obj));
        } catch (Exception e) {
            // Ignore invalid JSON bodies when applying profile defaults
            return request;
        }
    }

    private static boolean shouldForceMerchantId(String basePath) {
        if (basePath == null) {
            return false;
        }
        return basePath.startsWith("/api/v1/query/order-status/")
                || basePath.startsWith("/api/v1/query/orders-report/csv/")
                || basePath.startsWith("/api/v1/query/current-balance/")
                || basePath.startsWith("/api/v1/query/exchange-rates/");
    }

    private static boolean isDefaultPort(boolean tls, int port) {
        return (tls && port == 443) || (!tls && port == 80);
    }

    private static URI normalizeApiBase(String apiBase) throws Exception {
        URI uri = new URI(apiBase);
        if (uri.getScheme() == null) {
            uri = new URI("https://" + apiBase);
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new IllegalArgumentException("API base is missing a host");
        }
        String scheme = uri.getScheme();
        if (scheme == null || scheme.isBlank()) {
            uri = new URI("https", uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        }
        return uri;
    }

    private String extractIdFromPath(String path, String prefix) {
        String rest = path.substring(prefix.length());
        // accept "group/<id>/" or "<id>/"
        String s = rest;
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        s = s.trim();
        if (s.startsWith("group/")) {
            s = s.substring("group/".length());
        }
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private JsonNode safeJson(String body) throws Exception {
        if (body == null || body.isEmpty()) {
            return mapper.createObjectNode();
        }
        return mapper.readTree(body);
    }

    private static String valueOrEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String getJsonText(JsonNode json, String field) {
        if (json == null) return "";
        JsonNode n = json.get(field);
        if (n == null || n.isNull()) return "";
        return n.asText("");
    }

    private static Map.Entry<String, String> field(String name, String value) {
        return new AbstractMap.SimpleEntry<>(name, value);
    }

    private static List<String> missingFields(List<Map.Entry<String, String>> pairs) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : pairs) {
            if (e.getValue() == null || e.getValue().isEmpty()) {
                missing.add(e.getKey());
            }
        }
        return missing;
    }
}
