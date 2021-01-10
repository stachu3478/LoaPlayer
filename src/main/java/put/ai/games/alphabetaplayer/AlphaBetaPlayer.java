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

public class AlphaBetaPlayer extends Player {
    private final Random random = new Random(0xdeadbeef);

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
        return new AlphaBeta(b, getColor(), () -> timeLow[0].get(), random).process();
    }

    public interface TimeWatchdog {
        boolean call();
    }

    public static class TimeoutException extends RuntimeException {}

    private static class AlphaBeta {
        private final TimeWatchdog watchdog;
        private final Board board;
        private final Color me;
        private final Random random;
        private MovePoller poller;
        private Move bestMove;
        private int depth;

        public AlphaBeta(Board board, Color me, TimeWatchdog watchdog, Random random) {
            this.board = board.clone();
            this.watchdog = watchdog;
            this.me = me;
            this.random = random;
            this.poller = new MovePoller(me);
            this.depth = 1;
        }

        public Move process() {
            depth = Math.max(1, depth - 3);
            Move bestDepthMove = null;
            this.poller.setMaxDepth(Integer.MAX_VALUE);
            try {
                while (watchdog.call()) {
                    bestMove = null;
                    float bestVal = run(me, depth, -Float.MAX_VALUE, Float.MAX_VALUE);
                    System.out.println("Best val: " + bestVal);
                    if (bestMove != null) bestDepthMove = bestMove;
                    if (bestVal == Float.MAX_VALUE) return bestDepthMove;
                    depth++;
                }
            } catch (TimeoutException e) {
                System.out.println("Depth reached " + depth);
            }
            if (bestDepthMove == null) {
                List<Move> moves = board.getMovesFor(me);
                return moves.get(random.nextInt(moves.size()));
            }
            return bestDepthMove;
        }

        private float run(Color color, int depth, float alpha, float beta)
        {
            if (!watchdog.call()) throw new TimeoutException();
            Color winner = board.getWinner(color);
            if (winner != null) {
                return winner == color ? Float.MAX_VALUE : -Float.MAX_VALUE;
            }
            List<Move> moves = board.getMovesFor(color);
            if( depth == 0 ) {
                float poll = 0;
                for (int i = 0; i < 1 && watchdog.call(); i++) {
                    poll += poller.poll(moves, board);
                }
                return color == me ? poll : -poll;
            }
            for(Move move : moves) {
                board.doMove(move);
                float val = -run(getOpponent(color), depth - 1, -beta, -alpha);
                board.undoMove(move);
                if( val > alpha ) {
                    if (this.depth == depth) bestMove = move;
                    alpha = val; // alpha=max(val,alpha);
                }
                if( alpha >= beta ) return beta; // cutoff
            } //endfor
            return alpha;
        }

        private class MovePoller {
            private Board board;
            private final Color me;
            private int depth;
            private int maxDepth;

            public MovePoller(Color c) {
                this.me = c;
            }

            public void setMaxDepth(int maxDepth) {
                this.maxDepth = maxDepth;
            }

            public float poll(List<Move> moves, Board b) {
                this.board = b;
                maxDepth = Integer.MAX_VALUE;
                depth = 0;
                float winningWeight = canBeWinning(moves.get(random.nextInt(moves.size())), me);
                if (winningWeight > 0 && depth < maxDepth) {
                    maxDepth = depth;
                }
                return winningWeight / depth;
            }

            private int canBeWinning(Move move, Color current) {
                if (depth >= maxDepth) return 0;
                board.doMove(move);
                depth++;
                Color opponent = getOpponent(current);
                Color winner = board.getWinner(opponent);
                int foundWinning;
                if (winner != null) {
                    foundWinning = winner == me ? 1 : -100;
                } else {
                    List<Move> moves = board.getMovesFor(opponent);
                    foundWinning = canBeWinning(moves.get(random.nextInt(moves.size())), opponent);
                }
                board.undoMove(move);
                return foundWinning;
            }
        }
    }
}
