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
        A a = new A(b, new Heuristics());
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
                        bestNodes.remove(toBeExpanded);
                        // System.out.print("." + node.val);
                        break;
                    }
                }
                if (toBeExpanded != null) break;
            }
            if (toBeExpanded == null) throw new ExhaustedException(); // the tree is fully expanded
            else expandNode(toBeExpanded);
        }

        public Move findBestMove() {
            for (List<Node> bestNodes : orderedNodes.values()) {
                //System.out.println("Val: " + bestNodes.get(0).val);
                for (Node node : bestNodes) {
                    if (node.lastMoverColor == getColor()) {
                        Move move = leaders.get(node.board);
                        if (move == null) continue;
                        System.out.println("Selected node: " + node);
                        return move;
                    }
                }
            }
            return null;
        }

        private void addNode(Board b) {
            addNode(b.clone(), getOpponent(getColor()), 0);
        }

        private void addNode(Board b, Color moverColor, int depth) {
            Color winner = b.getWinner(null);
            int measurement; // funkcja kosztu
            boolean leaf = winner != null;
            int distanceToNode = depth * distanceBetweenNodes;
            if (winner == getOpponent(moverColor)) measurement = Integer.MAX_VALUE - distanceToNode;
            else if (winner == moverColor) measurement = Integer.MIN_VALUE + distanceToNode;
            else measurement = heuristic.measure(b, moverColor) + distanceToNode;
            insertNode(new Node(b, moverColor, depth, leaf, measurement), measurement);
        }

        private void insertNode(Node node, int key) {
            List<Node> nodes = orderedNodes.computeIfAbsent(key, k -> new ArrayList<>());
            nodes.add(node);
            // System.out.print("->" + key);
        }

        private void expandNode(Node node) {
            Color moverColor = getOpponent(node.lastMoverColor);
            List<Move> moves = node.board.getMovesFor(moverColor);
            int nextDepth = node.depth + 1;
            Move leaderMove = leaders.get(node.board);
            if (leaderMove == null && node.depth > 1) throw new InvalidNodeException();
            boolean unreachable = false;
            for (Move move : moves) {
                node.board.doMove(move);
                if (node.board.getWinner(null) == moverColor) unreachable = true;
                node.board.undoMove(move);
                if (unreachable) {
                    node.val = Integer.MIN_VALUE + node.depth * distanceBetweenNodes;
                    node.expanded = true;
                    node.leaf = true;
                    node.lastMoverColor = moverColor;
                    insertNode(node, node.val);
                    if (leaderMove == null) leaders.put(node.board, move);
                    return;
                }
            }
            for (Move move : moves) {
                node.board.doMove(move);
                if (!leaders.containsKey(node.board)) {
                    Board boardClone = node.board.clone();
                    addNode(boardClone, moverColor, nextDepth);
                    if (nextDepth > 1) leaders.put(boardClone, leaderMove);
                    else {
                        leaders.put(boardClone, move);
                        System.out.println("Add new move " + move);
                    }
                }
                node.board.undoMove(move);
            }
            node.expanded = true;
        }

        private class Node {
            public Node(Board board, Color lastMoverColor, int depth, boolean leaf, int val) {
                this.board = board;
                this.lastMoverColor = lastMoverColor;
                this.depth = depth;
                this.leaf = leaf;
                this.val = val;
            }
            public Board board;
            public int depth;
            public Color lastMoverColor;
            public boolean leaf;
            public boolean expanded = false;
            public int val;

            @Override
            public String toString() {
                return "Node{" +
                        "board=\n" + board +
                        ", depth=" + depth +
                        ", lastMoverColor=" + lastMoverColor +
                        ", leaf=" + leaf +
                        ", expanded=" + expanded +
                        ", val=" + val +
                        '}';
            }
        }

        public class ExhaustedException extends Throwable {}
        public class InvalidNodeException extends RuntimeException {}
    }

    public interface Heuristic {
        int measure(Board b, Color myColor);
    }

    public class Heuristics implements Heuristic{
        float distMyPlayer, distEnemyPlayer;

        /**
         * podajesz tablice z pionkami i kolor gracza, a funkcja okresla heurystyczna ocene danego stanu dla danego koloru gracza
         */
        public int measure(Board boardIn, Color myPlayerColor){//mozna przyspieszyc poprzez dodanie tablicy myPieces
            distMyPlayer=0;
            distEnemyPlayer=0;
            int boardSize = boardIn.getSize();
            int center=Math.floorDiv(boardSize,2);
            for(int i = 0; i< boardSize; i++){
                for(int j = 0; j< boardSize; j++){
                    Color color = boardIn.getState(i,j);
                    if (color == Color.EMPTY) continue;
                    double sqrt = Math.hypot(i - center, j - center);
                    if(color==myPlayerColor)distMyPlayer+= sqrt;
                    else distEnemyPlayer += sqrt;
                }
            }

            return (int) Math.floor(distMyPlayer - distEnemyPlayer);
        }
    }
}
