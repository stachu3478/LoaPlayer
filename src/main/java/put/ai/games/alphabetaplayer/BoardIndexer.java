package put.ai.games.alphabetaplayer;

import put.ai.games.game.Board;
import put.ai.games.game.Player.*;

public class BoardIndexer {
    private int[] mixRows;
    private int[] mixCols;
    private int[] mixLurdWedges;
    private int[] mixLdruWedges;
    private int boardSize;
    private int boardMaxField;
    private Color[][] boardSnaphot;

    public BoardIndexer(Board b) {
        boardSize = b.getSize();
        boardMaxField = boardSize - 1;
        mixRows = new int[boardSize];
        mixCols = new int[boardSize];
        mixLurdWedges = new int[boardSize * 2];
        mixLdruWedges = new int[boardSize * 2];
        boardSnaphot = new Color[boardSize][boardSize];
    }

    public int getSize() {
        return boardSize;
    }

    public Color getColor(int x, int y) {
        return boardSnaphot[x][y];
    }

    public void fetch(Board b) {
        makeBoardSnapshot(b);
        createMoveIndexes(b);
    }

    public boolean find(Color c, int x, int y) {
        int pxMax = Math.min(boardMaxField, x + mixRows[y]);
        for (int px = Math.max(0, x - mixRows[y]); px <= pxMax; px++)
            if (c == boardSnaphot[px][y]) return true;
        int pyMax = Math.min(boardMaxField, y + mixCols[x]);
        for (int py = Math.max(0, y - mixCols[x]); py <= pyMax; py++)
            if (c == boardSnaphot[x][py]) return true;
        int waMax = Math.min(boardMaxField - x, Math.min(boardMaxField - y, mixLurdWedges[boardSize + x - y]));
        for (int wa = -Math.min(x, Math.min(y, mixLurdWedges[boardSize + x - y])); wa <= waMax; wa++)
            if (c == boardSnaphot[x + wa][y + wa]) return true;
        int wbMax = Math.min(x, Math.min(boardMaxField - y, mixLdruWedges[x + y]));
        for (int wb = -Math.min(boardMaxField - x, Math.min(y, mixLdruWedges[x + y])); wb <= wbMax; wb++)
            if (c == boardSnaphot[x - wb][y + wb]) return true;
         return false;
    }

    private void makeBoardSnapshot(Board b) {
        for (int x = 0; x < boardSize; x++) {
            for (int y = 0; y < boardSize; y++) {
                boardSnaphot[x][y] = b.getState(x, y);
            }
        }
    }

    private void createMoveIndexes(Board b) {
        for (int x = 0; x < boardSize; x++) {
            // One to make finding faster because of adding sticking
            mixRows[x] = 1;
            mixCols[x] = 1;
            mixLurdWedges[x] = 1;
            mixLdruWedges[x] = 1;
        }
        for (int x = 0; x < boardSize; x++) {
            for (int y = 0; y < boardSize; y++) {
                if (boardSnaphot[x][y] != Color.EMPTY) {
                    mixRows[y]++;
                    mixCols[x]++;
                    mixLurdWedges[boardSize + x - y]++;
                    mixLdruWedges[x + y]++;
                }
            }
        }
    }
}