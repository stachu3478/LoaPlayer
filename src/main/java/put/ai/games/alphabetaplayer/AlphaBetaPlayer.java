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
            maxDepth = 0;
            while (isTimeAvailable[0].get()) {
                for (int i = 0; i < polls.length; i++) {
                    Move move = moves.get(i);
                    depth = 0;
                    boolean winning = canBeWinning(move, me);
                    polls[i] += winning ? 1 : -1;
                    if (winning && depth < maxDepth) maxDepth = depth;
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

        private boolean canBeWinning(Move move, Color current) {
            Color winner = board.getWinner(current);
            if (winner != null) {
                return winner == me;
            };
            if (depth >= maxDepth) return false;
            board.doMove(move);
            depth++;
            List<Move> moves = board.getMovesFor(getOpponent(current));
            boolean foundWinning = false;
            if (!moves.isEmpty()) foundWinning = canBeWinning(moves.get(random.nextInt(moves.size())), getOpponent(current));
            board.undoMove(move);
            return foundWinning;
        }
    }
}
