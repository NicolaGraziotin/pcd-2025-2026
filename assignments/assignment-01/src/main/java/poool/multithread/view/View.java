package poool.multithread.view;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import poool.multithread.controller.ActiveController;
import poool.multithread.model.Boundary;
import poool.multithread.model.Hole;
import poool.multithread.model.PooolModel;
import poool.multithread.model.PooolObserver;

import java.util.List;

/**
 * View, mirroring {@code pcd.sketch02.view.View}: it observes the model and,
 * on every update, copies the immutable snapshot into the {@link ViewModel}
 * and asks the frame to repaint.
 */
public class View implements PooolObserver {

    private final ViewModel viewModel;
    private final ViewFrame frame;

    public View(ViewModel viewModel, ActiveController controller,
            Boundary bounds, List<Hole> holes) {
        this.viewModel = viewModel;
        this.frame = new ViewFrame(viewModel, controller, bounds, holes);
    }

    public void display() {
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }

    public JFrame getFrame() {
        return frame;
    }

    public void dispose() {
        try {
            SwingUtilities.invokeAndWait(frame::dispose);
        } catch (Exception ex) {
            frame.dispose();
        }
    }

    @Override
    public void modelUpdated(PooolModel model) {
        viewModel.update(model.getSnapshot());
        frame.refresh();
    }
}
