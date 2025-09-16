package burp.zota.util;

import burp.api.montoya.MontoyaApi;

public final class ZotaLogger {
    private static MontoyaApi api;

    private ZotaLogger(){}

    public static void init(MontoyaApi a) {
        api = a;
        info("Zota extension loaded");
    }

    public static void info(String s) {
        if (api != null) api.logging().logToOutput("[Zota] " + s);
    }

    public static void error(String s) {
        if (api != null) api.logging().logToError("[Zota] " + s);
    }
}