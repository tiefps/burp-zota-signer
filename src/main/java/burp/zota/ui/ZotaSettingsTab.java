package burp.zota.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.zota.controller.ZotaController;
import burp.zota.profile.ZotaProfile;
import burp.zota.sample.SampleFactory;
import burp.zota.util.ZotaLogger;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Extension settings panel used to manage profiles, configure auto-signing behaviour, and generate sample requests.
 */
public class ZotaSettingsTab {
    private final JPanel root = new JPanel(new BorderLayout());
    private final ZotaController controller;
    private final MontoyaApi api;
    private final burp.zota.ZotaExtension ext;

    private final DefaultComboBoxModel<String> profileModel = new DefaultComboBoxModel<>();
    private JComboBox<String> profileCombo;
    private JTextField nameField;
    private JTextField merchantIdField;
    private JPasswordField secretField;
    private JTextField apiBaseField;
    private JTextField endpointIdField;

    public ZotaSettingsTab(MontoyaApi api, ZotaController controller, burp.zota.ZotaExtension ext) {
        this.api = api;
        this.controller = controller;
        this.ext = ext;

        buildUI();
        refreshProfiles();
    }

    private JPanel createProfilesPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Profiles"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(2,4,2,4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.NONE;

        profileCombo = new JComboBox<>(profileModel);
        profileCombo.addActionListener(e -> {
            String sel = (String) profileCombo.getSelectedItem();
            if (sel != null) {
                controller.selectActiveProfile(sel);
                populateFromActive();
            }
        });
        JButton btnRemove = new JButton("Remove");

        int row = 0;
        gc.gridx = 0; gc.gridy = row; panel.add(new JLabel("Active:"), gc);
        gc.gridx = 1; panel.add(profileCombo, gc);
        gc.gridx = 2; panel.add(btnRemove, gc);
        row++;

        nameField = new JTextField(16);
        merchantIdField = new JTextField(20);
        secretField = new JPasswordField(20);
        apiBaseField = new JTextField(28);
        endpointIdField = new JTextField(12);
        if (apiBaseField.getText().isEmpty()) {
            apiBaseField.setText("https://api.zotapay-stage.com");
        }

        Dimension fieldDim = new Dimension(420, nameField.getPreferredSize().height);
        profileCombo.setPreferredSize(fieldDim);
        nameField.setPreferredSize(fieldDim);
        merchantIdField.setPreferredSize(fieldDim);
        secretField.setPreferredSize(fieldDim);
        apiBaseField.setPreferredSize(fieldDim);
        endpointIdField.setPreferredSize(fieldDim);

        gc.gridx = 0; gc.gridy = row; panel.add(new JLabel("Name:"), gc);
        gc.gridx = 1; panel.add(nameField, gc); row++;
        gc.gridx = 0; gc.gridy = row; panel.add(new JLabel("MerchantID:"), gc);
        gc.gridx = 1; panel.add(merchantIdField, gc); row++;
        gc.gridx = 0; gc.gridy = row; panel.add(new JLabel("Secret:"), gc);
        gc.gridx = 1; panel.add(secretField, gc); row++;
        gc.gridx = 0; gc.gridy = row; panel.add(new JLabel("API Base:"), gc);
        gc.gridx = 1; panel.add(apiBaseField, gc); row++;
        gc.gridx = 0; gc.gridy = row; panel.add(new JLabel("Default EndpointID:"), gc);
        gc.gridx = 1; panel.add(endpointIdField, gc);

        JButton btnAdd = new JButton("Add/Update");
        gc.gridx = 2; panel.add(btnAdd, gc);

        btnAdd.addActionListener((ActionEvent e) -> saveProfile());
        btnRemove.addActionListener((ActionEvent e) -> removeSelectedProfile());

