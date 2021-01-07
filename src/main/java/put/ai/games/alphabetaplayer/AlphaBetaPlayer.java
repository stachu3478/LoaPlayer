/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package put.ai.games.alphabetaplayer;

import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import put.ai.games.game.Board;
import put.ai.games.game.Move;
import put.ai.games.game.Player;

public class AlphaBetaPlayer extends Player {
    @Override
    public String getName() {
        return "Stanisław Graczyk 146889 Wojciech Kamiński 141242";
    }

    @Override
    public Move nextMove(Board b) {
        Timer timer = new Timer();
        final AtomicBoolean[] timeLow = {new AtomicBoolean(true)};
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                timeLow[0].set(false);
            }
        }, getTime() - 100);
        return new AlphaBeta(b, getColor(), () -> timeLow[0].get()).process();
    }

    public interface TimeWatchdog {
        boolean call();
    }

    public static class TimeoutException extends RuntimeException {}

    private static class AlphaBeta {
        private final TimeWatchdog watchdog;
        private final Board board;
        private int depth;
        private boolean isEckhausted;
        private final Color me;
        private final BoardIndexer indexer;

        public AlphaBeta(Board board, Color me, TimeWatchdog watchdog) {
            this.board = board.clone();
            this.watchdog = watchdog;
            this.me = me;
            this.indexer = new BoardIndexer(board);
        }

        public Move process() {
            List<Move> moves = board.getMovesFor(me);
            Move bestMove1 = moves.get(0);
            int depth = 1;
            try {
                while (true) {
                    Move bestMove = moves.get(0);
                    isEckhausted = true;
                    float bestValue = Float.MIN_VALUE;
                    for (Move move : moves) {
                        board.doMove(move);
                        float result = -run(getOpponent(me), depth, -64 * board.getSize(), 64 * board.getSize());
                        if (result > bestValue) {
                            bestValue = result;
                            bestMove = move;
                        }
                        board.undoMove(move);
                    }
                    System.out.println("Best val: " + bestValue);
                    bestMove1 = bestMove;
                    if (isEckhausted) break;
                    depth++;
                }
            } catch (TimeoutException e) {
                System.out.println("Depth reached " + (depth - 1));
            }
            return bestMove1;
        }

        private int groupState(Color color) {
            indexer.fetch(board);
            int state = 0;
            for (int x = 0; x < indexer.getSize(); x++) {
                for (int y = 0; y < indexer.getSize(); y++) {
                    if (color != indexer.getColor(x, y)) continue;
                    state -= 8;
                    state += indexer.getConnectable(color, x, y, getOpponent(me));
                }
            }
            return state;
        }

        private float run(Color color, int depth, float alpha, float beta)
        {
            if (!watchdog.call()) throw new TimeoutException();
            List<Move> moves = board.getMovesFor(color);
            if( moves.size() == 0 || depth == 0 ) {
                if (moves.size() != 0) isEckhausted = false;
                Color winner = board.getWinner(color);
                if (winner == me) return -64 * board.getSize();
                if (winner == null) return groupState(getOpponent(me)) - groupState(me);
                return 64 * board.getSize();
            }
            for(Move move : moves) {
                board.doMove(move);
                float val = -run(getOpponent(color), depth - 1, -beta, -alpha);
                board.undoMove(move);
                if( val > alpha ) alpha = val; // alpha=max(val,alpha);
                if( alpha >= beta ) return beta; // cutoff
            } //endfor
            return alpha;
        }
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
