package qlearning;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;
import wumpusworld.*;

public class QLearningAgent {
    private static final String Q_FILE_PATH = "Q.dat";
    
    private static final int ACTION_MOVE = 0;
    private static final int ACTION_SHOOT = 1;
    private static final int ACTION_TURN_LEFT = 2;
    private static final int ACTION_TURN_RIGHT = 3;
    private static final int ACTION_COUNT = 4;
    
    private static final byte PERCEPT_BREEZY = 1;
    private static final byte PERCEPT_STENCH = 2;
    
    private static final byte TYPE_NORMAL = 0;
    private static final byte TYPE_UNKNOWN = 1;
    private static final byte TYPE_WALL = 2;
    
    private static final byte HAZARD_WUMPUS = 1;
    private static final byte HAZARD_PIT = 2;
    
    private static final double REWARD_EATEN = -1000;
    private static final double REWARD_GOLD = 1000;
    private static final double REWARD_PIT = -500;
    private static final double REWARD_WUMPUS_KILLED = 100;
    private static final double REWARD_ARROW_MISSED = -100;
    private static final double REWARD_EXPLORED_TILE = 10;
    
    private static final int[][] NEIGHBOUR_COORDINATES = { {1, 0}, {0, 1}, {-1, 0}, {0, -1} };
    private static final int[][] N2N_COORDINATES = { {2, 0}, {1, 1}, {0, 2}, {-1, 1}, {-2, 0}, {-1, -1}, {0, -2}, {1, -1} };
    
    private static final double ALPHA = 0.1;
    private static final double GAMMA = 0.5;
    
    private class State {
        public byte percepts;
        public byte hazards;
        public byte[] neighbour_type = new byte[4];
        public byte[] neighbour_hazards = new byte[4];
        public byte[] n2n_type = new byte[8];
        public byte[] n2n_percepts = new byte[8];
        public boolean wumpus_alive;
        public boolean has_arrow;
        
        public State() {
            
        }
        
        public State(ObjectInputStream fis) throws IOException {
            percepts = fis.readByte();
            hazards = fis.readByte();
            fis.readFully(neighbour_type, 0, neighbour_type.length);
            fis.readFully(neighbour_hazards, 0, neighbour_hazards.length);
            fis.readFully(n2n_type, 0, n2n_type.length);
            fis.readFully(n2n_percepts, 0, n2n_percepts.length);
            wumpus_alive = fis.readBoolean();
            has_arrow = fis.readBoolean();
        }
        
        public void write(ObjectOutputStream fos) throws IOException {
            fos.writeByte(percepts);
            fos.writeByte(hazards);
            fos.write(neighbour_type);
            fos.write(neighbour_hazards);
            fos.write(n2n_type);
            fos.write(n2n_percepts);
            fos.writeBoolean(wumpus_alive);
            fos.writeBoolean(has_arrow);
        }
    }
    
    private World w;
    private Random random;
    private HashMap<State, double[]> Q;
    
    public QLearningAgent(World world) {
        w = world;
        random = new Random();
        readQMatrix();
    }
    
    public void doAction() {
        int x1 = w.getPlayerX();
        int y1 = w.getPlayerY();
        
        // Immediately grab gold on the first turn.
        if (w.hasGlitter(x1, y1)) {
            w.doAction(World.A_GRAB);
            return;
        }
        
        // Immediately climb out of the pit.
        if (w.hasPit(x1, y1)) {
            w.doAction(World.A_CLIMB);
        }
        
        // Find the best action to do in our current state.
        World previous_world = w.clone();
        State s1 = createState(x1, y1);

        double[] q_values_1;
        if (Q.containsKey(s1)) {
            q_values_1 = Q.get(s1);
        } else {
            q_values_1 = new double[ACTION_COUNT];
            Q.put(s1, q_values_1);
        }

        int a1 = getBestAction(q_values_1);

        // Do the selected action.
        w.doAction(getActionString(a1));
        
        int x2 = w.getPlayerX();
        int y2 = w.getPlayerY();
        
        // Grab the gold if we've encountered it, climb out of pits if we're in them and there is no gold there.
        if (w.hasGlitter(x2, y2)) {
            w.doAction(World.A_GRAB);
        } else if (w.hasPit(x2, y2)) {
            w.doAction(World.A_CLIMB);
        }

        // Given the new state after making the action, find out if we are rewarded in the new state.
        State s2 = createState(x2, y2);
        double r = getReward(previous_world);
        
        double[] q_values_2;
        if (Q.containsKey(s2)) {
            q_values_2 = Q.get(s2);
        } else {
            q_values_2 = new double[ACTION_COUNT];
            Q.put(s2, q_values_2);
        }
        
        // Calculate the new Q-value for the taken action.
        double max = Double.NEGATIVE_INFINITY;
        for (int a2 = 0; a2 < q_values_2.length; ++a2)
            max = Math.max(max, q_values_2[a2]);
        q_values_1[a1] = q_values_1[a1] + ALPHA * (r + GAMMA * max - q_values_1[a1]);
        
        System.out.println("New Q-Value = " + q_values_1[a1]);
    }
    
