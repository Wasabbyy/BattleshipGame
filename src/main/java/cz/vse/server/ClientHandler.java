package cz.vse.server;

import java.io.*;
import java.net.Socket;

class ClientHandler implements Runnable {
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

            out.println("Welcome to Battleships Server! Please log in using 'LOGIN: username'");
            out.flush();

            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("LOGIN: ")) {
                    username = message.substring(7).trim();
                    if (Server.activeUsers.contains(username)) {
                        out.println("Username already in use. Try another one.");
                    } else {
                        Server.activeUsers.add(username);
                        GameManager.addPlayerToQueue(username);
                        out.println("Welcome, " + username + "! Waiting for an opponent...");
                        break;
                    }
                } else {
                    out.println("Invalid command. Use 'LOGIN: username'");
                }
            }

            BattleshipGame game;
            while ((game = GameManager.getGame(username)) == null) {
                Thread.sleep(500);
            }

            out.println("⚓ Place your ships using 'PLACE x,y' (5 ships total)");
            out.flush();

            int shipsPlaced = 0;
            while (shipsPlaced < 5) { // Oprava: 5 lodí místo 2
                message = in.readLine();
                if (message.startsWith("PLACE ")) {
                    String[] parts = message.substring(6).trim().split(",");
                    if (parts.length == 2) {
                        try {
                            int x = Integer.parseInt(parts[0].trim());
                            int y = Integer.parseInt(parts[1].trim());
                            if (game.placeShip(username, x, y)) {
                                shipsPlaced++;
                                out.println("✅ Ship placed at " + x + "," + y + " (" + shipsPlaced + "/5)");
                            } else {
                                out.println("⚠ Invalid position or already occupied!");
                                out.flush();
                            }
                        } catch (NumberFormatException e) {
                            out.println("❌ Invalid coordinates! Use numbers between 0-9.");
                        }
                    } else {
                        out.println("❌ Invalid command! Use 'PLACE x,y'");
                    }
                }
            }

            out.println("🎮 All ships placed! Waiting for opponent...");
            while (!game.isSetupComplete()) {
                Thread.sleep(500);
            }

            out.println("🎮 Game started! Your opponent is " + game.getOpponent(username));
            out.flush();
        } catch (IOException | InterruptedException e) {
            GameManager.removePlayer(username);
            BattleshipGame game = GameManager.getGame(username);
            if (game != null) {
                game.forfeit(username);
            }
        }
    }
}
