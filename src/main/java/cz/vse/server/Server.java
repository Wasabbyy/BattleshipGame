package cz.vse.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private static final Logger logger = LogManager.getLogger(Server.class);

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
        logger.info("Server is starting on port {}", PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("New client connected: {}", clientSocket.getInetAddress());
                clientThreads.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void checkAndShutdown() {
        System.out.println("Checking if server should shut down...");

        if (activeUsers.isEmpty()) {
            System.out.println("No active users remaining. Shutting down server...");
            logger.info("No active users remaining. Server is shutting down.");
            System.exit(0); // Vypne server
        } else {
            System.out.println("Active users still connected: " + activeUsers);
            logger.info("Active users remaining: {}", activeUsers.size());
        }
    }



}
