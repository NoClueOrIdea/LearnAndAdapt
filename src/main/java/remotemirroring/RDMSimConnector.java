package remotemirroring;


import program.POMDP;
import rdm.management.Effector;
import rdm.management.NetworkManagment;
import rdm.management.Probe;
import rdm.network.Monitorables;
import solver.BeliefPoint;

public class RDMSimConnector {
	
	public static NetworkManagment network_management;
	public static boolean refsetcreation=false;
	public static Probe probe;
	public static Effector effector;
	public static int timestep;
	public static Monitorables monitorables;
	
	public static POMDP p;
	
	
	
	
	public RDMSimConnector()
	{
		network_management=new NetworkManagment();
		probe=network_management.getProbe();
		effector=network_management.getEffector();
	}
	
	public int performAction(int selectedaction)
	{
		
	////Perform ITP or DTP on the link on the simulator
			///return rewards and observations
			//update belief value and change initial belief
			
			///Immediate Reward
			double r=p.getReward(p.getCurrentState(), selectedaction);
			int nextstate;
			if(selectedaction==0)
			{
				
				
				nextstate=p.nextStateRDM(p.getCurrentState(), selectedaction);
				p.setCurrentState(nextstate);
				
			}
			else {
				
				nextstate=p.nextStateRDM(p.getCurrentState(), selectedaction);
				p.setCurrentState(nextstate);
			}
			
			///Observation
			int obs=p.getObservation(selectedaction, nextstate);
			BeliefPoint b=p.updateBelief(p.getInitialBelief(), selectedaction, obs);
			p.setInitialBelief(b);
			
			//p.getReward(s, action);
			
			/*S currentS  = states.stateIdentifier(currentState);
			
			S nextState = this.transitions.nextState(currentS, action);
			
			this.currentState = states.stateNumber(nextState);
			
			
			double[] reward = this.rewards.getReward(currentS, action, nextState);
			
			
			
				O obs = this.observationFunction.getObservation(action, nextState);
				
				this.beliefUpdate(action, obs);*/
		
			
			
			return 0;

			
		
	}
	
	

}
