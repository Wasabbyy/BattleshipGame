package cz.vse.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Queue;

public class GameManager {
    private static final Logger logger = LogManager.getLogger(GameManager.class);
    private static final Queue<String> waitingPlayers = new ConcurrentLinkedQueue<>();
    private static final Map<String, BattleshipGame> activeGames = new ConcurrentHashMap<>();

    public static void addPlayerToQueue(String username) {
        if (!waitingPlayers.isEmpty()) {
            String opponent = waitingPlayers.poll();
            BattleshipGame game = new BattleshipGame(username, opponent);
            activeGames.put(username, game);
            activeGames.put(opponent, game);
            logger.info("Game started: {} vs {}", username, opponent);
        } else {
            waitingPlayers.add(username);
            logger.info("{} is waiting for an opponent...", username);
        }
    }

    public static String getOpponent(String username) {
        BattleshipGame game = activeGames.get(username);
        if (game != null) {
            return game.getOpponent(username);
        }
        return null;
    }

    public static synchronized void removePlayer(String username) {
        waitingPlayers.remove(username);
        BattleshipGame game = activeGames.remove(username);
        if (game != null) {
            String opponent = game.getOpponent(username);
            activeGames.remove(opponent);
            logger.info("Game between {} and {} ended due to disconnection", username, opponent);
        }
    }

    public static BattleshipGame getGame(String username) {
        return activeGames.get(username);
    }
}
