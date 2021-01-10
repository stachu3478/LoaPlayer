/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package put.ai.games.alphabetaplayer;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import put.ai.games.game.Board;
import put.ai.games.game.Move;
import put.ai.games.game.Player;
import sun.rmi.server.InactiveGroupException;

public class AlphaBetaPlayer extends Player {
    private final Random random = new Random(0xdeadbeef);
    private AlphaBeta alg;

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
        if (alg == null) alg = new AlphaBeta(getColor(), random);
        alg.setWatchdog(() -> timeLow[0].get());
        alg.setBoard(b);
        return alg.process();
    }

    public interface TimeWatchdog {
        boolean call();
    }

    public static class TimeoutException extends RuntimeException {}

    private static class AlphaBeta {
        private TimeWatchdog watchdog;
        private Board board;
        private final Color me;
        private final Random random;
        private int depth = 0;
        private static final float horizonImpact = 0.0625F; // Generally horizon clash not preferred
        private final Map<Integer, Float> cuttingBoardsMe = new HashMap<>(65536);
        private final Map<Integer, Float> cuttingBoardsOp = new HashMap<>(65536);
        private Integer totalWins = 0;
        private Move bestMove;

        public AlphaBeta(Color me, Random random) {
            this.me = me;
            this.random = random;
        }

        public void setWatchdog(TimeWatchdog watchdog) {
            this.watchdog = watchdog;
        }

        public void setBoard(Board board) {
            this.board = board.clone();
        }

        public Move process() {
            List<Move> moves = board.getMovesFor(me);
            Move currentMove = moves.get(random.nextInt(moves.size()));
            depth = Math.max(1, depth - 3);
            try {
                while (watchdog.call()) {
                    System.out.print("Depth = " + depth);
                    cuttingBoardsMe.clear();
                    cuttingBoardsOp.clear();
                    Kunter k = new Kunter();
                    float bestResult = run(me, depth, -Float.MIN_VALUE, Float.MAX_VALUE, k);
                    if (bestMove == null) {
                        System.out.println("Warning: Empty best move");
                    } else currentMove = bestMove;
                    System.out.print(" Best val: " + bestResult);
                    System.out.print(" Total wins: " + k.getVal());
                    System.out.println(" Allocations: " + (cuttingBoardsMe.size() + cuttingBoardsOp.size()));
                    totalWins = 0;
                    depth++;
                }
            } catch (TimeoutException ignored) {}
            return currentMove;
        }

        private float run(Color color, int depth, float alpha, float beta, Kunter totalWins)
        {
            int hash = board.hashCode();
            Map<Integer, Float> cuttingBoard = getCuttingBoard(color);
            if (cuttingBoard.containsKey(hash)) {
                return cuttingBoard.get(hash);
            }
            if (!watchdog.call()) throw new TimeoutException();
            Color winner = board.getWinner(color);
            if (winner != null) {
                int win = winner == color ? 1 : -1;
                totalWins.change(winner == me ? 1 : -1);
                return win * Float.MAX_VALUE;
            }
            if (depth == 0 ) {
                return totalWins.getVal();
            }
            List<Move> moves = board.getMovesFor(color);
            if (this.depth == depth) Collections.shuffle(moves);
            for(Move move : moves) {
                board.doMove(move);
                Kunter wins = new Kunter();
                float val = -run(getOpponent(color), depth - 1, -beta, -alpha, wins);
                totalWins.change(wins.getVal());
                board.undoMove(move);
                if( val > alpha ) {
                    if (this.depth == depth) this.bestMove = move;
                    alpha = val; // alpha=max(val,alpha);
                }
                if( alpha >= beta ) {
                    getCuttingBoard(color).put(hash, beta);
                    return beta; // cutoff
                }
            } //endfor
            getCuttingBoard(color).put(hash, alpha);
            return alpha;
        }

        private Map<Integer, Float> getCuttingBoard(Color color) {
            if (color == me) return cuttingBoardsMe;
            return cuttingBoardsOp;
        }

        private class Kunter {
            private int val = 0;

            public void change(int v) {
                val += v;
            }

            public int getVal() {
                return val;
            }
        }
    }
}
