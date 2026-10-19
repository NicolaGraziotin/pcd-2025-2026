package poool.taskbased.model;

/**
 * Observer of the game model, mirroring
 * {@code pcd.sketch02.model.CounterObserver}: it is notified after every
 * simulation step so the view can refresh.
 */
public interface PooolObserver {

    void modelUpdated(PooolModel model);
}

