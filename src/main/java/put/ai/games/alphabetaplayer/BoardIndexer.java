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

    public int getConnectable(Color c, int x, int y, Color o) {
        int connectable = 0;
        Walker walker = new Walker(x, y, boardSize, boardSnaphot);
        connectable += walker.towards(1, 0, mixRows[y], c, o);
        connectable += walker.towards(-1, 0, mixRows[y], c, o);
        connectable += walker.towards(0, 1, mixCols[x], c, o);
        connectable += walker.towards(0, -1, mixCols[x], c, o);
        connectable += walker.towards(-1, -1, mixLurdWedges[boardSize + x - y], c, o);
        connectable += walker.towards(1, 1, mixLurdWedges[boardSize + x - y], c, o);
        connectable += walker.towards(1, -1, mixLdruWedges[x + y], c, o);
        connectable += walker.towards(-1, 1, mixLdruWedges[x + y], c, o);
         return connectable;
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

    private class Walker {
        private int x;
        private int y;
        private int boardSize;
        private Color[][] boardSnaphot;

        public Walker(int x, int y, int boardSize, Color[][] boardSnaphot) {
            this.x = x;
            this.y = y;
            this.boardSize = boardSize;
            this.boardSnaphot = boardSnaphot;
        }

        private int towards(int xd, int yd, int times, Color c, Color o) {
            for (int px = x, py = y, i = 0; i < times && px > 0 && py > 0 && px < boardSize && py < boardSize; px += xd, py += yd, i++) {
                if (c == boardSnaphot[px][py]) return 1;
                if (o == boardSnaphot[px][py]) return 0;
            }
            return 0;
        }
    }
}