/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package put.ai.games.alphabetaplayer;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import put.ai.games.Point;
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
            Collections.shuffle(moves);
            Move bestMove1 = moves.get(0);
            int depth = 0;
            try {
                while (true) {
                    Move bestMove = moves.get(0);
                    isEckhausted = true;
                    float bestValue = Float.MIN_VALUE;
                    for (Move move : moves) {
                        board.doMove(move);
                        float result = -run(getOpponent(me), depth, Float.MIN_VALUE, Float.MAX_VALUE);
                        if (result > bestValue) {
                            bestValue = result;
                            bestMove = move;
                        }
                        if (result >= Float.MAX_VALUE / 2) return move;
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
                if (winner == me) return Float.MIN_VALUE;
                if (winner == null) return new Labeller(board, getOpponent(me)).run() - new Labeller(board, me).run();
                return Float.MAX_VALUE;
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
                    if (minDistance <= label1.getSize()) finalLabels.add(label1);
                }
                if (finalLabels.size() <= 1) {
                    System.out.println("Warning: No colors found on the board");
                    int sum = 0;
                    int maxSize = 1;
                    for (Label label : labels) {
                        sum += label.getSize();
                        maxSize = Math.max(maxSize, label.getSize());
                    }
                    return sum - maxSize;
                }
                return span(finalLabels);
            }

            private int span(List<Label> labels) {
                int[][] labelGraph = new int[labels.size()][labels.size()];
                boolean[] connected = new boolean[labels.size()];
                int toBeConnected = labels.size() - 1;
                int spanSize = 0;
                for (Label label1 : labels) {
                    for (Label label2 : labels) {
                        if (label1.equals(label2)) labelGraph[label1.getId()][label2.getId()] = Integer.MAX_VALUE;
                        else labelGraph[label1.getId()][label2.getId()] = label1.getDistance(label2);
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
                                if (minDist <= 2) break;
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
                    label.addPoint(new Point(x, y));
                    putRecursiveLabel(label, x, y);
                }
            }

            private static class Label {
                private final int id;
                private final List<Point> points = new ArrayList<>();

                public Label(int id) {
                    this.id = id;
                }

                public void addPoint(Point p) {
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
                    for (Point myPoint : points) {
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
}
