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
                        Server.registerPlayerOutput(username, out); // ✅ Uložíme PrintWriter hráče
                        GameManager.addPlayerToQueue(username);
                        out.println("Welcome, " + username + "! Waiting for an opponent...");
                        break;
                    }
                }

            }

            BattleshipGame game;
            while ((game = GameManager.getGame(username)) == null) {
                Thread.sleep(500);
            }

            out.println("⚓ Place your ships using 'PLACE x,y' (5 ships total)");
            out.flush();

            int shipsPlaced = 0;
            while (shipsPlaced < 5) {
                message = in.readLine();
                if (message == null) {
                    System.out.println("❌ Player " + username + " disconnected during setup.");
                    throw new IOException("Client disconnected.");
                }

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

            // ✅ **Přidání herní smyčky pro střelbu**
            while (true) {
                message = in.readLine();
                if (message == null) break;

                if ("EXIT".equalsIgnoreCase(message)) {
                    out.println("Goodbye, " + username + "!");
                    break;
                }

                if (message.startsWith("FIRE ")) {
                    String move = message.substring(5).trim();
                    game.processMove(username, move, out); // 🔥 Volání processMove()!
                } else {
                    out.println("❌ Invalid command! Use 'FIRE x,y' to shoot.");
                }
            }

        }catch (IOException | InterruptedException e) {
        System.out.println("❌ Connection lost with client: " + username);
        if (username != null) {
            Server.activeUsers.remove(username);
            BattleshipGame game = GameManager.getGame(username);

            if (game != null) {
                String opponent = game.getOpponent(username);
                game.forfeit(username); // ✅ Ukončí hru a oznámí výhru soupeři
                GameManager.removePlayer(username);

                // ✅ Informuj soupeře, že vyhrál
                PrintWriter opponentOut = Server.getPlayerOutput(opponent);
                if (opponentOut != null) {
                    opponentOut.println("🏆 Your opponent disconnected! You win by default.");
                }
            }
        }

    } finally {
            Server.removePlayerOutput(username); // ✅ Odebrání hráče při odchodu
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
