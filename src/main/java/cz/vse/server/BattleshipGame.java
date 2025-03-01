package cz.vse.server;

import java.io.PrintWriter;
import java.util.*;

public class BattleshipGame {
    private String player1;
    private String player2;
    private char[][] grid1 = new char[10][10];
    private char[][] grid2 = new char[10][10];
    private String currentTurn;
    private int ships1 = 5;
    private int ships2 = 5;
    private Set<String> placedShips1 = new HashSet<>();
    private Set<String> placedShips2 = new HashSet<>();
    private GameState gameState;
    private List<Ship> fleet1 = new ArrayList<>();
    private List<Ship> fleet2 = new ArrayList<>();
    private Set<String> placedShipTypes1 = new HashSet<>();
    private Set<String> placedShipTypes2 = new HashSet<>();

    public BattleshipGame(String player1, String player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.currentTurn = player1;
        this.gameState = GameState.WAITING_FOR_PLAYERS;
        initializeGrid(grid1);
        initializeGrid(grid2);
    }

    private void initializeGrid(char[][] grid) {
        for (int i = 0; i < 10; i++) {
            Arrays.fill(grid[i], '~');
        }
    }

    public String getOpponent(String player) {
        return player.equals(player1) ? player2 : player1;
    }

    public boolean placeShip(String player, String shipType, String positions, PrintWriter out) {
        // ✅ Odstraníme případné mezery a čísla v názvu
        shipType = shipType.replaceAll("\\(\\d+\\)", "").trim();

        char[][] grid = player.equals(player1) ? grid1 : grid2;
        List<Ship> fleet = player.equals(player1) ? fleet1 : fleet2;
        Set<String> placedShipTypes = player.equals(player1) ? placedShipTypes1 : placedShipTypes2;

        if (placedShipTypes.contains(shipType)) {
            out.println("ERROR: You have already placed a " + shipType);
            return false;
        }

        Set<String> shipCoords = new HashSet<>();
        for (String coord : positions.split(" ")) {
            String[] parts = coord.split(",");
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());

            if (grid[x][y] == 'S') {
                out.println("ERROR: Ship overlaps at: " + coord);
                return false;
            }
            shipCoords.add(coord);
        }

        Ship newShip = new Ship(shipCoords);
        fleet.add(newShip);
        placedShipTypes.add(shipType); // ✅ Uložíme správný typ lodi

        for (String coord : shipCoords) {
            String[] parts = coord.split(",");
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            grid[x][y] = 'S';
        }

        if (player.equals(player1)) ships1++;
        else ships2++;

        if (isSetupComplete()) {
            out.println("All ships placed! Game is starting.");
        }

        out.println("SUCCESS: " + shipType + " " + positions);
        return true;
    }




    public boolean isSetupComplete() {
        if (fleet1.size() == 5 && fleet2.size() == 5) {
            gameState = GameState.IN_PROGRESS;  // ✅ Nastavíme, že hra začala

            // ✅ Oznámíme hráčům, že hra začala!
            PrintWriter p1 = Server.getPlayerOutput(player1);
            PrintWriter p2 = Server.getPlayerOutput(player2);

            if (p1 != null) {
                p1.println("GAME START");
                p1.println("Your turn");  // ✅ První tah patří player1
            }
            if (p2 != null) {
                p2.println("GAME START");
                p2.println("Opponent's turn");
            }

            return true;
        }
        return false;
    }

    public void processMove(String player, String move, PrintWriter out) {
        if (!isSetupComplete()) {
            out.println("You must place all ships before starting the game!");
            return;
        }

        if (!player.equals(currentTurn)) {
            out.println("Not your turn!");
            return;
        }

        String[] parts = move.split(",");
        if (parts.length != 2) {
            out.println("Invalid move format! Use: x,y");
            return;
        }

        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            String coord = x + "," + y;

            char[][] enemyGrid = player.equals(player1) ? grid2 : grid1;
            List<Ship> enemyFleet = player.equals(player1) ? fleet2 : fleet1;
            PrintWriter opponentOut = Server.getPlayerOutput(getOpponent(player));

            boolean hit = false;
            for (Ship ship : enemyFleet) {
                if (ship.registerHit(coord)) {
                    hit = true;
                    if (ship.isSunk()) {
                        out.println("SUNK: " + coord);
                        if (opponentOut != null) opponentOut.println("SUNK: " + coord);
                    } else {
                        out.println("HIT: " + coord);
                        if (opponentOut != null) opponentOut.println("HIT: " + coord);
                    }
                    break;
                }
            }

            if (!hit) {
                enemyGrid[x][y] = 'O';
                out.println("MISS: " + coord);
                if (opponentOut != null) opponentOut.println("MISS: " + coord);
            }

            // Přepnutí tahu
            currentTurn = getOpponent(player);
            if (opponentOut != null) opponentOut.println("Your turn");
            out.println("Opponent's turn");

        } catch (NumberFormatException e) {
            out.println("Invalid coordinates! Use numbers between 0-9.");
        }
    }



    private boolean checkWin() {
        if (ships1 == 0 || ships2 == 0) {
            String winner = (ships1 == 0) ? player2 : player1;
            String loser = (ships1 == 0) ? player1 : player2;
            gameState = GameState.FINISHED;

            PrintWriter winnerOut = Server.getPlayerOutput(winner);
            PrintWriter loserOut = Server.getPlayerOutput(loser);

            if (winnerOut != null) {
                winnerOut.println("You won!");
            }
            if (loserOut != null) {
                loserOut.println("You lost!"); // ✅ Přidáno
            }
            return true;
        }
        return false;
    }


    public void forfeit(String player) {
        if (gameState == GameState.FINISHED) return;

        String winner = getOpponent(player);
        gameState = GameState.FINISHED;

        System.out.println( winner + " wins by default!");

        PrintWriter winnerOut = Server.getPlayerOutput(winner);
        PrintWriter loserOut = Server.getPlayerOutput(player);

        if (winnerOut != null) {
            winnerOut.println("Your opponent forfeited! You win!");
        }
        if (loserOut != null) {
            loserOut.println("You forfeited the game!");
        }
    }
}
