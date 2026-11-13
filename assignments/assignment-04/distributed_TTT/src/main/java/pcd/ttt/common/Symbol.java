package pcd.ttt.common;

/**
 * The two possible marks of a Tic-Tac-Toe game.
 */
public enum Symbol {
    X, O;

    public Symbol other() {
        return this == X ? O : X;
    }
}
