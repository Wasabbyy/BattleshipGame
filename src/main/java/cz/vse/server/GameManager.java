package cz.vse.server;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Queue;

public class GameManager {
    private static final Queue<String> waitingPlayers = new ConcurrentLinkedQueue<>();
    private static final Map<String, BattleshipGame> activeGames = new ConcurrentHashMap<>();

    public static synchronized void addPlayerToQueue(String username) {
        if (!waitingPlayers.isEmpty()) {
            String opponent = waitingPlayers.poll();
            BattleshipGame game = new BattleshipGame(username, opponent);
            activeGames.put(username, game);
            activeGames.put(opponent, game);
            System.out.println("🎮 Game started: " + username + " vs " + opponent);
        } else {
            waitingPlayers.add(username);
            System.out.println("⌛ " + username + " is waiting for an opponent...");
        }
    }

    public static synchronized void removePlayer(String username) {
        waitingPlayers.remove(username);

        BattleshipGame game = activeGames.remove(username);
        if (game != null) {
            String opponent = game.getOpponent(username);
            activeGames.remove(opponent);
        }
    }


    public static BattleshipGame getGame(String username) {
        return activeGames.get(username);
    }
}
