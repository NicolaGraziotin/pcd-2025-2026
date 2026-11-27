package poool.common.model;

/**
 * Observer of the game model: it is notified after every
 * simulation step so the view can refresh.
 */
public interface PooolObserver {

    void modelUpdated(PooolModel model);
}
