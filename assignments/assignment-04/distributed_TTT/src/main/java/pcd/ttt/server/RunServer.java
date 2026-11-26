package pcd.ttt.server;

import pcd.ttt.common.GameLobby;
import pcd.ttt.common.RmiConfig;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CountDownLatch;

/**
 * Entry point of the server.
 */
public final class RunServer {

    private RunServer() {
    }

    public static void main(String[] args) {
        int port = RmiConfig.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Usage: RunServer [port]");
                return;
            }
        }

        try {
            LobbyImpl lobby = new LobbyImpl();
            GameLobby stub = (GameLobby) UnicastRemoteObject.exportObject(lobby, 0);

            Registry registry = startOrGetRegistry(port);
            registry.rebind(RmiConfig.LOBBY_NAME, stub);

            log("TTT lobby bound as '" + RmiConfig.LOBBY_NAME + "' on registry port " + port);
            log("Server ready. Press Ctrl+C to stop.");

            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log("Server exception: " + e);
            e.printStackTrace();
        }
    }

    private static Registry startOrGetRegistry(int port) throws RemoteException {
        try {
            Registry registry = LocateRegistry.createRegistry(port);
            log("Created a new RMI registry on port " + port);
            return registry;
        } catch (ExportException e) {
            log("An RMI registry is already running on port " + port + "; using it");
            return LocateRegistry.getRegistry(port);
        }
    }

    private static void log(String message) {
        System.out.println("[ " + System.currentTimeMillis() + " ][ Server ] " + message);
    }
}
