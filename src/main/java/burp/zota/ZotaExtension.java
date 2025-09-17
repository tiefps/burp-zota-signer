package burp.zota;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.ui.UserInterface;
import burp.zota.controller.ZotaController;
import burp.zota.profile.ZotaProfile;
import burp.zota.signer.ZotaSigner;
import burp.zota.ui.ZotaSettingsTab;
import burp.zota.ui.menu.ZotaRepeaterContextMenu;
import burp.zota.util.ZotaLogger;

public class ZotaExtension implements BurpExtension, HttpHandler {

    private MontoyaApi api;
    private ZotaController controller;
    private ZotaSigner signer;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("Zota Signer");
        ZotaLogger.init(api);

        this.controller = new ZotaController(api);
        this.signer = new ZotaSigner(api, controller.profiles());

        api.userInterface().registerContextMenuItemsProvider(new ZotaRepeaterContextMenu(controller, signer));

        // UI (ensure components created on the EDT)
        UserInterface ui = api.userInterface();
        javax.swing.SwingUtilities.invokeLater(() -> {
            ZotaSettingsTab tab = new ZotaSettingsTab(api, controller, this);
            ui.registerSuiteTab("Zota", tab.getRoot());
        });

        // HTTP signing
        Http http = api.http();
        http.registerHttpHandler(this);

        String version = ZotaExtension.class.getPackage() != null
                ? ZotaExtension.class.getPackage().getImplementationVersion()
                : null;
        if (version == null || version.isBlank()) {
            version = "dev";
        }
        ZotaLogger.info("Zota Signer v" + version + " initialized.");
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent request) {
        ToolType tool = request.toolSource().toolType();

        boolean should = controller.shouldSign(tool);

        // Always analyze for annotations; only apply modifications if signing is enabled
        ZotaSigner.Result result = signer.signIfZota(request);
        if (!should) {
            if (result.annotations() != null) {
                return RequestToBeSentAction.continueWith(request, result.annotations());
            }
            return RequestToBeSentAction.continueWith(request);
        }
        if (result.annotations() != null) {
            return RequestToBeSentAction.continueWith(result.request(), result.annotations());
        }
        return RequestToBeSentAction.continueWith(result.request());
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        // No response manipulation required; could add callback/redirect signature verification display later.
        return ResponseReceivedAction.continueWith(responseReceived);
    }

    // Utility for the UI
    public ZotaProfile getActiveProfile() { return controller.activeProfile(); }
    public ZotaController getController() { return controller; }

    public ZotaSigner getSigner() {
        return signer;
    }
}
