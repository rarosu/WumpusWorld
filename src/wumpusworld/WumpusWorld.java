package wumpusworld;

import java.util.HashMap;
import java.util.Vector;
import qlearning.QLearningAgent;
/**
 * Starting class for the Wumpus World program. The program
 * has three options: 1) Run a GUI where the Wumpus World can be
 * solved step by step manually or by an agent, or 2) run
 * a simulation with random worlds over a number of games,
 * or 3) run a simulation over the worlds read from a map file.
 * 
 * @author Johan Hagelbäck
 */
public class WumpusWorld {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
        WumpusWorld ww = new WumpusWorld();
    }
    
    /**
     * Starts the program.
     * 
     */
    public WumpusWorld()
    {
        String option = Config.getOption();
        
        if (option.equalsIgnoreCase("gui"))
        {
            showGUI();
        }
        if (option.equalsIgnoreCase("sim"))
        {
            runSimulator();
        }
        if (option.equalsIgnoreCase("simdb"))
        {
            runSimulatorDB();
        }
    }
    
    /**
     * Starts the program in GUI mode.
     */
    private void showGUI()
    {
        GUI g = new GUI();
    }
    
    private static final int COUNT = 10000;
    
    /**
     * Starts the program in simulator mode with
     * maps read from a data file.
     */
    private void runSimulatorDB()
    {
        HashMap<QLearningAgent.State, double[]> Q = QLearningAgent.readQMatrix();
        
        MapReader mr = new MapReader();
        Vector<WorldMap> maps = mr.readMaps();
        final int C = COUNT / maps.size();
        
        double totScore = 0;
        for (int k = 0; k < C; k++)
        {
            for (int i = 0; i < maps.size(); i++)
            {
                World w = maps.get(i).generateWorld();
                totScore += (double)runSimulation(w, Q);
            }
        }
        totScore = totScore / ((double)maps.size() * C);
        System.out.println("Average score: " + totScore);
        
        QLearningAgent.writeQMatrix(Q);
    }
    
    
    
    /**
     * Starts the program in simulator mode
     * with random maps.
     */
    private void runSimulator()
    {
        HashMap<QLearningAgent.State, double[]> Q = QLearningAgent.readQMatrix();
        
        double totScore = 0;
        for (int i = 0; i < COUNT; i++)
        {
            WorldMap w = MapGenerator.getRandomMap(i);
            totScore += (double)runSimulation(w.generateWorld(), Q);
        }
        totScore = totScore / (double)COUNT;
        System.out.println("Average score: " + totScore);
        
        QLearningAgent.writeQMatrix(Q);
    }
    
    /**
     * Runs the solver agent for the specified Wumpus
     * World.
     * 
     * @param w Wumpus World
     * @return Achieved score
     */
    private int runSimulation(World w, HashMap<QLearningAgent.State, double[]> Q)
    {
        int actions = 0;
        Agent a = new MyAgent(w, Q);
        while (!w.gameOver())
        {
            a.doAction();
            actions++;
        }
        int score = w.getScore();
        System.out.println("Simulation ended after " + actions + " actions. Score " + score);
        return score;
    }
}
