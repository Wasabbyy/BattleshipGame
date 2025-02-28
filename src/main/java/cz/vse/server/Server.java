package cz.vse.server;

import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;


public class Server {
    private static final int PORT = 12345;
    private static final ExecutorService clientThreads = Executors.newCachedThreadPool();
    public static final Set<String> activeUsers = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        System.out.println("🚀 Server is starting on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("✅ New client connected: " + clientSocket.getInetAddress());
                clientThreads.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
