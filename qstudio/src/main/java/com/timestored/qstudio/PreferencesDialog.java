package com.timestored.qstudio;

import static com.timestored.theme.Theme.getFormRow;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.timestored.theme.Theme;

/**
 * Allow modification of all {@link MyPreferences}.
 */
class PreferencesDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final int WIDTH = 500;
    private final int HEIGHT = 600;

    private final MyPreferences myPreferences;
    private final List<PreferencesPanel> prefPanels = Lists.newArrayList();


    /**
     * @param frame Frame that I should be positioned relative to.
     */
    PreferencesDialog(final MyPreferences myPreferences, JFrame frame) {

        this.myPreferences = Preconditions.checkNotNull(myPreferences);
        setTitle("Preferences");
        setSize(WIDTH, HEIGHT);
        setLayout(new BorderLayout());
        setModalityType(JDialog.ModalityType.APPLICATION_MODAL);

        // construct appearance
        JTabbedPane tabpane = new JTabbedPane();

        AppearancePreferencesPanel appearancePreferencesPanel = new AppearancePreferencesPanel(myPreferences, this);
        tabpane.add("Appearance", appearancePreferencesPanel);
        prefPanels.add(appearancePreferencesPanel);

        QueryPreferencesPanel queryPanel = new QueryPreferencesPanel(myPreferences, this);
        tabpane.add("Connections", queryPanel);
        prefPanels.add(queryPanel);

        PreferencesPanel miscPanel = new MiscPreferencesPanel(myPreferences, this);
        tabpane.add("Misc", miscPanel);
        prefPanels.add(miscPanel);


        add(tabpane, BorderLayout.NORTH);
        add(getCloseSaveBox(), BorderLayout.SOUTH);
        setLocationRelativeTo(frame);

        setVisible(true);
    }


    private Box getCloseSaveBox() {
        // buttons
        JButton closeButton = new JButton(new AbstractAction("Cancel") {
            @Override
            public void actionPerformed(ActionEvent e) {

                for (PreferencesPanel pp : prefPanels) {
                    pp.cancel();
                    myPreferences.notifyListeners();
                }

                PreferencesDialog.this.setVisible(false);
                PreferencesDialog.this.dispatchEvent(new WindowEvent(
                        PreferencesDialog.this, WindowEvent.WINDOW_CLOSING));
            }
        });
        JButton saveButton = new JButton(new AbstractAction("Save") {
            @Override
            public void actionPerformed(ActionEvent e) {

                for (PreferencesPanel pp : prefPanels) {
                    pp.saveSettings();
                    myPreferences.notifyListeners();
                }

                PreferencesDialog.this.setVisible(false);
                PreferencesDialog.this.dispatchEvent(new WindowEvent(
                        PreferencesDialog.this, WindowEvent.WINDOW_CLOSING));
            }
        });
        Box closeBox = Box.createHorizontalBox();
        closeBox.add(Box.createHorizontalGlue());
        closeBox.add(saveButton);
        closeBox.add(Box.createHorizontalGlue());
        closeBox.add(closeButton);
        closeBox.add(Box.createHorizontalGlue());
        return closeBox;
    }


    private static class MiscPreferencesPanel extends PreferencesPanel {

        private static final long serialVersionUID = 1L;
        private JCheckBox saveWithWindowsLineEndingsCheckBox;
        private final JTextField hideNsTextField;
        private final JTextField hideFoldersTextField;

        public MiscPreferencesPanel(MyPreferences myPreferences, Component container) {
            super(myPreferences, container);
            Box panel = Box.createVerticalBox();
            panel.setBorder(Theme.getCentreBorder());

            saveWithWindowsLineEndingsCheckBox = new JCheckBox();
            String tooltipText = "<html>Files will be saved with \\r\\n line endings,<br/>" +
                    " compared to \\n standard found on linux/mac</html>";
            panel.add(getFormRow(saveWithWindowsLineEndingsCheckBox, "Save with \\r\\n Windows Line Ending:", tooltipText));
            panel.add(Box.createVerticalStrut(10));


            /* hide namespace config */
            hideNsTextField = new JTextField(20);
            tooltipText = "<html>These namespaces will not be displayed in the server tree panel.</html>";
            panel.add(getFormRow(hideNsTextField, "Hidden Namespaces:", null));
            panel.add(Box.createVerticalStrut(10));

            /* hide namespace config */
            hideFoldersTextField = new JTextField(20);
            tooltipText = "<html>These folders will not be searched when opening files using the Command Bar (Ctrl+P).</html>";
            panel.add(getFormRow(hideFoldersTextField, "Regex Filter To Hide Folders:", tooltipText));
            panel.add(Box.createVerticalStrut(10));

            setLayout(new BorderLayout());
            add(panel, BorderLayout.NORTH);

            refresh();
        }

        @Override
        void refresh() {
            saveWithWindowsLineEndingsCheckBox.setSelected(myPreferences.isSaveWithWindowsLineEndings());

            String hid = Joiner.on(" ").join(myPreferences.getHiddenNamespaces());
            hideNsTextField.setText(hid);

            hideFoldersTextField.setText(myPreferences.getIgnoreFilterRegex());
        }

        @Override
        void saveSettings() {
            myPreferences.setSaveWithWindowsLineEndings(saveWithWindowsLineEndingsCheckBox.isSelected());

            String[] ns = hideNsTextField.getText().split(" ");
            myPreferences.setHiddenNamespaces(ns);

            myPreferences.setIgnoreFilterRegex(hideFoldersTextField.getText());
        }

        @Override
        void cancel() {
        }

    }
}
