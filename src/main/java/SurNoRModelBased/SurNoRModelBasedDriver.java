package SurNoRModelBased;
import java.util.Random;

public class SurNoRModelBasedDriver {
	
	public static void main(String args[])
	{
		WorldModel wm=new WorldModel(4, 2);
		
		Random rand = new Random(); //instance of random class
	    int cstate = rand.nextInt(4); 
	    
	   for(int timestep=0;timestep<50;timestep++)
	    {
	    	int sprime=rand.nextInt(4);//next state
	   	    int action=rand.nextInt(2); //action
	   	    
	   	   // System.out.println("\nCurrent State: "+cstate+" Selected Action: "+action+" Next State: "+sprime);
	   	    System.out.println("Timestep: "+timestep);
	   	    System.out.println("\nCurrent State: "+cstate+" Selected Action: "+action+" Next State: "+sprime);
	   	   
	   	    wm.calculateSurprise(cstate, action, sprime);
	   	   	wm.computeAdaptationRate();
			wm.updateBelief(cstate, action, sprime);
			wm.updateTransitionModel();
			
			cstate=sprime;
	    	
	    }
		
		
		
	}

}
