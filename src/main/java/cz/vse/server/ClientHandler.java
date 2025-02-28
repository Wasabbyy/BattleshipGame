package cz.vse.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
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
                        System.out.println("🔑 User logged in: " + username);
                        break;
                    }
                } else {
                    out.println("Invalid command. Use 'LOGIN: username'");
                }
            }

            // ✅ Získání hry před fází umisťování lodí
            BattleshipGame game;
            while ((game = GameManager.getGame(username)) == null) {
                Thread.sleep(500);
            }

            // ⚓ Fáze umisťování lodí
            out.println("⚓ Place your ships using 'PLACE x,y' (5 ships total)");

            int shipsPlaced = 0;
            while (shipsPlaced < 5) {
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
                            }
                        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                            out.println("❌ Invalid coordinates! Use numbers between 0-9.");
                        }
                    } else {
                        out.println("❌ Invalid command! Use 'PLACE x,y'");
                    }
                }
            }
            out.println("🎮 All ships placed! Waiting for opponent...");

            // ✅ Hra začíná
            out.println("🎮 Game started! Your opponent is " + game.getOpponent(username));

            while ((message = in.readLine()) != null) {
                if ("EXIT".equalsIgnoreCase(message)) {
                    out.println("Goodbye, " + username + "!");
                    break;
                }
                System.out.println("📩 [" + username + "] Sent: " + message);
                game.processMove(username, message, out);
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("❌ Connection lost with client.");
            if (username != null) {
                Server.activeUsers.remove(username);
                GameManager.removePlayer(username);
                BattleshipGame game = GameManager.getGame(username);
                if (game != null) {
                    String opponent = game.getOpponent(username);
                    System.out.println("🏆 " + opponent + " wins by default!");
                    GameManager.removePlayer(opponent);
                }
            }
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
