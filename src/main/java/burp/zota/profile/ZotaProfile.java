package burp.zota.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ZotaProfile {
    private String name;
    private String merchantId;
    private String merchantSecretKey;
    private String defaultEndpointId; // optional
    private String apiBase;           // https://api.zotapay-stage.com or https://api.zotapay.com

    public ZotaProfile(){}

    public ZotaProfile(String name, String merchantId, String merchantSecretKey, String apiBase) {
        this.name = name;
        this.merchantId = merchantId;
        this.merchantSecretKey = merchantSecretKey;
        this.apiBase = apiBase;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getMerchantSecretKey() { return merchantSecretKey; }
    public void setMerchantSecretKey(String merchantSecretKey) { this.merchantSecretKey = merchantSecretKey; }

    public String getDefaultEndpointId() { return defaultEndpointId; }
    public void setDefaultEndpointId(String defaultEndpointId) { this.defaultEndpointId = defaultEndpointId; }

    // GroupId removed from profile; still supported in request paths but not stored in profile

    public String getApiBase() { return apiBase; }
    public void setApiBase(String apiBase) { this.apiBase = apiBase; }
}