        return panel;
    }

    private JPanel createBehaviorPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Signing"));
        JCheckBox cbEnabled = new JCheckBox("Enable", controller.getConfig().enabled);
        JCheckBox cbRepeater = new JCheckBox("Repeater", controller.getConfig().signRepeater);
        JCheckBox cbProxy = new JCheckBox("Proxy", controller.getConfig().signProxy);
        JCheckBox cbIntruder = new JCheckBox("Intruder", controller.getConfig().signIntruder);
        cbEnabled.addActionListener(e -> controller.setEnabled(cbEnabled.isSelected()));
        cbRepeater.addActionListener(e -> controller.setSignRepeater(cbRepeater.isSelected()));
        cbProxy.addActionListener(e -> controller.setSignProxy(cbProxy.isSelected()));
        cbIntruder.addActionListener(e -> controller.setSignIntruder(cbIntruder.isSelected()));
        panel.add(new JLabel("Auto-sign:"));
        panel.add(cbEnabled);
        panel.add(cbRepeater);
        panel.add(cbProxy);
        panel.add(cbIntruder);
        return panel;
    }

    private JPanel createSamplesPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 3, 8, 8));
        panel.setBorder(new TitledBorder("Generate sample requests → Repeater"));
        addSampleButton(panel, "Deposit", "deposit");
        addSampleButton(panel, "Payout", "payout");
        addSampleButton(panel, "Order Status", "order-status");
        addSampleButton(panel, "Orders Report CSV", "orders-report");
        addSampleButton(panel, "Exchange Rates", "exchange-rates");
        addSampleButton(panel, "Current Balance", "current-balance");
        return panel;
    }

    /**
     * Adds a sample-generation button wired to {@link #sendSampleToRepeater(String)}.
     */
    private void addSampleButton(JPanel panel, String label, String kind) {
        JButton button = new JButton(label);
        button.addActionListener(e -> sendSampleToRepeater(kind));
        Dimension preferred = new Dimension(200, button.getPreferredSize().height);
        button.setPreferredSize(preferred);
        panel.add(button);
    }

    /**
     * Persists the currently edited profile fields and refreshes the combo-box selection.
     */
    private void saveProfile() {
        ZotaProfile p = new ZotaProfile();
        p.setName(nameField.getText().trim().isEmpty() ? "default" : nameField.getText().trim());
        p.setMerchantId(merchantIdField.getText().trim());
        char[] sec = secretField.getPassword();
        try {
            p.setMerchantSecretKey(new String(sec));
        } finally {
            java.util.Arrays.fill(sec, '\0');
        }
        p.setApiBase(apiBaseField.getText().trim());
        p.setDefaultEndpointId(endpointIdField.getText().trim());
        controller.addOrUpdateProfile(p);
        refreshProfiles();
        profileCombo.setSelectedItem(p.getName());
        populateFromActive();
        ZotaLogger.info("Saved profile: " + p.getName());
    }

    /**
     * Removes the currently selected profile (if any) and updates the combo-box contents.
     */
    private void removeSelectedProfile() {
        String sel = (String) profileCombo.getSelectedItem();
        if (sel != null) {
            controller.removeProfile(sel);
            refreshProfiles();
        }
    }

    public JComponent getRoot() {
        return root;
    }

    private void buildUI() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(8,8,8,8));

        JPanel profilesPanel = createProfilesPanel();
        JPanel behaviorPanel = createBehaviorPanel();
        JPanel samplesPanel = createSamplesPanel();

        content.add(profilesPanel);
        content.add(Box.createVerticalStrut(8));
        content.add(behaviorPanel);
        content.add(Box.createVerticalStrut(8));
        content.add(samplesPanel);

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.add(content);
        root.add(wrapper, BorderLayout.CENTER);
        populateFromActive();
    }

    private void refreshProfiles() {
        profileModel.removeAllElements();
        List<ZotaProfile> all = controller.allProfiles();
        for (ZotaProfile p : all) {
            profileModel.addElement(p.getName());
        }
        ZotaProfile act = controller.activeProfile();
        if (act != null) {
            profileModel.setSelectedItem(act.getName());
            populateFromActive();
        }
    }

    /**
     * Generates a sample request of the specified kind, signs it for preview, and sends it to Repeater.
     */
    private void sendSampleToRepeater(String kind) {
        ZotaProfile p = controller.activeProfile();
        if (p == null) {
            ZotaLogger.error("No active profile");
            return;
        }

        SampleFactory f = new SampleFactory(api, p);
        HttpRequest r = switch (kind) {
            case "deposit" -> f.deposit();
            case "payout" -> f.payout();
            case "order-status" -> f.orderStatus();
            case "orders-report" -> f.ordersReport();
            case "exchange-rates" -> f.exchangeRates();
            case "current-balance" -> f.currentBalance();
            default -> null;
        };
        if (r == null) {
            ZotaLogger.error("Unknown sample request kind: " + kind);
            return;
        }
        // Pre-sign before sending to Repeater so the visible tab shows modifications
        HttpRequest signed = ext.getSigner().signForPreview(r);
        api.repeater().sendToRepeater(signed, "Zota " + kind);
    }

    private void populateFromActive() {
        ZotaProfile p = controller.activeProfile();
        if (p == null) return;
        nameField.setText(p.getName() == null ? "" : p.getName());
        merchantIdField.setText(p.getMerchantId() == null ? "" : p.getMerchantId());
        secretField.setText(p.getMerchantSecretKey() == null ? "" : p.getMerchantSecretKey());
        apiBaseField.setText(p.getApiBase() == null ? "" : p.getApiBase());
        endpointIdField.setText(p.getDefaultEndpointId() == null ? "" : p.getDefaultEndpointId());
    }
}
