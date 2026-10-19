package poool.taskbased.view;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Pre-game configuration popup, shown above the game board: lets the user
 * pick the ball layout (minimal / large / massive) and the number of
 * simulation workers (empty means the program default).
 */
public class ConfigDialog extends JDialog {

    /** User selection. {@code workers == null} means "use the default". */
    public record ConfigSelection(String board, Integer workers) {
    }

    private ConfigSelection result;
    private final JComboBox<String> boardBox =
            new JComboBox<>(new String[] { "minimal", "large", "massive" });
    private final JTextField workersField = new JTextField(10);
    private final JLabel errorLabel = new JLabel(" ");

    private ConfigDialog(Window parent) {
        super(parent, "Poool - New Game", ModalityType.APPLICATION_MODAL);
        boardBox.setSelectedItem("large");
        int cores = Runtime.getRuntime().availableProcessors();

        var panel = new JPanel(new GridBagLayout());
        var c = new GridBagConstraints();
        c.insets = new Insets(6, 8, 6, 8);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Ball layout:"), c);
        c.gridx = 1;
        panel.add(boardBox, c);

        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Workers (empty = default: " + cores + "):"), c);
        c.gridx = 1;
        panel.add(workersField, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        errorLabel.setForeground(java.awt.Color.RED);
        panel.add(errorLabel, c);

        var buttons = new JPanel();
        var start = new JButton("Start");
        var cancel = new JButton("Quit");
        buttons.add(start);
        buttons.add(cancel);
        c.gridy = 3;
        panel.add(buttons, c);

        start.addActionListener(e -> onStart());
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        getRootPane().setDefaultButton(start);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(panel);
    }

    private void onStart() {
        String text = workersField.getText().trim();
        Integer workers = null;
        if (!text.isEmpty()) {
            try {
                int n = Integer.parseInt(text);
                if (n <= 0) {
                    throw new NumberFormatException();
                }
                workers = n;
            } catch (NumberFormatException ex) {
                errorLabel.setText("Workers must be a positive integer (or empty).");
                pack();
                return;
            }
        }
        result = new ConfigSelection((String) boardBox.getSelectedItem(), workers);
        dispose();
    }

    /**
     * Shows the modal popup centred over {@code parent} (or over the screen
     * if {@code parent} is null).
     *
     * @return the user selection, or null if the dialog was cancelled/closed
     */
    public static ConfigSelection showDialog(Component parent) {
        Window window = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
        var dialog = new ConfigDialog(window);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return dialog.result;
    }
}

