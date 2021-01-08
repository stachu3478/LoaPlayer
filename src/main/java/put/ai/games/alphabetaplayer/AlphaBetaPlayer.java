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
        private boolean isEckhausted;
        private final Color me;

        public AlphaBeta(Board board, Color me, TimeWatchdog watchdog) {
            this.board = board.clone();
            this.watchdog = watchdog;
            this.me = me;
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
            private final HashMap<Integer, Integer> labels;
            private final Board board;
            private final Color color;
            private int labelled;
            private int totalLabelled;
            private final int[] fourMax;
            private int minMax = 0;
            private int minI = 0;

            public Labeller(Board board, Color color) {
                labels = new HashMap<>();
                this.board = board;
                this.color = color;
                totalLabelled = 0;
                fourMax = new int[4];
                fourMax[0] = 0;
                fourMax[1] = 0;
                fourMax[2] = 0;
                fourMax[3] = 0;
            }

            public float run() {
                int label = 0;
                for (int x = 0; x < board.getSize(); x++) {
                    for (int y = 0; y < board.getSize(); y++) {
                        if (board.getState(x, y) == color && !labels.containsKey(x * board.getSize() + y)) {
                            labelled = 1;
                            totalLabelled++;
                            putRecursiveLabel(label++, x, y);
                            verifyMax(labelled);
                        }
                    }
                }
                int maxLabelled = fourMax[0] + fourMax[1] + fourMax[2] + fourMax[3] - label;
                if (totalLabelled == 0) {
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

            private void verifyMax(int labelled) {
                if (labelled < minMax) return;
                fourMax[minI] = labelled;
                minMax = Integer.MAX_VALUE;
                for (int i = 0; i < 4; i++) {
                    if (fourMax[i] < minMax) {
                        minMax = fourMax[i];
                        minI = i;
                    }
                }
            }
        }
    }
}
