package qlearning;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
    
    private static final double REWARD_EATEN = -1.0;
    private static final double REWARD_GOLD = 1.0;
    private static final double REWARD_PIT = -0.5;
    private static final double REWARD_WUMPUS_KILLED = 0.1;
    private static final double REWARD_ARROW_MISSED = -0.1;
    private static final double REWARD_EXPLORED_TILE = 0.2;
    private static final double REWARD_BUMPING_INTO_WALL = -0.1;
    private static final double REWARD_FIRING_WITHOUT_AMMO = -0.1;
    private static final double REWARD_TURNING = -0.01;
    
    private static final double OPTIMAL_CHANCE = 0.99;
    
    private static final int[][] NEIGHBOUR_COORDINATES = { {1, 0}, {0, 1}, {-1, 0}, {0, -1} };
    private static final int[][] N2N_COORDINATES = { {2, 0}, {1, 1}, {0, 2}, {-1, 1}, {-2, 0}, {-1, -1}, {0, -2}, {1, -1} };
    
    private static final double ALPHA = 0.1;
    private static final double GAMMA = 0.5;
    
    public static class State {
        public byte direction;
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
            direction = fis.readByte();
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
            fos.writeByte(direction);
            fos.writeByte(percepts);
            fos.writeByte(hazards);
            fos.write(neighbour_type);
            fos.write(neighbour_hazards);
            fos.write(n2n_type);
            fos.write(n2n_percepts);
            fos.writeBoolean(wumpus_alive);
            fos.writeBoolean(has_arrow);
        }
        
        @Override
        public String toString() {
            return Integer.toString(hashCode());
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 59 * hash + this.direction;
            hash = 59 * hash + this.percepts;
            hash = 59 * hash + this.hazards;
            hash = 59 * hash + Arrays.hashCode(this.neighbour_type);
            hash = 59 * hash + Arrays.hashCode(this.neighbour_hazards);
            hash = 59 * hash + Arrays.hashCode(this.n2n_type);
            hash = 59 * hash + Arrays.hashCode(this.n2n_percepts);
            hash = 59 * hash + (this.wumpus_alive ? 1 : 0);
            hash = 59 * hash + (this.has_arrow ? 1 : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final State other = (State) obj;
            if (this.direction != other.direction) {
                return false;
            }
            if (this.percepts != other.percepts) {
                return false;
            }
            if (this.hazards != other.hazards) {
                return false;
            }
            if (!Arrays.equals(this.neighbour_type, other.neighbour_type)) {
                return false;
            }
            if (!Arrays.equals(this.neighbour_hazards, other.neighbour_hazards)) {
                return false;
            }
            if (!Arrays.equals(this.n2n_type, other.n2n_type)) {
                return false;
            }
            if (!Arrays.equals(this.n2n_percepts, other.n2n_percepts)) {
                return false;
            }
            if (this.wumpus_alive != other.wumpus_alive) {
                return false;
            }
            if (this.has_arrow != other.has_arrow) {
                return false;
            }
            return true;
        }
    }
    
    private World w;
    private Random random;
    private HashMap<State, double[]> Q;
    private boolean writeQOnGameEnd;
    
    public QLearningAgent(World world) {
        w = world;
        random = new Random();
        Q = readQMatrix();
        writeQOnGameEnd = true;
    }
    
    public QLearningAgent(World world, HashMap<State, double[]> Q) {
        w = world;
        random = new Random();
        this.Q = Q;
        writeQOnGameEnd = false;
    }
    
    
    public void doAction() {
        int x1 = w.getPlayerX();
        int y1 = w.getPlayerY();
        
        
        // Do not run the agent if the game has ended.
        if (w.gameOver())
        {
            return;
        }
        
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
        double r = getReward(previous_world, a1);
        
        System.out.println(getActionString(a1) + " (" + getQValuesString(q_values_1, a1) + ") " + r);
        
        double[] q_values_2;
        if (Q.containsKey(s2)) {
            //System.out.println("Found existing state in Q-matrix");
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
        
        // If the game has ended, write the Q matrix to file.
        if (w.gameOver())
        {
            if (writeQOnGameEnd)
                writeQMatrix(Q);
            System.out.println("-- Episode ended --");
        }
        
        //System.out.println("New Q-Value = " + q_values_1[a1]);
    }
    
    private State createState(int x, int y) {
        State s = new State();

        s.direction = (byte) w.getDirection();
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
        ArrayList<Integer> not_best = new ArrayList<>();
        
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < qValues.length; ++i) {
            max = Math.max(max, qValues[i]);
        }
        
        for (int i = 0; i < qValues.length; ++i) {
            if (qValues[i] == max) {
                best.add(i);
            } else {
                not_best.add(i);
            }
        }
        
        if (random.nextDouble() <= OPTIMAL_CHANCE || not_best.isEmpty()) 
            return best.get(random.nextInt(best.size()));
        else
            return not_best.get(random.nextInt(not_best.size()));
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
    
    private double getReward(World previous, int action) {
        if (action == ACTION_TURN_LEFT || action == ACTION_TURN_RIGHT)
            return REWARD_TURNING;
        if (action == ACTION_MOVE && w.getPlayerX() == previous.getPlayerX() && w.getPlayerY() == previous.getPlayerY())
            return REWARD_BUMPING_INTO_WALL;
        if (action == ACTION_SHOOT && !previous.hasArrow())
            return REWARD_FIRING_WITHOUT_AMMO;
        
        if (w.hasWumpus(w.getPlayerX(), w.getPlayerY()))
            return REWARD_EATEN;
        if (w.hasGold())
            return REWARD_GOLD;
        if (w.hasPit(w.getPlayerX(), w.getPlayerY()) && !previous.hasPit(previous.getPlayerX(), previous.getPlayerY()))
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

    public static HashMap<State, double[]> readQMatrix() {
        HashMap<State, double[]> Q = new HashMap<>();
        
        try (ObjectInputStream fis = new ObjectInputStream(new FileInputStream(new File(Q_FILE_PATH)))) {
            while (fis.available() > 0) {
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
        
        return Q;
    }
    
    public static void writeQMatrix(HashMap<State, double[]> Q) {
        try (ObjectOutputStream fos = new ObjectOutputStream(new FileOutputStream(new File(Q_FILE_PATH), false))) {
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
    
    private String getQValuesString(double[] q_values, int selected_action) {
        StringBuilder sb = new StringBuilder();
        
        for (int a = 0; a < q_values.length; ++a) {
            if (a == selected_action) {
                sb.append("[").append(getActionString(a)).append(":").append(q_values[a]).append("]");
            } else {
                sb.append(getActionString(a)).append(":").append(q_values[a]);
            }
            
            if (a != q_values.length - 1)
                sb.append(", ");
        }
        
        return sb.toString();
    }
}
