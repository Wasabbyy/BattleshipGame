package cz.vse.server;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

    public boolean placeShip(String player, int x, int y) {
        if (gameState == GameState.FINISHED) return false;

        char[][] grid = player.equals(player1) ? grid1 : grid2;
        Set<String> placedShips = player.equals(player1) ? placedShips1 : placedShips2;

        if (x < 0 || x >= 10 || y < 0 || y >= 10) return false;
        if (grid[x][y] == '~' && placedShips.size() < 5) {
            grid[x][y] = 'S';
            placedShips.add(x + "," + y);

            if (placedShips1.size() == 5 && placedShips2.size() == 5) {
                gameState = GameState.IN_PROGRESS;
            }
            return true;
        }
        return false;
    }

    public boolean isSetupComplete() {
        return gameState == GameState.IN_PROGRESS;
    }
    public void processMove(String player, String move, PrintWriter out) {
        if (!isSetupComplete()) {
            out.println("⏳ You must place all ships before starting the game!");
            return;
        }

        if (!player.equals(currentTurn)) {
            out.println("⏳ Not your turn!");
            return;
        }

        String[] parts = move.split(",");
        if (parts.length != 2) {
            out.println("❌ Invalid move format! Use: x,y");
            return;
        }

        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());

            char[][] enemyGrid = player.equals(player1) ? grid2 : grid1;
            PrintWriter opponentOut = Server.getPlayerOutput(getOpponent(player));

            if (enemyGrid[x][y] == 'S') {
                enemyGrid[x][y] = 'X';
                out.println("HIT:" + x + "," + y);
                if (opponentOut != null) opponentOut.println("HIT:" + x + "," + y);
            } else {
                enemyGrid[x][y] = 'O';
                out.println("MISS:" + x + "," + y);
                if (opponentOut != null) opponentOut.println("MISS:" + x + "," + y);
            }

            // ✅ Přepnutí tahu a poslání zpráv o tom, kdo hraje
            currentTurn = getOpponent(player);
            System.out.println("🔄 Switching turn to: " + currentTurn);  // Debug výpis na serveru

            if (opponentOut != null) opponentOut.println("Your turn");
            out.println("Opponent's turn");

        } catch (NumberFormatException e) {
            out.println("❌ Invalid coordinates! Use numbers between 0-9.");
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
                winnerOut.println("🏆 You won!");
            }
            if (loserOut != null) {
                loserOut.println("💀 You lost!"); // ✅ Přidáno
            }
            return true;
        }
        return false;
    }


    public void forfeit(String player) {
        if (gameState == GameState.FINISHED) return;

        String winner = getOpponent(player);
        gameState = GameState.FINISHED;

        System.out.println("🏆 " + winner + " wins by default!");

        PrintWriter winnerOut = Server.getPlayerOutput(winner);
        PrintWriter loserOut = Server.getPlayerOutput(player);

        if (winnerOut != null) {
            winnerOut.println("🏆 Your opponent forfeited! You win!");
        }
        if (loserOut != null) {
            loserOut.println("💀 You forfeited the game!");
        }
    }
}
