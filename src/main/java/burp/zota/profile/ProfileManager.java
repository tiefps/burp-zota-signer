package burp.zota.profile;

import burp.api.montoya.MontoyaApi;
import burp.zota.util.ZotaLogger;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public class ProfileManager {

    private final MontoyaApi api;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, ZotaProfile> profiles = new LinkedHashMap<>();
    private String active;

    public ProfileManager(MontoyaApi api) {
        this.api = api;
        load();
        if (profiles.isEmpty()) {
            // insert an empty default profile
            ZotaProfile p = new ZotaProfile("default", "", "", "https://api.zotapay-stage.com");
            profiles.put(p.getName(), p);
            active = p.getName();
        } else if (active == null) {
            active = profiles.keySet().iterator().next();
        }
    }

    public synchronized void addOrUpdate(ZotaProfile p) {
        profiles.put(p.getName(), p);
        if (active == null) active = p.getName();
        save();
    }

    public synchronized void remove(String name) {
        profiles.remove(name);
        if (Objects.equals(active, name)) {
            active = profiles.isEmpty() ? null : profiles.keySet().iterator().next();
        }
        save();
    }

    public synchronized List<ZotaProfile> all() {
        return new ArrayList<>(profiles.values());
    }

    public synchronized ZotaProfile byName(String name) {
        return profiles.get(name);
    }

    public synchronized ZotaProfile getActiveProfile() {
        return profiles.get(active);
    }

    public synchronized void setActiveProfile(String name) {
        if (profiles.containsKey(name)) {
            active = name;
            save();
        }
    }

    public ZotaProfile getActiveProfileOrWarn() {
        ZotaProfile p = getActiveProfile();
        if (p == null) {
            ZotaLogger.error("No active Zota profile configured");
        }
        return p;
    }

    private void load() {
        try {
            String json = api.persistence().extensionData().getString("zota.profiles");
            if (json != null && !json.isEmpty()) {
                readJson(json);
            }
            String act = api.persistence().extensionData().getString("zota.active");
            if (act != null && !act.isEmpty()) {
                active = act;
            }
        } catch (Throwable t) {
            ZotaLogger.error("Failed to load profiles from project: " + t.getMessage());
        }
    }

    private void save() {
        try {
            String json = mapper.writeValueAsString(profiles);
            api.persistence().extensionData().setString("zota.profiles", json);
            api.persistence().extensionData().setString("zota.active", active == null ? "" : active);
        } catch (Throwable t) {
            ZotaLogger.error("Failed to save profiles to project: " + t.getMessage());
        }
    }

    private void readJson(String json) throws Exception {
        Map<String, ZotaProfile> loaded = mapper.readValue(json, new TypeReference<>() {
        });
        profiles.clear();
        profiles.putAll(loaded);
    }
}
