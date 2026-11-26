package pcd.ttt.server;

import pcd.ttt.common.BoardState;
import pcd.ttt.common.GameSession;
import pcd.ttt.common.Symbol;
import pcd.ttt.common.exceptions.GameException;

import java.rmi.RemoteException;

/**
 * Server-side implementation of the per-player remote session.
 */
public final class GameSessionImpl implements GameSession {

    private final GameImpl game;
    private final Symbol symbol;
    private final String playerName;

    public GameSessionImpl(GameImpl game, Symbol symbol, String playerName) {
        this.game = game;
        this.symbol = symbol;
        this.playerName = playerName;
    }

    @Override
    public String gameName() {
        return game.name();
    }

    @Override
    public Symbol mySymbol() {
        return symbol;
    }

    @Override
    public String opponentName() {
        return game.opponentName(symbol);
    }

    @Override
    public BoardState currentState() {
        return game.currentState();
    }

    @Override
    public void makeMove(int row, int col) throws RemoteException, GameException {
        game.makeMove(symbol, row, col);
    }

    @Override
    public void leave() throws RemoteException {
        game.leave(symbol);
    }

    public String playerName() {
        return playerName;
    }
}
