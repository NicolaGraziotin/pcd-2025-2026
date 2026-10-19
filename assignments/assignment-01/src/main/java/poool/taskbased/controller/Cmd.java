package poool.taskbased.controller;

import poool.taskbased.model.PooolModel;

/**
 * A command issued by the view (human input) and executed by the
 * {@link ActiveController} on the model, mirroring
 * {@code pcd.sketch02.controller.Cmd}.
 */
public interface Cmd {

    void execute(PooolModel model);
}

