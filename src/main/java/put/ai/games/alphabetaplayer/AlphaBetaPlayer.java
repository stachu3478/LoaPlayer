/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package put.ai.games.alphabetaplayer;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import put.ai.games.Point;
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
        int estimatedMovesToWin = new Labeller(b, getColor()).run();
            System.out.println("Estimated moves to win: " + estimatedMovesToWin);

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

    private static class Labeller {
        private final Board board;
        private final Color color;
        private int[][] labelMap;
        private final List<Label> labels;

        public Labeller(Board board, Color color) {
            labels = new ArrayList<>();
            this.board = board;
            this.color = color;
        }

        public int run() {
            labels.clear();
            labelMap = new int[board.getSize()][board.getSize()];
            for (int x = 0; x < board.getSize(); x++) {
                for (int y = 0; y < board.getSize(); y++) {
                    checkAndPutLabel(null, x, y);
                }
            }

            List<Label> finalLabels = new ArrayList<>();
            for (Label label1 : labels) {
                int minDistance = Integer.MAX_VALUE;
                for (Label label2 : labels) {
                    if (label1.equals(label2)) continue;
                    minDistance = Math.min(minDistance, label1.getDistance(label2));
                    if (minDistance <= 2) break;
                }
                if (minDistance <= Math.max(label1.getSize(), 2)) finalLabels.add(label1);
            }
            int sum = 0;
            int maxSize = 1;
            for (Label label : labels) {
                sum += label.getSize();
                maxSize = Math.max(maxSize, label.getSize());
            }
            if (finalLabels.size() <= 1) {
                System.out.println("Outer calc");
                return sum - maxSize;
            }
            System.out.println("Span calc");
            return Math.min(span(finalLabels), sum - maxSize);
        }

        private int span(List<Label> labels) {
            int[][] labelGraph = new int[labels.size()][labels.size()];
            boolean[] connected = new boolean[labels.size()];
            int toBeConnected = labels.size() - 1;
            int spanSize = 0;
            for (int l1 = 0; l1 < labels.size(); l1++) {
                for (int l2 = 0; l2 < labels.size(); l2++) {
                    if (labels.get(l1).equals(labels.get(l2))) labelGraph[l1][l2] = Integer.MAX_VALUE;
                    else labelGraph[l1][l2] = labels.get(l1).getDistance(labels.get(l2)) - 1;
                }
            }
            while (toBeConnected > 0) {
                for (int x = 0; x < labels.size(); x++) {
                    if (connected[x]) continue;
                    int minDist = Integer.MAX_VALUE;
                    int minY = 0;
                    for (int y = 0; y < labels.size(); y++) {
                        if (connected[y]) continue;
                        if (labelGraph[x][y] < minDist) {
                            minDist = labelGraph[x][y];
                            minY = y;
                            if (minDist <= 1) {
                                minDist = 0;
                                break;
                            };
                        }
                    }
                    connected[minY] = true;
                    spanSize += minDist;
                    toBeConnected--;
                    for (int y = 0; y < labels.size(); y++) {
                        labelGraph[x][y] = Math.min(labelGraph[x][y], labelGraph[minY][y]);
                    }
                }
            }
            return spanSize;
        }

        private void putRecursiveLabel(Label label, int x, int y) {
            checkAndPutLabel(label, x - 1, y);
            checkAndPutLabel(label, x + 1, y);
            checkAndPutLabel(label, x, y - 1);
            checkAndPutLabel(label, x, y + 1);
            checkAndPutLabel(label, x - 1, y - 1);
            checkAndPutLabel(label, x - 1, y + 1);
            checkAndPutLabel(label, x + 1, y - 1);
            checkAndPutLabel(label, x + 1, y + 1);
        }

        private void checkAndPutLabel(Label label, int x, int y) {
            if (board.getState(x, y) == color && labelMap[x][y] == 0) {
                if (label == null) {
                    label = new Label(labels.size());
                    labels.add(label);
                }
                labelMap[x][y] = 1;
                label.addPoint(new put.ai.games.Point(x, y));
                putRecursiveLabel(label, x, y);
            }
        }

        private static class Label {
            private final int id;
            private final List<put.ai.games.Point> points = new ArrayList<>();

            public Label(int id) {
                this.id = id;
            }

            public void addPoint(put.ai.games.Point p) {
                points.add(p);
            }

            public int getId() {
                return id;
            }

            public int getSize() {
                return points.size();
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                Label label = (Label) o;
                return id == label.id;
            }

            @Override
            public int hashCode() {
                return Objects.hash(id);
            }

            public int getDistance(Label l) {
                int near = Integer.MAX_VALUE;
                for (put.ai.games.Point myPoint : points) {
                    for (Point point : l.points) {
                        int distance = Math.max(Math.abs(myPoint.getX() - point.getX()), Math.abs(myPoint.getY() - point.getY()));
                        if (distance < near) {
                            if (distance <= 2) return distance;
                            near = distance;
                        }
                    }
                }
                return near;
            }
        }
    }
}
