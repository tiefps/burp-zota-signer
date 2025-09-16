package burp.zota.ui.menu;

import burp.api.montoya.core.ToolType;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse.SelectionContext;
import burp.zota.controller.ZotaController;
import burp.zota.profile.ZotaProfile;
import burp.zota.signer.ZotaSigner;
import burp.zota.util.ZotaLogger;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ZotaRepeaterContextMenu implements ContextMenuItemsProvider {

    private final ZotaController controller;
    private final ZotaSigner signer;

    public ZotaRepeaterContextMenu(ZotaController controller, ZotaSigner signer) {
        this.controller = controller;
        this.signer = signer;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        if (!event.isFromTool(ToolType.REPEATER)) {
            return Collections.emptyList();
        }

        Optional<MessageEditorHttpRequestResponse> maybeEditor = event.messageEditorRequestResponse();
        if (maybeEditor.isEmpty()) {
            return Collections.emptyList();
        }
        MessageEditorHttpRequestResponse editor = maybeEditor.get();
        if (editor.selectionContext() != SelectionContext.REQUEST) {
            return Collections.emptyList();
        }

        JMenu root = new JMenu("Zota Re-sign");
        addActiveProfileAction(root, editor);
        addProfileSwitcherActions(root, editor);
        if (root.getItemCount() == 0) {
            return Collections.emptyList();
        }
        List<Component> items = new ArrayList<>(1);
        items.add(root);
        return items;
    }

    private void addActiveProfileAction(JMenu root, MessageEditorHttpRequestResponse editor) {
        ZotaProfile active = controller.activeProfile();
        if (active == null) {
            JMenuItem disabled = new JMenuItem("Re-sign (no active profile)");
            disabled.setEnabled(false);
            root.add(disabled);
            return;
        }
        JMenuItem item = new JMenuItem("Re-sign (" + active.getName() + ")");
        item.addActionListener(e -> resignInEditor(editor, active));
        root.add(item);
    }

    private void addProfileSwitcherActions(JMenu root, MessageEditorHttpRequestResponse editor) {
        List<ZotaProfile> profiles = controller.allProfiles();
        if (profiles.size() <= 1) {
            return;
        }
        JMenu submenu = new JMenu("Re-sign with profile");
        ZotaProfile active = controller.activeProfile();
        for (ZotaProfile profile : profiles) {
            JMenuItem item = new JMenuItem(profileLabel(profile, active));
            item.addActionListener(e -> resignInEditor(editor, profile));
            submenu.add(item);
        }
        root.add(submenu);
    }

    private static String profileLabel(ZotaProfile profile, ZotaProfile active) {
        String name = profile.getName() == null ? "(unnamed)" : profile.getName();
        String activeName = active == null ? null : active.getName();
        if (activeName != null && activeName.equals(name)) {
            return name + " (active)";
        }
        return name;
    }

    private void resignInEditor(MessageEditorHttpRequestResponse editor, ZotaProfile profile) {
        try {
            var request = editor.requestResponse().request();
            var prepared = signer.applyProfileDefaults(request, profile);
            ZotaSigner.Result result = signer.sign(prepared, profile);
            var marked = signer.markManualProfile(result.request(), profile);
            if (marked != null) {
                editor.setRequest(marked);
            }
            if (result.annotations() != null && result.annotations().hasNotes()) {
                ZotaLogger.info(result.annotations().notes());
            } else {
                ZotaLogger.info("Re-signed request in Repeater using profile: " + profile.getName());
            }
        } catch (Exception ex) {
            ZotaLogger.error("Failed to re-sign request: " + ex.getMessage());
        }
    }
}
