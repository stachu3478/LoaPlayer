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
        private int depth = 0;
        private final Set<Long> cuttingBoards = new HashSet<>();

        public AlphaBeta(Board board, Color me, TimeWatchdog watchdog, Random random) {
            this.board = board.clone();
            this.watchdog = watchdog;
            this.me = me;
            this.random = random;
        }

        public Move process() {
            List<Move> moves = board.getMovesFor(me);
            depth = Math.max(0, depth - 2);
            int movesRemoved = 0;
            int movesTotal = moves.size();
            try {
                while (true) {
                    cuttingBoards.clear();
                    List<Move> toBeRemoved = new ArrayList<>();
                    for (Move move : moves) {
                        board.doMove(move);
                        float result = -run(getOpponent(me), depth, -1, 1);
                        if (result == 1) return move; // winning
                        else if (result == -1) {
                            movesRemoved++; // losing
                            toBeRemoved.add(move);
                        }
                        board.undoMove(move);
                    }
                    for (Move move : toBeRemoved) {
                        moves.remove(move);
                        if (moves.size() == 1) {
                            System.out.println("No moves remaining");
                            return moves.get(0); // no more moves to choose
                        }
                    }
                    depth++;
                }
            } catch (TimeoutException e) {
                System.out.println("Depth reached " + depth);
                System.out.println("Moves available " + (movesTotal - movesRemoved) + " / " + movesTotal);
            }
            return moves.get(random.nextInt(moves.size()));
        }

        private float run(Color color, int depth, float alpha, float beta)
        {
            if (!watchdog.call()) throw new TimeoutException();
            if( depth == 0 ) {
                Color winner = board.getWinner(color);
                if (winner != null) {
                    return winner == color ? 1 : -1;
                }
                return 0;
            }
            List<Move> moves = board.getMovesFor(color);
            long hash = board.hashCode() + (long) color.hashCode() * Integer.MAX_VALUE;
            if (cuttingBoards.contains(hash)) return beta;
            for(Move move : moves) {
                board.doMove(move);
                float val = -run(getOpponent(color), depth - 1, -beta, -alpha);
                board.undoMove(move);
                if( val > alpha ) alpha = val; // alpha=max(val,alpha);
                if( alpha >= beta ) {
                    cuttingBoards.add(hash);
                    return beta; // cutoff
                }
            } //endfor
            return alpha;
        }
    }
}
