package cz.vse.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

class ClientHandler implements Runnable {
    private static final Logger logger = LogManager.getLogger(ClientHandler.class);

    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private Timer afkTimer;
    private static final long AFK_TIMEOUT = 20 * 1000; // 20 seconds
    private final AtomicBoolean isConnected = new AtomicBoolean(true);

    // --- KEEP-ALIVE ---
    private Timer keepAliveTimer;
    private static final long KEEP_ALIVE_INTERVAL = 30 * 1000; // 30 seconds

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            String message;
            while ((message = in.readLine()) != null) {
                resetAfkTimer();
                if (message.equalsIgnoreCase("CHECK")) {
                    out.println("OK");
                    out.flush();
                    continue;
                }
                out.println("INFO: Welcome to Battleships Server! Please log in using 'LOGIN: username'");
                out.flush();
                if (message.startsWith("LOGIN: ")) {
                    username = message.substring(7).trim();
                    if (Server.activeUsers.contains(username)) {
                        out.println("ERROR: Username already in use. Try another one.");
                        logger.warn("Login attempt with already used username: {}", username);
                    } else {
                        Server.activeUsers.add(username);
                        Server.registerPlayerOutput(username, out);
                        GameManager.addPlayerToQueue(username);
                        out.println("INFO: Welcome, " + username + "! Waiting for an opponent...");
                        logger.info("User '{}' logged in and added to queue", username);
                        break;
                    }
                }
            }

            startAfkTimer();
            startKeepAlive();

            Thread exitMonitor = new Thread(() -> {
                try {
                    while (isConnected.get()) {
                        synchronized (in) {
                            if (in.ready()) {
                                String exitMessage = readInput();
                                resetAfkTimer();
                                if ("EXIT".equalsIgnoreCase(exitMessage)) {
                                    out.println("Goodbye, " + username + "!");
                                    logger.info("User '{}' disconnected voluntarily.", username);
                                    handleDisconnection();
                                    return;
                                }
                                if ("CHECK".equalsIgnoreCase(exitMessage)) {
                                    out.println("OK");
                                    out.flush();
                                }
                            }
                        }
                        Thread.sleep(100);
                    }
                } catch (IOException e) {
                    if (isConnected.get()) {
                        logger.debug("Exit monitor detected disconnection for '{}'", username);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            exitMonitor.start();

            while (isConnected.get()) {
                message = in.readLine();
                if (message == null) {
                    handleDisconnection();
                    return;
                }
                resetAfkTimer();
                if (message.equalsIgnoreCase("CHECK")) {
                    out.println("OK");
                    out.flush();
                    continue;
                }
                if ("READY".equalsIgnoreCase(message)) {
                    logger.info("Client '{}' is ready", username);
                    break;
                }
            }
            BattleshipGame game = null;  // Explicitly initialize to null
            try {
                while (isConnected.get()) {
                    game = GameManager.getGame(username);
                    if (game != null) {
                        break;  // Found a game, exit the loop
                    }
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Game waiting loop interrupted for '{}'", username);
                handleDisconnection();
                return;
            }

// Comprehensive null check
            if (game == null) {
                if (isConnected.get()) {
                    // Only log this as an error if we're still connected
                    logger.error("Failed to find game for player '{}'", username);
                    out.println("ERROR: Failed to start game");
                }
                handleDisconnection();
                return;
            }

// Now we can safely use the game object
            out.println("OPPONENT: " + game.getOpponent(username));
            out.flush();

            out.println("INFO: Place your ships using 'PLACE shipType x,y x,y' (5 ships total)");
            int shipsPlaced = 0;
            while (isConnected.get() && shipsPlaced < 5) {
                message = in.readLine();
                if (message == null) {
                    handleDisconnection();
                    return;
                }
                resetAfkTimer();
                if (message.equalsIgnoreCase("CHECK")) {
                    out.println("OK");
                    out.flush();
                    continue;
                }
                if (message.startsWith("PLACE ")) {
                    String[] parts = message.split(" ", 3);
                    if (parts.length < 3) {
                        out.println("ERROR: Invalid PLACE command format!");
                        logger.warn("Invalid PLACE format from '{}': {}", username, message);
                        continue;
                    }

                    String shipType = parts[1];
                    String positions = parts[2].replaceAll("\\(\\d+\\)", "").trim();
                    boolean success = game.placeShip(username, shipType, positions, out);

                    if (success) {
                        shipsPlaced++;
                    }
                }
            }

            if (!isConnected.get()) return;

            out.println("INFO: All ships placed! Waiting for opponent...");
            while (isConnected.get() && !game.isSetupComplete()) {
                Thread.sleep(500);
            }

            if (!isConnected.get()) return;

            out.println("INFO: Game started! Your opponent is " + game.getOpponent(username));
            out.flush();

            while (isConnected.get()) {
                message = in.readLine();
                if (message == null) {
                    handleDisconnection();
                    return;
                }
                resetAfkTimer();
                if (message.equalsIgnoreCase("CHECK")) {
                    out.println("OK");
                    out.flush();
                    continue;
                }
                if (message.startsWith("FIRE ")) {
                    String move = message.substring(5).trim();
                    game.processMove(username, move, out);
                }
            }

        } catch (SocketException e) {
            handleDisconnection();
        } catch (IOException | InterruptedException e) {
            logger.error("Connection lost with client: {}", username, e);
        } finally {
            cleanup();
        }
    }

    private void startAfkTimer() {
        if (!isConnected.get()) return;

        afkTimer = new Timer(true);
        afkTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (isConnected.get()) {
                    handleAfk();
                }
            }
        }, AFK_TIMEOUT);
    }

