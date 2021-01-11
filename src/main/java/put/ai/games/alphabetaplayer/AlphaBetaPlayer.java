/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package put.ai.games.alphabetaplayer;

import java.util.*;
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
    public Move nextMove(Board board) {
        Board b = board.clone();
        Timer timer = new Timer();
        final AtomicBoolean[] timeAvailable = {new AtomicBoolean(true)};
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                timeAvailable[0].set(false);
            }
        }, getTime() - 1000);
        return new MovePoller(b, timeAvailable).poll();
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
            this.board = b;
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

        private int canBeWinning(Move move, Color current) {
            if (depth >= maxDepth) return 0;
            board.doMove(move);
            depth++;
            Color opponent = getOpponent(current);
            Color winner = board.getWinner(opponent);
            int foundWinning = 0;
            if (winner != null) {
                foundWinning = winner == me ? 1 : -100;
            } else {
                List<Move> moves = board.getMovesFor(opponent);
                if (!moves.isEmpty()) foundWinning = canBeWinning(moves.get(random.nextInt(moves.size())), opponent);
            }
            board.undoMove(move);
            return foundWinning;
        }
    }
}
