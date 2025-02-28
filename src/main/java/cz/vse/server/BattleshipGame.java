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
    private int ships1 = 2;
    private int ships2 = 2;
    private Set<String> placedShips1 = new HashSet<>();
    private Set<String> placedShips2 = new HashSet<>();
    private boolean setupComplete1 = false;
    private boolean setupComplete2 = false;
    private boolean gameFinished = false;

    public BattleshipGame(String player1, String player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.currentTurn = player1;
        initializeGrid(grid1);
        initializeGrid(grid2);
        System.out.println("🎮 Game started! " + player1 + " vs " + player2);
        System.out.println("👉 " + player1 + " starts!");
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
        if (gameFinished) return false;
        char[][] grid = player.equals(player1) ? grid1 : grid2;
        Set<String> placedShips = player.equals(player1) ? placedShips1 : placedShips2;

        if (grid[x][y] == '~' && placedShips.size() < 2) {
            grid[x][y] = 'S';
            placedShips.add(x + "," + y);
            if (placedShips.size() == 2) {
                if (player.equals(player1)) setupComplete1 = true;
                else setupComplete2 = true;
            }
            return true;
        }
        return false;
    }

    public boolean isSetupComplete() {
        return setupComplete1 && setupComplete2;
    }

    public void processMove(String player, String move, PrintWriter out) {
        if (gameFinished) return;
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

            if (enemyGrid[x][y] == 'S') {
                enemyGrid[x][y] = 'X';
                if (player.equals(player1)) ships2--;
                else ships1--;
                out.println("🎯 Hit at " + x + "," + y + "!");
                if (checkWin(out)) return;
            } else if (enemyGrid[x][y] == '~') {
                enemyGrid[x][y] = 'O';
                out.println("💦 Miss at " + x + "," + y + "!");
            } else {
                out.println("⚠ Already shot here!");
            }

            currentTurn = getOpponent(player);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            out.println("❌ Invalid coordinates! Use numbers between 0-9.");
        }
    }

    private boolean checkWin(PrintWriter out) {
        if (ships1 == 0) {
            out.println("🏆 " + player2 + " won!");
            gameFinished = true;
            return true;
        } else if (ships2 == 0) {
            out.println("🏆 " + player1 + " won!");
            gameFinished = true;
            return true;
        }
        return false;
    }

    public void forfeit(String player) {
        if (gameFinished) return;
        String winner = getOpponent(player);
        System.out.println("🏆 " + winner + " wins by default!");
        gameFinished = true;
    }
}