    private void resetAfkTimer() {
        if (afkTimer != null) {
            afkTimer.cancel();
        }
        if (isConnected.get()) {
            startAfkTimer();
        }
    }

    private void handleAfk() {
        if (username == null || !isConnected.get()) {
            return;
        }
        logger.info("User '{}' is AFK. Declaring as lost.", username);
        out.println("INFO: You have been inactive for too long. You lose!");
        String opponent = GameManager.getOpponent(username);
        if (opponent != null) {
            PrintWriter opponentOut = Server.getPlayerOutput(opponent);
            if (opponentOut != null) {
                opponentOut.println("INFO: Your opponent was inactive for too long. You win!");
            }
        }
        handleDisconnection();
    }

    private synchronized String readInput() throws IOException {
        return in.readLine();
    }

    private synchronized void handleDisconnection() {
        if (!isConnected.getAndSet(false)) {
            return;
        }

        if (username != null) {
            logger.info("User '{}' disconnected.", username);

            // Notify the opponent
            String opponent = GameManager.getOpponent(username);
            if (opponent != null) {
                PrintWriter opponentOut = Server.getPlayerOutput(opponent);
                if (opponentOut != null) {
                    opponentOut.println("INFO: Your opponent has left the game.");
                    logger.info("Notified opponent '{}' about '{}' disconnection.", opponent, username);
                }
            }
        }

        cleanup();
    }

    private void cleanup() {
        try {
            if (afkTimer != null) {
                afkTimer.cancel();
                afkTimer = null;
            }
            if (keepAliveTimer != null) {
                keepAliveTimer.cancel();
                keepAliveTimer = null;
            }
            if (username != null && isConnected.get()) {
                Server.activeUsers.remove(username);
                Server.removePlayerOutput(username);
                GameManager.removePlayer(username);

                BattleshipGame game = GameManager.getGame(username);
                if (game != null && game.getGameState() != GameState.FINISHED) {
                    game.forfeit(username);
                }
            }
        } catch (Exception e) {
            logger.error("Error during cleanup for {}", username, e);
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) {
                logger.error("Error closing resources for {}", username, e);
            }
        }
    }

    private void startKeepAlive() {
        keepAliveTimer = new Timer(true);
        keepAliveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isConnected.get()) {
                    this.cancel();
                    return;
                }
                try {
                    out.println("PING");
                    out.flush();
                    System.out.println("[" + username + "] Server odeslal PING klientovi.");
                } catch (Exception e) {
                    logger.error("Keep-alive PING failed for '{}'", username, e);
                    handleDisconnection();
                }
            }
        }, KEEP_ALIVE_INTERVAL, KEEP_ALIVE_INTERVAL);
    }
}