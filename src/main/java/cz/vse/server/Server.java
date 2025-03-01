package cz.vse.server;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;


public class Server {
    private static final int PORT = 12345;
    private static final ExecutorService clientThreads = Executors.newCachedThreadPool();
    public static final Set<String> activeUsers = ConcurrentHashMap.newKeySet();
    private static final Map<String, PrintWriter> playerOutputs = new ConcurrentHashMap<>();

    public static void registerPlayerOutput(String username, PrintWriter out) {
        playerOutputs.put(username, out);
    }

    public static PrintWriter getPlayerOutput(String username) {
        return playerOutputs.get(username);
    }

    public static void removePlayerOutput(String username) {
        playerOutputs.remove(username);
    }

    public static void main(String[] args) {
        System.out.println("Server is starting on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                clientThreads.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
