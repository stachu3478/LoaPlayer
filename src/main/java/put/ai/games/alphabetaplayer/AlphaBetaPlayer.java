/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package put.ai.games.alphabetaplayer;

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import put.ai.games.game.Board;
import put.ai.games.game.Move;
import put.ai.games.game.Player;

public class AlphaBetaPlayer extends Player {
    private BoardIndexer indexer;
    private boolean init = false;

    @Override
    public String getName() {
        return "Stanisław Graczyk 146889 Wojciech Kamiński 141242";
    }

    @Override
    public Move nextMove(Board b) {
        if (!init) doInit(b);
        return alpha(b.clone());
    }

    private void doInit(Board b) {
        init = true;
        indexer = new BoardIndexer(b);
    }

    private Move alpha(Board b) {
        List<Move> myMoves = b.getMovesFor(getColor());
        Move bestMove = myMoves.get(0);
        int bestRank = Integer.MIN_VALUE;
        boolean replaced = false;
        for (Move m : myMoves) {
            int r = rank(b, m);
            if (r > bestRank) {
                bestRank = r;
                bestMove = m;
                replaced = true;
            }
        }
        if (bestRank == 0) System.out.println("Warning: Zero valued rank");
        if (!replaced) System.out.println("Warning: Zero indexed move");
        System.out.println("test");
        return bestMove;
    }

    private int rank(Board b, Move m) {
        b.doMove(m);
        indexer.fetch(b);
        // ranking code goes here
        int ranking = groupState(getColor()) - groupState(getOpponent(getColor()));
        b.undoMove(m);
        return ranking;
    }

    private int groupState(Color color) {
        int state = 0;
        for (int x = 0; x < indexer.getSize(); x++) {
            for (int y = 0; y < indexer.getSize(); y++) {
                if (color != indexer.getColor(x, y)) continue;
                state -= 8;
                state += indexer.getConnectable(color, x, y, getOpponent(getColor()));
            }
        }
        return state;
    }

    private static class BoardIndexer {
        private final int[] mixRows;
        private final int[] mixCols;
        private final int[] mixLurdWedges;
        private final int[] mixLdruWedges;
        private final int boardSize;
        private final Color[][] boardSnaphot;

        public BoardIndexer(Board b) {
            boardSize = b.getSize();
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

        private static class Walker {
            private final int x;
            private final int y;
            private final int boardSize;
            private final Color[][] boardSnaphot;

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
}
