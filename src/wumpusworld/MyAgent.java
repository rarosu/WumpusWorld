package wumpusworld;

import qlearning.*;

/**
 * Contans starting code for creating your own Wumpus World agent.
 * Currently the agent only make a random decision each turn.
 * 
 * @author Johan Hagelbäck
 */
public class MyAgent implements Agent
{
    private World w;
    private QLearningAgent agent;
    
    /**
     * Creates a new instance of your solver agent.
     * 
     * @param world Current world state 
     */
    public MyAgent(World world)
    {
        w = world;
        agent = new QLearningAgent(w);
    }
    
    /**
     * Asks your solver agent to execute an action.
     */
    public void doAction()
    {
        agent.doAction();
    }
}
