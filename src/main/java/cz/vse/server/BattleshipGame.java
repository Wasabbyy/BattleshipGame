package cz.vse.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintWriter;
import java.util.*;

public class BattleshipGame {
    private static final Logger logger = LogManager.getLogger(BattleshipGame.class);

    private String player1;
    private String player2;
    private char[][] grid1 = new char[10][10];
    private char[][] grid2 = new char[10][10];
    private String currentTurn;
    private int ships1 = 5;
    private int ships2 = 5;
    private GameState gameState;
    private List<Ship> fleet1 = new ArrayList<>();
    private List<Ship> fleet2 = new ArrayList<>();

    public BattleshipGame(String player1, String player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.currentTurn = player1;
        this.gameState = GameState.WAITING_FOR_PLAYERS;
        initializeGrid(grid1);
        initializeGrid(grid2);
        logger.info("New game created between '{}' and '{}'", player1, player2);
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
        logger.info("Player '{}' is placing ship '{}' at '{}'", player, shipType, positions);

        shipType = shipType.replaceAll("\\(\\d+\\)", "").trim();
        char[][] grid = player.equals(player1) ? grid1 : grid2;
        List<Ship> fleet = player.equals(player1) ? fleet1 : fleet2;

        Set<String> shipCoords = new HashSet<>();
        for (String coord : positions.split(" ")) {
            String[] parts = coord.split(",");
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());

            if (grid[x][y] == 'S') {
                out.println("ERROR: Ship overlaps at: " + coord);
                logger.warn("Player '{}' attempted to place a ship at an occupied location: {}", player, coord);
                return false;
            }
            shipCoords.add(coord);
        }

        Ship newShip = new Ship(shipCoords);
        fleet.add(newShip);

        for (String coord : shipCoords) {
            String[] parts = coord.split(",");
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            grid[x][y] = 'S';
        }

        if (isSetupComplete()) {
            out.println("All ships placed! Game is starting.");
        }

        out.println("SUCCESS: " + shipType + " " + positions);
        logger.info("Player '{}' successfully placed ship '{}'", player, shipType);
        return true;
    }

    public boolean isSetupComplete() {
        if (fleet1.size() == 5 && fleet2.size() == 5) {
            gameState = GameState.IN_PROGRESS;
            logger.info("Game between '{}' and '{}' is now in progress", player1, player2);

            PrintWriter p1 = Server.getPlayerOutput(player1);
            PrintWriter p2 = Server.getPlayerOutput(player2);

            if (p1 != null) {
                p1.println("GAME START");
                p1.println("Your turn");
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
        logger.info("Player '{}' attempting move '{}'", player, move);

        if (!isSetupComplete()) {
            out.println("ERROR: You must place all ships before starting the game!");
            logger.warn("Player '{}' attempted a move before setup completion", player);
            return;
        }

        if (!player.equals(currentTurn)) {
            out.println("ERROR: Not your turn!");
            logger.warn("Player '{}' attempted a move out of turn", player);
            return;
        }

        String[] parts = move.split(",");
        if (parts.length != 2) {
            out.println("ERROR: Invalid move format! Use: x,y");
            logger.warn("Player '{}' entered invalid move format: '{}'", player, move);
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
                        out.println("SUCCESS: SUNK: " + coord);
                        if (opponentOut != null) opponentOut.println("SUNK: " + coord);
                        logger.info("Player '{}' sunk a ship at '{}'", player, coord);
                    } else {
                        out.println("SUCCESS: HIT: " + coord);
                        if (opponentOut != null) opponentOut.println("HIT: " + coord);
                        logger.info("Player '{}' hit a ship at '{}'", player, coord);
                    }
                    break;
                }
            }

            if (!hit) {
                enemyGrid[x][y] = 'O';
                out.println("SUCCESS: MISS: " + coord);
                if (opponentOut != null) opponentOut.println("MISS: " + coord);
                logger.info("Player '{}' missed at '{}'", player, coord);
            }

            currentTurn = getOpponent(player);
            if (opponentOut != null) opponentOut.println("Your turn");
            out.println("Opponent's turn");

        } catch (NumberFormatException e) {
            out.println("ERROR: Invalid coordinates! Use numbers between 0-9.");
            logger.error("Invalid coordinate format entered by player '{}': '{}'", player, move, e);
        }
    }

    public void forfeit(String player) {
        if (gameState == GameState.FINISHED) return;

        String winner = getOpponent(player);
        gameState = GameState.FINISHED;
        logger.info("Player '{}' forfeited, '{}' wins by default.", player, winner);

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
