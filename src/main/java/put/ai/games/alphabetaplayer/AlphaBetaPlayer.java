/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package put.ai.games.alphabetaplayer;

import java.util.*;
import java.util.List;
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
        final AtomicBoolean[] timeAvail = {new AtomicBoolean(true)};
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                timeAvail[0].set(false);
            }
        }, getTime() - 100);
        A a = new A(b, (_b, _c) -> 0 /* TODO: add heuristic */);
        while (timeAvail[0].get()) {
            try {
                a.step();
            } catch (A.ExhaustedException e) {
                break;
            }
        }
        return a.findBestMove();
    }

    private class A {
        private final int distanceBetweenNodes;
        private final TreeMap<Integer, List<Node>> orderedNodes = new TreeMap<>();
        private final HashMap<Board, Move> leaders = new HashMap<>();
        private final Heuristic heuristic;

        public A(Board b, Heuristic heuristic) {
            this.heuristic = heuristic;
            this.distanceBetweenNodes = b.getSize() / 2;
            addNode(b);
        }

        public void step() throws ExhaustedException {
            Node toBeExpanded = null;
            for (List<Node> bestNodes : orderedNodes.values()) { // find leaf that is expandable
                for (Node node : bestNodes) {
                    if (!node.leaf && !node.expanded) {
                        toBeExpanded = node;
                        break;
                    }
                }
            }
            if (toBeExpanded == null) throw new ExhaustedException(); // the tree is fully expanded
            else expandNode(toBeExpanded);
        }

        public Move findBestMove() {
            for (List<Node> bestNodes : orderedNodes.values()) {
                for (Node node : bestNodes) {
                    if (node.lastMoverColor == getColor() && leaders.containsKey(node.board)) return leaders.get(node.board);
                }
            }
            return null;
        }

        private void addNode(Board b) {
            addNode(b.clone(), getOpponent(getColor()), 0);
        }

        private void addNode(Board b, Color moverColor, int depth) {
            Color winner = b.getWinner(null);
            int measurement;
            boolean leaf = true;
            int distanceToNode = depth * distanceBetweenNodes;
            if (winner == moverColor) measurement = Integer.MAX_VALUE - distanceToNode;
            else if (winner == getOpponent(moverColor)) measurement = Integer.MIN_VALUE + distanceToNode;
            else {
                leaf = false;
                measurement = heuristic.measure(b, moverColor) + distanceToNode;
            }
            measurement = getColor() == moverColor ? measurement : -measurement;
            insertNode(new Node(b.clone(), moverColor, depth, leaf), measurement);
        }

        private void insertNode(Node node, int key) {
            List<Node> nodes = orderedNodes.computeIfAbsent(key, k -> new ArrayList<>());
            nodes.add(node);
        }

        private void expandNode(Node node) {
            List<Move> moves = node.board.getMovesFor(node.lastMoverColor);
            Color moverColor = getOpponent(node.lastMoverColor);
            int nextDepth = node.depth + 1;
            Move leaderMove = leaders.get(node.board);
            for (Move move : moves) {
                node.board.doMove(move);
                if (!leaders.containsKey(node.board)) {
                    addNode(node.board, moverColor, nextDepth);
                    if (leaderMove != null) leaders.put(node.board, leaderMove);
                    else leaders.put(node.board, move);
                }
                node.board.undoMove(move);
            }
            node.expanded = true; // TODO: Remove expanded node
        }

        private class Node {
            public Node(Board board, Color lastMoverColor, int depth, boolean leaf) {
                this.board = board;
                this.lastMoverColor = lastMoverColor;
                this.depth = depth;
                this.leaf = leaf;
            }
            public Board board;
            public int depth;
            public Color lastMoverColor;
            public boolean leaf;
            public boolean expanded = false;
        }

        public class ExhaustedException extends Throwable {}
    }

    public interface Heuristic {
        int measure(Board b, Color myColor);
    }
}
