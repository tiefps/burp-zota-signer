package burp.zota.controller;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.persistence.PersistedObject;
import burp.zota.config.ZotaConfig;
import burp.zota.profile.ProfileManager;
import burp.zota.profile.ZotaProfile;
import burp.zota.util.ZotaLogger;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

public class ZotaController {
    private final MontoyaApi api;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProfileManager profiles;
    private ZotaConfig config;

    public ZotaController(MontoyaApi api) {
        this.api = api;
        this.profiles = new ProfileManager(api);
        loadConfig();
        // sync active profile both ways
        if (config.activeProfileName != null && !config.activeProfileName.isEmpty()) {
            profiles.setActiveProfile(config.activeProfileName);
        } else if (profiles.getActiveProfile() != null) {
            config.activeProfileName = profiles.getActiveProfile().getName();
            saveConfig();
        }
    }

    public ProfileManager profiles() { return profiles; }

    public ZotaConfig getConfig() { return config; }

    public void setEnabled(boolean enabled) { config.enabled = enabled; saveConfig(); }
    public void setSignRepeater(boolean enabled) { config.signRepeater = enabled; saveConfig(); }
    public void setSignProxy(boolean enabled) { config.signProxy = enabled; saveConfig(); }
    public void setSignIntruder(boolean enabled) { config.signIntruder = enabled; saveConfig(); }

    public boolean shouldSign(ToolType tool) {
        if (!config.enabled) return false;
        if (tool == ToolType.REPEATER) return config.signRepeater;
        if (tool == ToolType.PROXY) return config.signProxy;
        if (tool == ToolType.INTRUDER) return config.signIntruder;
        return false;
    }

    public List<ZotaProfile> allProfiles() { return profiles.all(); }

    public ZotaProfile activeProfile() { return profiles.getActiveProfile(); }

    public void selectActiveProfile(String name) {
        if (name == null) return;
        profiles.setActiveProfile(name);
        config.activeProfileName = name;
        saveConfig();
    }

    public void addOrUpdateProfile(ZotaProfile p) {
        if (p == null) return;
        if (p.getName() == null || p.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Profile name is required");
        }
        profiles.addOrUpdate(p);
        if (config.activeProfileName == null || config.activeProfileName.isEmpty()) {
            config.activeProfileName = p.getName();
            saveConfig();
        }
    }

    public void removeProfile(String name) {
        if (name == null || name.isEmpty()) return;
        boolean wasActive = Objects.equals(config.activeProfileName, name);
        profiles.remove(name);
        if (wasActive) {
            ZotaProfile act = profiles.getActiveProfile();
            config.activeProfileName = act == null ? "" : act.getName();
            saveConfig();
        }
    }

    private void loadConfig() {
        try {
            PersistedObject store = api.persistence().extensionData();
            String json = store.getString("zota.config");
            if (json != null && !json.isEmpty()) {
                this.config = mapper.readValue(json, ZotaConfig.class);
            } else {
                this.config = new ZotaConfig();
            }
        } catch (Exception e) {
            this.config = new ZotaConfig();
            ZotaLogger.error("Failed to load config: " + e.getMessage());
        }
    }

    private void saveConfig() {
        try {
            PersistedObject store = api.persistence().extensionData();
            String json = mapper.writeValueAsString(config);
            store.setString("zota.config", json);
        } catch (Exception e) {
            ZotaLogger.error("Failed to save config: " + e.getMessage());
        }
    }
}

