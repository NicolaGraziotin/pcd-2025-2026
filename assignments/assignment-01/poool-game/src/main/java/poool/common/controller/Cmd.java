package poool.common.controller;

import poool.common.model.PooolModel;

/**
 * A command issued by the view (human input) and executed by the
 * {@link ActiveController} on the model.
 */
public interface Cmd {

    void execute(PooolModel model);
}
