package pcd.ttt.common;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Immutable snapshot of a game board.
 */
public final class BoardState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int SIZE = 3;

    private final Symbol[] cells;
    private final Symbol turn;
    private final GameStatus status;

    public BoardState(Symbol[] cells, Symbol turn, GameStatus status) {
        if (cells.length != SIZE * SIZE) {
            throw new IllegalArgumentException("A board must contain exactly " + (SIZE * SIZE) + " cells");
        }
        this.cells = cells.clone();
        this.turn = turn;
        this.status = status;
    }

    public Symbol cellAt(int row, int col) {
        checkCoordinates(row, col);
        return cells[row * SIZE + col];
    }

    public Symbol[] cells() {
        return cells.clone();
    }

    public Symbol turn() {
        return turn;
    }

    public GameStatus status() {
        return status;
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public boolean isEmpty(int row, int col) {
        return cellAt(row, col) == null;
    }

    private static void checkCoordinates(int row, int col) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) {
            throw new IllegalArgumentException("Coordinates out of range: (" + row + ", " + col + ")");
        }
    }

    /**
     * Renders the board as plain text, useful for the command-line client.
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("     0   1   2\n");
        for (int row = 0; row < SIZE; row++) {
            sb.append("  ").append(row).append(" ");
            for (int col = 0; col < SIZE; col++) {
                Symbol cell = cells[row * SIZE + col];
                char c = (cell == null) ? ' ' : cell.name().charAt(0);
                sb.append(" ").append(c).append(" ");
                if (col < SIZE - 1) {
                    sb.append("|");
                }
            }
            sb.append('\n');
            if (row < SIZE - 1) {
                sb.append("    ---+---+---\n");
            }
        }
        sb.append("  turn: ").append(turn == null ? "-" : turn)
          .append("   status: ").append(status);
        return sb.toString();
    }

    @Override
    public String toString() {
        return "BoardState{cells=" + Arrays.toString(cells)
                + ", turn=" + turn + ", status=" + status + '}';
    }
}
