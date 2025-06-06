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
    private static final int GRID_SIZE = 10;

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

    public synchronized String getOpponent(String player) {
        return player.equals(player1) ? player2 : player1;
    }

    public synchronized boolean placeShip(String player, String shipType, String positions, PrintWriter out) {
        logger.info("Player '{}' is placing ship '{}' at '{}'", player, shipType, positions);

        shipType = shipType.replaceAll("\\(\\d+\\)", "").trim();
        char[][] grid = player.equals(player1) ? grid1 : grid2;
        List<Ship> fleet = player.equals(player1) ? fleet1 : fleet2;

        for (Ship ship : fleet) {
            if (ship.getType().equalsIgnoreCase(shipType)) {
                out.println("ERROR: You have already placed a " + shipType + "!");
                logger.warn("Player '{}' tried to place multiple '{}' ships.", player, shipType);
                return false;
            }
        }
        // In BattleshipGame.java
            Set<String> newShipPositions = new HashSet<>(Arrays.asList(positions.split(" ")));
            if (isAdjacent(player, newShipPositions)) {
                out.println("ERROR: Ships cannot be placed adjacent to each other!");
                logger.warn("Player '{}' tried to place a ship adjacent to another ship: {}", player, positions);
                return false;
            }




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

        Ship newShip = new Ship(shipType, shipCoords); // Přidáno uložení typu lodi
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

        out.println("SUCCESS: PLACE: " + shipType + " " + positions);
        logger.info("Player '{}' successfully placed ship '{}'", player, shipType);
        return true;
    }


    public synchronized boolean isSetupComplete() {
        if (gameState == GameState.IN_PROGRESS) {
            return true;
        }

        if (fleet1.size() == 5 && fleet2.size() == 5) {
            gameState = GameState.IN_PROGRESS;
            logger.info("Game between '{}' and '{}' is now in progress", player1, player2);

            PrintWriter out1 = Server.getPlayerOutput(player1);
            PrintWriter out2 = Server.getPlayerOutput(player2);

            if (out1 != null) {
                out1.println("INFO: Game Started: Your turn.");
            }
            if (out2 != null) {
                out2.println("INFO: Game Started: Opponent's turn");
            }

            return true;
        }
        return false;
    }

    // In BattleshipGame.java
// In BattleshipGame.java
    public synchronized void processMove(String player, String move, PrintWriter out) {
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

            if (x < 0 || x >= GRID_SIZE || y < 0 || y >= GRID_SIZE) {
                out.println("ERROR: Move out of bounds! Valid coordinates are between 0 and " + (GRID_SIZE - 1));
                return;
            }

            boolean hit = false;
            for (Ship ship : enemyFleet) {
                if (ship.registerHit(coord)) {
                    hit = true;
                    out.println("SUCCESS: HIT: " + coord);
                    if (opponentOut != null) opponentOut.println("SUCCESS: Opponent HIT: " + coord);
                    logger.info("Player '{}' hit a ship at '{}'", player, coord);

                    if (ship.isSunk()) {
                        String sunkCoords = String.join(" ", ship.getPositions());
                        out.println("SUCCESS: SUNK: " + sunkCoords);
                        if (opponentOut != null) opponentOut.println("SUNK: " + sunkCoords);
                        logger.info("Player '{}' sunk a ship at '{}'", player, sunkCoords);

                        if (allShipsSunk(enemyFleet)) {
                            out.println("INFO: You win! All enemy ships have been sunk.");
                            if (opponentOut != null) opponentOut.println("INFO: You lose! All your ships have been sunk.");
                            logger.info("Player '{}' wins the game by sinking all enemy ships.", player);
                            gameState = GameState.FINISHED;
                        }
                    }
                    break;
                }
            }

            if (!hit) {
                enemyGrid[x][y] = 'O';
                out.println("SUCCESS: MISS: " + coord);
                if (opponentOut != null) opponentOut.println("SUCCESS: Opponent MISS: " + coord);
                logger.info("Player '{}' missed at '{}'", player, coord);
                currentTurn = getOpponent(player);
                if (opponentOut != null) opponentOut.println("SUCCESS: Your turn");
                out.println("SUCCESS: Opponent's turn");
            } else {
                out.println("SUCCESS: Your turn again");
                if (opponentOut != null) opponentOut.println("SUCCESS: Opponent's turn again");
            }

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
    // In BattleshipGame.java
    private synchronized boolean isAdjacent(String player, Set<String> newShipPositions) {
        List<Ship> fleet = player.equals(player1) ? fleet1 : fleet2;
        Set<String> allPositions = new HashSet<>();
        for (Ship ship : fleet) {
            allPositions.addAll(ship.getPositions());
        }

        for (String pos : newShipPositions) {
            int x = Integer.parseInt(pos.split(",")[0]);
            int y = Integer.parseInt(pos.split(",")[1]);
            String[] adjacentPositions = {
                    (x-1) + "," + y, (x+1) + "," + y,
                    x + "," + (y-1), x + "," + (y+1),
                    (x-1) + "," + (y-1), (x-1) + "," + (y+1),
                    (x+1) + "," + (y-1), (x+1) + "," + (y+1)
            };
            for (String adjPos : adjacentPositions) {
                if (allPositions.contains(adjPos)) {
                    return true;
                }
            }
        }
        return false;
    }
    private synchronized boolean allShipsSunk(List<Ship> fleet) {
        for (Ship ship : fleet) {
            if (!ship.isSunk()) {
                return false;
            }
        }
        return true;
    }

    public synchronized GameState getGameState() {
        return gameState;
    }

    public synchronized void setGameState(GameState newState) {
        this.gameState = newState;
    }
}
