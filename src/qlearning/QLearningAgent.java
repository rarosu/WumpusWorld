package qlearning;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import wumpusworld.*;

public class QLearningAgent {    
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
    
    private static final int[] neighbour_coordinates_x = { 1, 0, -1, 0 };
    private static final int[] neighbour_coordinates_y = { 0, 1, 0, -1 };
    private static final int[] n2n_coordinates_x = { 2, 1, 0, -1, -2, -1, 0, 1 };
    private static final int[] n2n_coordinates_y = { 0, 1, 2, 1, 0, -1, -2, -1 };
    
    private static final double ALPHA = 0.1;
    private static final double GAMMA = 0.5;
    
    private class State
    {
        public byte percepts;
        public byte hazards;
        public byte[] neighbour_type = new byte[4];
        public byte[] neighbour_hazards = new byte[4];
        public byte[] n2n_type = new byte[8];
        public byte[] n2n_percepts = new byte[8];
        public boolean wumpus_alive;
        public boolean has_arrow;
        
        public State()
        {
            
        }
        
        public State(FileInputStream fis)
        {
            
        }
    }
    
    private World w;
    private Random random;
    private HashMap<State, double[]> Q;
    
    public QLearningAgent(World world) {
        this.w = world;
        this.random = new Random();
        this.Q = new HashMap<>();
        
        // TODO: Load Q-Values
        File Q_file = new File("Q.dat");
        try {
            FileInputStream fis = new FileInputStream(Q_file);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(QLearningAgent.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    public void doAction() {
        int x = w.getPlayerX();
        int y = w.getPlayerY();
        
        // Immediately grab gold on the first turn.
        if (w.hasGlitter(x, y))
        {
            w.doAction(World.A_GRAB);
            return;
        }
        
        // Immediately climb out of the pit.
        if (w.hasPit(x, y))
        {
            w.doAction(World.A_CLIMB);
        }
        
        // Do the best action for our current state.
        World previous_world = w.clone();
        State s = createState(x, y);

        double[] qValues;
        if (Q.containsKey(s)) {
            qValues = Q.get(s);
        } else {
            qValues = new double[ACTION_COUNT];
            Q.put(s, qValues);
        }

        int a = getBestAction(qValues);

        w.doAction(getActionString(a));
        
        // Grab the gold if we've encountered it, climb out of pits if we're in them and there is no gold there.
        if (w.hasGlitter(x, y))
        {
            w.doAction(World.A_GRAB);
        }
        else if (w.hasPit(x, y))
        {
            w.doAction(World.A_CLIMB);
        }

        // Do the reinforcement depending on the new state.
        int x2 = w.getPlayerX();
        int y2 = w.getPlayerY();
        State s2 = createState(x2, y2);
        double r = getReward(previous_world);
        
        double[] newQValues;
        if (Q.containsKey(s)) {
            newQValues = Q.get(s);
        } else {
            newQValues = new double[ACTION_COUNT];
            Q.put(s, qValues);
        }
        
        // Calculate the new Q-value for the taken action.
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < newQValues.length; ++i)
            max = Math.max(max, newQValues[i]);
        qValues[a] = qValues[a] + ALPHA * (r + GAMMA * max - qValues[a]);
        
        System.out.println("New Q-Value = " + qValues[a]);
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
        for (int i = 0; i < 4; ++i)
        {
            int nx = x + neighbour_coordinates_x[i];
            int ny = y + neighbour_coordinates_y[i];
            
            if (w.isValidPosition(nx, ny))
            {
                if (w.isUnknown(nx, ny))
                {
                    s.neighbour_type[i] = TYPE_UNKNOWN;
                    s.neighbour_hazards[i] = 0;
                }
                else
                {
                    s.neighbour_type[i] = TYPE_NORMAL;
                    
                    if (w.hasPit(nx, ny))
                        s.neighbour_hazards[i] |= HAZARD_PIT;
                    if (w.hasWumpus(nx, ny))
                        s.neighbour_hazards[i] |= HAZARD_WUMPUS;
                }
            }
            else
            {
                s.neighbour_type[i] = TYPE_WALL;
                s.neighbour_hazards[i] = 0;
            }
        }
        
        // Check percepts in our neighbours' neighbours.
        for (int i = 0; i < 8; ++i)
        {
            int nx = x + n2n_coordinates_x[i];
            int ny = y + n2n_coordinates_y[i];
            
            if (w.isValidPosition(nx, ny))
            {
                if (w.isUnknown(nx, ny))
                {
                    s.n2n_type[i] = TYPE_UNKNOWN;
                    s.n2n_percepts[i] = 0;
                }
                else
                {
                    s.n2n_type[i] = TYPE_NORMAL;
                    
                    if (w.hasBreeze(nx, ny))
                        s.n2n_percepts[i] |= PERCEPT_BREEZY;
                    if (w.hasStench(nx, ny))
                        s.n2n_percepts[i] |= PERCEPT_STENCH;
                }
            }
            else
            {
                s.n2n_type[i] = TYPE_WALL;
                s.n2n_percepts[i] = 0;
            }
        }
        
        return s;
    }
    
    private int getBestAction(double[] qValues) {
        ArrayList<Integer> best = new ArrayList<>();
        
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < qValues.length; ++i)
        {
            max = Math.max(max, qValues[i]);
        }
        
        for (int i = 0; i < qValues.length; ++i)
        {
            if (qValues[i] == max)
            {
                best.add(i);
            }
        }
        
        // TODO: Sometimes randomize for non-optimal actions.
        
        return best.get(random.nextInt(best.size()));
    }
    
    private String getActionString(int action) {
        switch (action)
        {
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
        if (!w.hasArrow() && previous.hasArrow())
        {
            if (!w.wumpusAlive())
                return REWARD_WUMPUS_KILLED;
            else
                return REWARD_ARROW_MISSED;
        }
        
        if (previous.isUnknown(w.getPlayerX(), w.getPlayerY()))
            return REWARD_EXPLORED_TILE;
        
        return 0.0;
    }
}
