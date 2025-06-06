package cz.vse.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Timer;
import java.util.TimerTask;

class ClientHandler implements Runnable {
    private static final Logger logger = LogManager.getLogger(ClientHandler.class);

    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private Timer afkTimer;
    private static final long AFK_TIMEOUT = 2 * 60 * 1000; // 2 minuty

    // --- KEEP-ALIVE ---
    private Timer keepAliveTimer;
    private static final long KEEP_ALIVE_INTERVAL = 30 * 1000; // 30 sekund

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            String message;
            // Přihlašovací smyčka s prioritou na CHECK
            while ((message = in.readLine()) != null) {
                resetAfkTimer();
                if (message.equalsIgnoreCase("CHECK")) {
                    out.println("OK");
                    out.flush();
                    continue;
                }
                // První ne-CHECK zpráva → pošli uvítací zprávu a pokračuj v login procesu
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
                // Pokud to nebyl login, pokračuj ve smyčce
            }

            startAfkTimer();
            startKeepAlive();

            // Monitorování příkazu EXIT a CHECK v samostatném vlákně
            Thread exitMonitor = new Thread(() -> {
                try {
                    while (true) {
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
                } catch (IOException | InterruptedException e) {
                    logger.error("Error reading exit command for '{}'", username, e);
                }
            });
            exitMonitor.start();

            // Čekání na READY s prioritou na CHECK
            while (true) {
                message = in.readLine();
                resetAfkTimer();
                if (message == null) {
                    handleDisconnection();
                    return;
                }
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

            BattleshipGame game;
            while ((game = GameManager.getGame(username)) == null) {
                Thread.sleep(500);
            }

            out.println("OPPONENT: " + game.getOpponent(username));
            out.flush();

            out.println("INFO: Place your ships using 'PLACE shipType x,y x,y' (5 ships total)");
            int shipsPlaced = 0;
            while (shipsPlaced < 5) {
                message = in.readLine();
                resetAfkTimer();
                if (message == null) {
                    handleDisconnection();
                    return;
                }
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

            out.println("INFO: All ships placed! Waiting for opponent...");
            while (!game.isSetupComplete()) {
                Thread.sleep(500);
            }

            out.println("INFO: Game started! Your opponent is " + game.getOpponent(username));
            out.flush();

            // Hlavní smyčka pro zpracování zpráv během hry
            while (true) {
                message = in.readLine();
                resetAfkTimer();
                if (message == null) {
                    handleDisconnection();
                    return;
                }
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
        afkTimer = new Timer(true);
        afkTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                handleAfk();
            }
        }, AFK_TIMEOUT);
    }

    private void resetAfkTimer() {
        if (afkTimer != null) {
            afkTimer.cancel();
        }
        startAfkTimer();
    }

    private void handleAfk() {
        logger.info("User '{}' is AFK. Declaring as lost.", username);
        out.println("INFO: You have been inactive for too long. You lose!");
        PrintWriter opponentOut = Server.getPlayerOutput(GameManager.getOpponent(username));
        if (opponentOut != null) {
            opponentOut.println("INFO: Your opponent was inactive for too long. You win!");
        }
        handleDisconnection();
    }

    private synchronized String readInput() throws IOException {
        return in.readLine();
    }

    private synchronized void handleDisconnection() {
        logger.info("User '{}' disconnected.", username);
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
            if (username != null) {
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
            try (BufferedReader ignored = in; PrintWriter ignoredOut = out) {
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