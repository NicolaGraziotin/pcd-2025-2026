package poool.multithread.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;

import poool.multithread.controller.ActiveController;
import poool.multithread.controller.KickCmd;
import poool.multithread.model.BallType;
import poool.multithread.model.Boundary;
import poool.multithread.model.Hole;
import poool.multithread.model.PooolModel;
import poool.multithread.model.V2d;

/**
 * Main window, mirroring {@code pcd.sketch02.view.ViewFrame}: it renders the
 * board (adapted from {@code pcd.sketch01.ViewFrame}) and asynchronously
 * forwards the human input (arrow keys) to the {@link ActiveController} as
 * {@link KickCmd}s.
 */
public class ViewFrame extends JFrame implements KeyListener {

    /** Impulse added to the human ball velocity at every key press. */
    private static final double KICK_STEP = 0.5;

    private final VisualiserPanel panel;
    private final ActiveController controller;

    public ViewFrame(ViewModel viewModel, ActiveController controller,
            Boundary bounds, List<Hole> holes) {
        setTitle("Poool - multithreaded version");
        setSize(1200, 800);
        setResizable(false);
        panel = new VisualiserPanel(viewModel, bounds, holes);
        getContentPane().add(panel);
        this.controller = controller;

        addKeyListener(this);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent ev) {
                System.exit(0);
            }

            @Override
            public void windowClosed(WindowEvent ev) {
                System.exit(0);
            }
        });
    }

    public void refresh() {
        panel.repaint();
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (controller == null) {
            return;
        }
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> controller.notifyNewCmd(new KickCmd(new V2d(0, KICK_STEP)));
            case KeyEvent.VK_DOWN -> controller.notifyNewCmd(new KickCmd(new V2d(0, -KICK_STEP)));
            case KeyEvent.VK_LEFT -> controller.notifyNewCmd(new KickCmd(new V2d(-KICK_STEP, 0)));
            case KeyEvent.VK_RIGHT -> controller.notifyNewCmd(new KickCmd(new V2d(KICK_STEP, 0)));
            default -> {
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    private static class VisualiserPanel extends JPanel {

        private final ViewModel viewModel;
        private final Boundary bounds;
        private final List<Hole> holes;

        VisualiserPanel(ViewModel viewModel, Boundary bounds, List<Hole> holes) {
            this.viewModel = viewModel;
            this.bounds = bounds;
            this.holes = holes;
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g2.clearRect(0, 0, getWidth(), getHeight());

            int w = getWidth();
            int h = getHeight() - 100;
            double scale = Math.min(w / (bounds.x1() - bounds.x0()),
                    h / (bounds.y1() - bounds.y0()));
            double ox = (w - (bounds.x1() - bounds.x0()) * scale) / 2;
            double oy = (h - (bounds.y1() - bounds.y0()) * scale) / 2 + 70;

            // board border
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2));
            g2.drawRect((int) ox, (int) oy,
                    (int) ((bounds.x1() - bounds.x0()) * scale),
                    (int) ((bounds.y1() - bounds.y0()) * scale));

            // holes
            g2.setColor(Color.DARK_GRAY);
            for (Hole hole : holes) {
                int[] c = toScreen(hole.center().x(), hole.center().y(), ox, oy, scale);
                int r = (int) (hole.radius() * scale);
                g2.fillOval(c[0] - r, c[1] - r, r * 2, r * 2);
            }

            // balls
            List<PooolModel.BallSnapshot> balls = viewModel.getBalls();
            g2.setStroke(new BasicStroke(1));
            g2.setColor(Color.BLACK);
            for (var b : balls) {
                if (b.type() != BallType.SMALL) {
                    continue;
                }
                int[] c = toScreen(b.pos().x(), b.pos().y(), ox, oy, scale);
                int r = Math.max(1, (int) (b.radius() * scale));
                g2.drawOval(c[0] - r, c[1] - r, r * 2, r * 2);
            }
            g2.setStroke(new BasicStroke(3));
            for (var b : balls) {
                int[] c = toScreen(b.pos().x(), b.pos().y(), ox, oy, scale);
                int r = Math.max(2, (int) (b.radius() * scale));
                if (b.type() == BallType.HUMAN) {
                    g2.setColor(Color.BLUE);
                    g2.drawOval(c[0] - r, c[1] - r, r * 2, r * 2);
                } else if (b.type() == BallType.BOT) {
                    g2.setColor(Color.RED);
                    g2.drawOval(c[0] - r, c[1] - r, r * 2, r * 2);
                }
            }

            // HUD
            g2.setColor(Color.BLUE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2.drawString("Human: " + viewModel.getHumanScore(), 20, 30);
            g2.setColor(Color.RED);
            g2.drawString("Bot: " + viewModel.getBotScore(), w - 120, 30);
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2.drawString("Small balls left: " + viewModel.getBallsRemaining()
                    + "   FPS: " + viewModel.getFps(), 20, 52);
            g2.drawString("Arrows: kick human ball (blue) - Bot ball is red", 20, 70);

            if (viewModel.isGameOver()) {
                g2.setColor(Color.MAGENTA.darker());
                g2.setFont(new Font("SansSerif", Font.BOLD, 28));
                String msg = "GAME OVER - " + viewModel.getWinner();
                g2.drawString(msg, w / 2 - 300, 60);
            }
        }

        private int[] toScreen(double x, double y, double ox, double oy, double scale) {
            int sx = (int) (ox + (x - bounds.x0()) * scale);
            int sy = (int) (oy + (bounds.y1() - y) * scale);
            return new int[] { sx, sy };
        }
    }
}
