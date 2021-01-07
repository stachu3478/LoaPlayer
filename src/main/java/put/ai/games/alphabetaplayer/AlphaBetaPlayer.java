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
    private BoardIndexer indexer;
    private boolean init = false;

    @Override
    public String getName() {
        return "Stanisław Graczyk 146889 Wojciech Kamiński 141242";
    }

    @Override
    public Move nextMove(Board b) {
        // if (!init) doInit(b);
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
        int ranking = 0;//groupState(getColor()) - groupState(getOpponent(getColor()));
        b.undoMove(m);
        return ranking;
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
        private BoardIndexer indexer;

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
                    float bestValue = -1;
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
                if (winner == null) return new Labeller(board, getOpponent(me)).run() - new Labeller(board, me).run();
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

        private static class Labeller {
            private HashMap<Integer, Integer> labels = new HashMap<>();
            private final Board board;
            private final Color color;
            private int labelled;
            private int totalLabelled;

            public Labeller(Board board, Color color) {
                labels = new HashMap<>();
                this.board = board;
                this.color = color;
                totalLabelled = 0;
            }

            public float run() {
                int label = 0;
                int maxLabelled = 0;
                for (int x = 0; x < board.getSize(); x++) {
                    for (int y = 0; y < board.getSize(); y++) {
                        if (board.getState(x, y) == color && !labels.containsKey(x * board.getSize() + y)) {
                            labelled = 1;
                            totalLabelled++;
                            putRecursiveLabel(label++, x, y);
                            if (labelled > maxLabelled) maxLabelled = labelled;
                        }
                    }
                }
                if (maxLabelled == 0 || totalLabelled == 0) {
                    System.out.println("Warning: No colors found on the board");
                    return 0;
                }
                return (float)maxLabelled / (float)totalLabelled;
            }

            private void putRecursiveLabel(int label, int x, int y) {
                checkAndPutLabel(label, x - 1, y);
                checkAndPutLabel(label, x + 1, y);
                checkAndPutLabel(label, x, y - 1);
                checkAndPutLabel(label, x, y + 1);
                checkAndPutLabel(label, x - 1, y - 1);
                checkAndPutLabel(label, x - 1, y + 1);
                checkAndPutLabel(label, x + 1, y - 1);
                checkAndPutLabel(label, x + 1, y + 1);
            }

            private void checkAndPutLabel(int label, int x, int y) {
                if (board.getState(x, y) == color && !labels.containsKey(x * board.getSize() + y)) {
                    labels.put(x * board.getSize() + y, label);
                    labelled++;
                    totalLabelled++;
                    putRecursiveLabel(label, x, y);
                }
            }
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