    private State createState(int x, int y) {
        State s = new State();

        if (w.hasBreeze(x, y))
            s.percepts |= PERCEPT_BREEZY;
        if (w.hasStench(x, y))
            s.percepts |= PERCEPT_STENCH;
        s.has_arrow = w.hasArrow();
        s.wumpus_alive = w.wumpusAlive();
        
        if (w.hasPit(x, y))
            s.hazards |= HAZARD_PIT;
        if (w.hasWumpus(x, y))
            s.hazards |= HAZARD_WUMPUS;
        
        // Check type and hazards of neighbours.
        for (int i = 0; i < 4; ++i) {
            int nx = x + NEIGHBOUR_COORDINATES[i][0];
            int ny = y + NEIGHBOUR_COORDINATES[i][1];
            
            if (w.isValidPosition(nx, ny)) {
                if (!w.isUnknown(nx, ny)) {
                    s.neighbour_type[i] = TYPE_NORMAL;
                    
                    if (w.hasPit(nx, ny))
                        s.neighbour_hazards[i] |= HAZARD_PIT;
                    if (w.hasWumpus(nx, ny))
                        s.neighbour_hazards[i] |= HAZARD_WUMPUS;
                } else {
                    s.neighbour_type[i] = TYPE_UNKNOWN;
                    s.neighbour_hazards[i] = 0;
                }
            } else {
                s.neighbour_type[i] = TYPE_WALL;
                s.neighbour_hazards[i] = 0;
            }
        }
        
        // Check percepts in our neighbours' neighbours.
        for (int i = 0; i < 8; ++i) {
            int nx = x + N2N_COORDINATES[i][0];
            int ny = y + N2N_COORDINATES[i][1];
            
            if (w.isValidPosition(nx, ny)) {
                if (!w.isUnknown(nx, ny)) {
                    s.n2n_type[i] = TYPE_NORMAL;
                    
                    if (w.hasBreeze(nx, ny))
                        s.n2n_percepts[i] |= PERCEPT_BREEZY;
                    if (w.hasStench(nx, ny))
                        s.n2n_percepts[i] |= PERCEPT_STENCH;
                } else {
                    s.n2n_type[i] = TYPE_UNKNOWN;
                    s.n2n_percepts[i] = 0;
                }
            } else {
                s.n2n_type[i] = TYPE_WALL;
                s.n2n_percepts[i] = 0;
            }
        }
        
        return s;
    }
    
    private int getBestAction(double[] qValues) {
        ArrayList<Integer> best = new ArrayList<>();
        
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < qValues.length; ++i) {
            max = Math.max(max, qValues[i]);
        }
        
        for (int i = 0; i < qValues.length; ++i) {
            if (qValues[i] == max) {
                best.add(i);
            }
        }
        
        // TODO: Sometimes randomize for non-optimal actions.
        
        return best.get(random.nextInt(best.size()));
    }
    
    private String getActionString(int action) {
        switch (action) {
            case ACTION_MOVE: return World.A_MOVE;
            case ACTION_SHOOT: return World.A_SHOOT;
            case ACTION_TURN_LEFT: return World.A_TURN_LEFT;
            case ACTION_TURN_RIGHT: return World.A_TURN_RIGHT;
            default: return "";
        }
    }
    
    private double getReward(World previous) {
        if (w.hasWumpus(w.getPlayerX(), w.getPlayerY()))
            return REWARD_EATEN;
        if (w.hasGold())
            return REWARD_GOLD;
        if (w.hasPit(w.getPlayerX(), w.getPlayerY()))
            return REWARD_PIT;
        if (!w.hasArrow() && previous.hasArrow()) {
            if (!w.wumpusAlive())
                return REWARD_WUMPUS_KILLED;
            else
                return REWARD_ARROW_MISSED;
        }
        
        if (previous.isUnknown(w.getPlayerX(), w.getPlayerY()))
            return REWARD_EXPLORED_TILE;
        
        return 0.0;
    }

    private void readQMatrix() {
        Q = new HashMap<>();
        
        try {
            ObjectInputStream fis = new ObjectInputStream(new FileInputStream(new File(Q_FILE_PATH)));

            while (fis.available() > 0)
            {
                State s = new State(fis);
                
                double[] q_values = new double[ACTION_COUNT];
                for (int i = 0; i < ACTION_COUNT; ++i) {
                    q_values[i] = fis.readDouble();
                }
                
                Q.put(s, q_values);
            }
        } catch (FileNotFoundException ex) {
            // Just let the Q-matrix be empty.
        } catch (IOException ex) {
            // If we somehow failed to read the file, just clear the Q-matrix and start from scratch.
            Q.clear();
        }
    }
    
    private void writeQMatrix() {
        try {
            ObjectOutputStream fos = new ObjectOutputStream(new FileOutputStream(new File(Q_FILE_PATH)));
            for (Entry<State, double[]> entry : Q.entrySet()) {
                entry.getKey().write(fos);
                for (int i = 0; i < ACTION_COUNT; ++i) {
                    fos.writeDouble(entry.getValue()[i]);
                }
            }
        } catch (IOException ex) {
            System.err.println("Failed to write Q-Matrix to " + Q_FILE_PATH);
        }
    }
}
