package cz.vse.server;

public class Player {
    private String username;
    private char[][] board = new char[10][10];

    public Player(String username) {
        this.username = username;
        initializeBoard();
    }

    private void initializeBoard() {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                board[i][j] = '~'; // Voda
            }
        }
    }

    public String getUsername() {
        return username;
    }

    public char[][] getBoard() {
        return board;
    }
}
