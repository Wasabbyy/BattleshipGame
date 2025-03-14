package cz.vse.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

class ClientHandler implements Runnable {
    private static final Logger logger = LogManager.getLogger(ClientHandler.class);

    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            out.println("INFO: Welcome to Battleships Server! Please log in using 'LOGIN: username'");
            out.flush();

            String message;
            while ((message = in.readLine()) != null) {
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

            // Process READY message
            message = in.readLine();
            if ("READY".equalsIgnoreCase(message)) {
                logger.info("Client '{}' is ready", username);
            }

            BattleshipGame game;
            while ((game = GameManager.getGame(username)) == null) {
                Thread.sleep(500);
            }

            out.println("INFO: Place your ships using 'PLACE shipType x,y x,y' (5 ships total)");

            int shipsPlaced = 0;
            while (shipsPlaced < 5) {
                message = in.readLine();
                if (message == null) {
                    handleDisconnection();
                    return;
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

            while (true) {
                message = in.readLine();
                if (message == null) {
                    handleDisconnection();
                    return;
                }

                if ("EXIT".equalsIgnoreCase(message)) {
                    out.println("Goodbye, " + username + "!");
                    logger.info("User '{}' disconnected voluntarily.", username);
                    handleDisconnection();
                    return;
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

    private void handleDisconnection() {
        logger.info("User '{}' disconnected.", username);
        cleanup();
    }

    private void cleanup() {
        if (username != null) {
            Server.activeUsers.remove(username);
            Server.removePlayerOutput(username);
            //Server.checkAndShutdown();
        }
        try {
            clientSocket.close();
        } catch (IOException e) {
            logger.error("Error closing client socket for '{}'", username, e);
        }
    }
}