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
    private boolean usePoll = true;
    private int leastPolls = Integer.MAX_VALUE;
    private long timeLeft;

    @Override
    public String getName() {
        return "Stanisław Graczyk 146889 Wojciech Kamiński 141242";
    }

    @Override
    public Move nextMove(Board b) {
        Move move = null;
        timeLeft = getTime();
        //if (!usePoll) {
            if (alg == null) alg = new AlphaBeta(getColor(), random);
            alg.setTimeLow(createTimer(10000));
            alg.setBoard(b);
            move = alg.process();
            if (alg.getTotalWins() == 0) {
                System.out.println("Now using polling fast forward");
                usePoll = true;
            }
        //}

        //if (usePoll) {
            MovePoller poller = new MovePoller(b, createTimer(500));
            move = poller.poll();
            if (leastPolls > poller.getTotalPolls()) leastPolls = poller.getTotalPolls();
            if (leastPolls * 3 < poller.getTotalPolls()) {
                System.out.println("Now using alpha beta");
                usePoll = false;
            }
        //}

        if (move == null) {
            System.out.println("Warning: Empty move");
            List<Move> moves = b.getMovesFor(getColor());
            return moves.get(random.nextInt(moves.size()));
        }
        return move;
    }

    private AtomicBoolean[] createTimer(int freeSpace) {
        Timer timer = new Timer();
        long timeBound = timeLeft;
        timeLeft = freeSpace;
        final AtomicBoolean[] timeLow = {new AtomicBoolean(true)};
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                timeLow[0].set(false);
            }
        }, timeBound - freeSpace);
        return timeLow;
    }

    public static class TimeoutException extends RuntimeException {}

    private static class AlphaBeta {
        private AtomicBoolean[] timeLow;
        private Board board;
        private final Color me;
        private final Random random;
        private int depth = 0;
        private Integer totalWins = 0;
        private Move bestMove;
        private boolean horizon;

        public AlphaBeta(Color me, Random random) {
            this.me = me;
            this.random = random;
        }

        public void setTimeLow(AtomicBoolean[] timeLow) {
            this.timeLow = timeLow;
        }

        public void setBoard(Board board) {
            this.board = board.clone();
        }

        public Integer getTotalWins() {
            return totalWins;
        }

        public Move process() {
            bestMove = null;
            List<Move> moves = board.getMovesFor(me);
            Move currentMove = moves.get(random.nextInt(moves.size()));
            depth = Math.min(Math.max(1, depth - 3), 20);
            try {
                horizon = true;
                while (timeLow[0].get() && depth < 200 && horizon) {
                    System.out.print("Depth = " + depth);
                    horizon = false;
                    float bestResult = run(me, depth, -Float.MAX_VALUE, Float.MAX_VALUE);
                    if (bestMove == null) {
                        System.out.println("Warning: Empty best move");
                        depth--;
                        horizon = true;
                    } else currentMove = bestMove;
                    System.out.println(" Best val: " + bestResult);
                    // System.out.println(" Total wins: " + k.getVal());
                    // this.totalWins = k.getVal();
                    depth++;
                }
            } catch (TimeoutException ignored) {}
            return currentMove;
        }

        private float run(Color color, int depth, float alpha, float beta)
        {
            if (!timeLow[0].get()) {
                throw new TimeoutException();
            }
            Color winner = board.getWinner(color);
            if (winner != null) {
                return winner == color ? Float.MAX_VALUE : -Float.MAX_VALUE;
            }
            if (depth == 0 ) {
                horizon = true;
                List<Move> moves = board.getMovesFor(color);
                int winningMoves = 0;
                for (Move move : moves) {
                    board.doMove(move);
                    winner = board.getWinner(color);
                    if (winner != null) {
                        winningMoves += winner == color ? 1 : -1;
                    }
                    board.undoMove(move);
                }
                return winningMoves;
            }
            List<Move> moves = board.getMovesFor(color);
            if (this.depth == depth) Collections.shuffle(moves);
            for(Move move : moves) {
                board.doMove(move);
                float val = -run(getOpponent(color), depth - 1, -beta, -alpha);
                board.undoMove(move);
                if( val > alpha ) {
                    if (this.depth == depth) this.bestMove = move;
                    alpha = val; // alpha=max(val,alpha);
                }
                if( alpha >= beta ) {
                    return beta; // cutoff
                }
            } //endfor
            return alpha;
        }

        private static class Kunter {
            private int val = 0;

            public void change(int v) {
                val += v;
            }

            public int getVal() {
                return val;
            }
        }
    }

    private class MovePoller {
        private final int[] polls;
        private final List<Move> moves;
        private final AtomicBoolean[] isTimeAvailable;
        private final Board board;
        private int totalPolls = 0;
        private final Color me;
        private int depth;
        private int maxDepth;

        public MovePoller(Board b, AtomicBoolean[] isTimeAvailable) {
            this.me = getColor();
            moves = b.getMovesFor(me);
            this.polls = new int[moves.size()];
            this.isTimeAvailable = isTimeAvailable;
            this.board = b.clone();
        }

        public Move poll() {
            maxDepth = Integer.MAX_VALUE;
            while (isTimeAvailable[0].get()) {
                for (int i = 0; i < polls.length; i++) {
                    Move move = moves.get(i);
                    depth = 0;
                    int winningWeight = canBeWinning(move, me);
                    polls[i] += winningWeight;
                    if (winningWeight > 0 && depth < maxDepth) {
                        maxDepth = depth;
                        if (maxDepth <= 1) return move;
                    }
                    totalPolls++;
                    if (!isTimeAvailable[0].get()) break;
                }
            }
            int bestPoll = 0;
            int bestVal = Integer.MIN_VALUE;
            for (int i = 0; i < polls.length; i++) {
                if (polls[i] > bestVal) {
                    bestPoll = i;
                    bestVal = polls[i];
                }
            }
            System.out.println("Total polls: " + totalPolls);
            return moves.get(bestPoll);
        }

        public int getTotalPolls() {
            return totalPolls;
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
