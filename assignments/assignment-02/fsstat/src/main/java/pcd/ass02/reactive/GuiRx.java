package pcd.ass02.reactive;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Minimal interactive GUI for the reactive version.
 */
public class GuiRx {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(GuiRx::create);
    }

    private static void create() {
        FSStatLib lib = new FSStatLib();

        JFrame f = new JFrame("FSStat - reactive (RxJava)");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setSize(800, 600);
        f.setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout());
        JTextField dirField = new JTextField("your directory", 25);
        JTextField maxField = new JTextField("1000", 10);
        JTextField nbField = new JTextField("4", 4);
        JButton startBtn = new JButton("Start");
        JButton stopBtn = new JButton("Stop");
        stopBtn.setEnabled(false);
        top.add(new JLabel("Dir:")); top.add(dirField);
        top.add(new JLabel("MaxFS:")); top.add(maxField);
        top.add(new JLabel("NB:")); top.add(nbField);
        top.add(startBtn); top.add(stopBtn);

        JTextArea area = new JTextArea();
        area.setEditable(false);
        f.add(top, BorderLayout.NORTH);
        f.add(new JScrollPane(area), BorderLayout.CENTER);

        final Disposable[] current = new Disposable[1];

        startBtn.addActionListener(e -> {
            String dir = dirField.getText().trim();
            long maxFS; int nb;
            try {
                maxFS = Long.parseLong(maxField.getText().trim());
                nb = Integer.parseInt(nbField.getText().trim());
            } catch (NumberFormatException ex) {
                area.setText("MaxFS/NB must be numbers\n");
                return;
            }
            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);
            area.setText("Scanning " + dir + " ...\n");
            current[0] = lib.getFSReportUpdates(dir, maxFS, nb)
                .subscribe(
                    r -> SwingUtilities.invokeLater(() -> {
                        if (current[0] == null || current[0].isDisposed()) return;
                        area.setText("Total files: " + r.getTotalFiles() + "\n" + r);
                    }),
                    err -> SwingUtilities.invokeLater(() -> {
                        area.append("\nFAILED: " + err + "\n");
                        startBtn.setEnabled(true);
                        stopBtn.setEnabled(false);
                    }),
                    () -> SwingUtilities.invokeLater(() -> {
                        area.append("\n--- scan complete ---\n");
                        startBtn.setEnabled(true);
                        stopBtn.setEnabled(false);
                    })
                );
        });

        stopBtn.addActionListener(e -> {
            if (current[0] != null && !current[0].isDisposed()) current[0].dispose();
            area.append("\n--- scan stopped by user ---\n");
            startBtn.setEnabled(true);
            stopBtn.setEnabled(false);
        });

        f.setVisible(true);
    }
}
