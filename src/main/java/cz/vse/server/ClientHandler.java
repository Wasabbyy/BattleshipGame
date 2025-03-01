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

            out.println("⚓ Place your ships using 'PLACE shipType x,y x,y' (5 ships total)");

            int shipsPlaced = 0;
            while (shipsPlaced < 5) {
                message = in.readLine();
                if (message == null) {
                    System.out.println("Player " + username + " disconnected during setup.");
                    throw new IOException("Client disconnected.");
                }

                if (message.startsWith("PLACE ")) {
                    System.out.println("📩 Received command from client: " + message); // Debug výpis

                    String[] parts = message.split(" ", 3); // Rozdělení na ["PLACE", "Battleship (4)", "2,3 3,3 4,3 5,3"]

                    if (parts.length < 3) {
                        out.println("ERROR: Invalid PLACE command format!");
                        System.out.println("⚠ Invalid PLACE format: " + message);
                        continue;
                    }

                    String shipType = parts[1];// ✅ Odstraníme číslo v závorce
                    String positions = parts[2].replaceAll("\\(\\d+\\)", "").trim();

                    System.out.println("🚢 Placing ship: " + shipType + " at positions: " + positions); // Debug výpis

                    boolean success = game.placeShip(username, shipType, positions, out);

                    if (success) {
                        shipsPlaced++;
                        out.println("Ship placed at " + positions + " (" + shipsPlaced + "/5)");
                        System.out.println("✅ Ship placed successfully: " + shipType);
                    } else {
                        out.println("Invalid position or already occupied!");
                        System.out.println("❌ Failed to place ship: " + shipType);
                    }
                }


            }

            out.println("All ships placed! Waiting for opponent...");
            while (!game.isSetupComplete()) {
                Thread.sleep(500);
            }

            out.println("Game started! Your opponent is " + game.getOpponent(username));
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
                    out.println("Invalid command! Use 'FIRE x,y' to shoot.");
                }
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("Connection lost with client: " + username);
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
