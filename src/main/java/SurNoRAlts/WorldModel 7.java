package SurNoRModelBased;
public class WorldModel {
	
	//Two below represent state/actions for SAS
	int number_of_states;
	int number_of_actions;
	int number_of_nfr = 3;
	
	//Below contains the surprise value
	double BayesFactorSurprise; //surprise taken to be Bayesian factor here, should eventually be changed to CCS
	double CCS;
	double adaptation_rate; //The adaptation rate balances new and old info, correspond to gamma in the paper
	
	/*
	* Contains transition probabilities
	* These are also currently used to calculate BF surprise
	*/
	double trans[][][];
	double trans_t1[][][];
	double trans_tm1[][][];
	/*
	 * Alpha is the "belief in transition" corresponds to likelihood a specific transition is chosen
	 * 
	 */
	double alpha[][][];
	double alpha_t1[][][];
	double alpha_t12[][][];
	double alpha_probs[][][];
	// Go from 1 to 8, NFR in the order MC, MR, MP
	int NFR[][] = {{1,1,1},{1,1,0},{1,0,1},{1,0,0},{0,1,1},{0,1,0},{0,0,1},{0,0,0}};
	
	double curiousity[] = {1,1,1,1,1,1,1,1};
	
	public WorldModel(int statesnum, int actionsnum)
	{
		number_of_states=statesnum;
		number_of_actions=actionsnum;
		
		//////////////////////////////////////////
		trans=new double[number_of_states][number_of_actions][number_of_states];
		trans_t1=new double[number_of_states][number_of_actions][number_of_states];
		//trans_tm1=new double[number_of_states][number_of_actions][number_of_states];
		
		alpha=new double[number_of_nfr*2][number_of_actions][number_of_nfr];
		alpha_t1=new double[number_of_nfr*2][number_of_actions][number_of_nfr];
		alpha_t12=new double[number_of_nfr*2][number_of_actions][number_of_nfr];
		
		alpha_probs=new double[number_of_nfr*2][number_of_actions][number_of_nfr];
		
		
		//initializing prior belief and transitions on the basis of prior belief
		//add equation to calculate initial transitions
		/*
		 *  Alpha is initial distributed uniformly
		 * Transition probabilieis are done based on this "prior"
		 */
		System.out.println("Initializing alphas");
		for(int a=0;a<number_of_actions;a++)	
		{
			System.out.println("action: "+a);
			for(int i=0;i<number_of_nfr*2;i++)
			{
					for(int j=0;j<number_of_nfr;j++)
					{
						
						alpha_probs[i][a][j] = (double) 1/3;
						alpha_t1[i][a][j]=(double) 1;
						alpha_t12[i][a][j]=(double) 1/3;
						alpha[i][a][j]=(double) 1;
						
						System.out.print(alpha[i][a][j]+" ");
									
					}
					System.out.println();
					//double[] temp = {1,20,1,20,1,20,1,20};
					//alpha_t1[i][a] = temp;
		  }
		}
		
		//computing initial transitions
		//System.out.println("initializing transition probabilities");
				for(int action=0;action<number_of_actions;action++)
				{
					System.out.println("action: "+action);
						for(int sprime=0;sprime<number_of_states;sprime++) //sprime is our potential next state
						{
							for(int cstate=0;cstate<number_of_states;cstate++) //cstate I believe is current state
							{
								
								//Calculate initial denominator
								
								//Based on numerator and denominator we calculate the transition probabilities
								trans_t1[cstate][action][sprime]=(double) 1/number_of_states;
								trans[cstate][action][sprime]=(double) 1/number_of_states;
								//trans_tm1[cstate][action][sprime]=(double) 1/number_of_states;

								System.out.print(trans[cstate][action][sprime]+" ");
							}
							System.out.println();
						}
				}
				
		
		
		
		
		
			
		
	}
	
	/**
	 * Calculate Surprise
	 * @param cstate
	 * @param action
	 * @param sprime
	 */
	//for all potential actions, and all potential next states, and all potential current states...
	public void calculateSurprise(int cstate, int action, int sprime)
	{
		////////////////////////////////////////

		//BayesFactorSurprise=trans_t1[cstate][action][sprime]/trans[cstate][action][sprime];
		//CCS = BayesFactorSurprise;
		CCS = klDivergence(trans[cstate][action], trans_t1[cstate][action]);
		//CCS = 1-CCS;
		//System.out.println(trans_t1[cstate][action][sprime]+"  "+trans[cstate][action][sprime]);
		//System.out.println("Surprise: "+CCS);
				
		//////////////////////////////////////
		
			
	}
	
	public void computeAdaptationRate(double pc)
	{
		///add m=pc/1-pc   /////// pc is the probability of change represents volatility of environment
		//double pc = 0.64;
		double m=pc/(1-pc);//m=0.5;
		adaptation_rate= (m*CCS)/(1+(m*CCS)); //equation for gamma from the paper
		//System.out.println("Adaptation Rate: "+adaptation_rate);
	}
	

	public void updateBelief(int cstate,int selected_action,int sprime)
	{
		double kroneckers_delta; //This will hold either 0 or 1 based on if the next state is the same as the current state or not
		System.out.println("update belief");
		
		/*
		 * What we are checking is if we are entering this state (sprime) and based on which NFR's are satisfied update the
		 * likelihood of each NFR being satisfied by taking the action on the previous state that lead to sprime
		*/
		int other_action = 1 - selected_action;
		for (int sat_NFR=0; sat_NFR<number_of_nfr*2; sat_NFR++) {
			//specific_NFR and cnfr are used to decide if we should update the transition probabilities for the satisfied or unsatisfied versions
			int specific_NFR = NFR[cstate][sat_NFR%3];
			int cnfr = sat_NFR + 3*(1-specific_NFR);
			for (int next_NFR=0; next_NFR<number_of_nfr; next_NFR++) {
				int new_NFR = NFR[sprime][next_NFR];
				if (((new_NFR==1)&&(sat_NFR<3)) | ((new_NFR==0) && (sat_NFR>2))) {
					kroneckers_delta=(double) 1.0;
				}
				else {
					kroneckers_delta = (double) 0;
				}
				alpha[sat_NFR][selected_action][new_NFR] = ((1-adaptation_rate)*alpha[sat_NFR][selected_action][new_NFR])+(adaptation_rate*alpha_t1[sat_NFR][selected_action][new_NFR])+kroneckers_delta;
				alpha[sat_NFR][other_action][new_NFR] = ((1-adaptation_rate)*alpha[sat_NFR][other_action][new_NFR])+(adaptation_rate*alpha_t1[sat_NFR][other_action][new_NFR])+1-kroneckers_delta;
				
			}
		}
		
		for(int action=0;action<number_of_actions;action++)
		{
			for(int infr=0;infr<number_of_nfr*2;infr++)
			{
				for(int given_NFR=0;given_NFR<number_of_nfr;given_NFR++)
				{
					
					double numerator1=alpha[infr][action][given_NFR];
					//double numerator1=alpha[cstate][action][sprime]+adaptation_rate*alpha_t1[cstate][action][sprime];
					double denominator1=0;
					
					
					for(int i=0;i<number_of_nfr;i++)
					{
						denominator1=denominator1+alpha[infr][action][i];
						//denominator1=denominator1+alpha[cstate][action][i]+adaptation_rate*alpha_t1[cstate][action][i];
					}
					
					alpha_probs[infr][action][given_NFR]=(numerator1)/(denominator1);

				}
			}
		}
		
	}
	
	//Equation 3 (I think) from Novelty is not surprise

	public void updateTransitionModel()
	{
		//trans_tm1 = trans;
		System.out.println("Update Transition Model");
		
				/*
				 * We use cstate on alpha probs because alpha probs gives the probability of an NFR being satisfied if we are in a state
				 * and take action a, this is important as when calcuating the probability of entering a new state sprime it will be the
				 * joint probility of all NFR's being satisfied when in the current state taking action a; NOT the probability of being
				 * in the new state and taking action a
				 */
		for (int action = 0; action<number_of_actions; action++) {
			for (int cstate=0; cstate<number_of_states; cstate++) {
				double denominator1=0;
				for(int sprime=0; sprime<number_of_states;sprime++) {

					trans[cstate][action][sprime] = 1;
					for (int i = 0; i < number_of_nfr; i++) {
						int cnfr = i +3*(1-NFR[cstate][i]);
						int nnfr = NFR[sprime][i];
						double mult;
						if (nnfr==1) {
							mult = 1-alpha_probs[cnfr][action][i]; 
						} else {
							mult = (alpha_probs[cnfr][action][i]); 
						}
						trans[cstate][action][sprime] = trans[cstate][action][sprime]*mult;
					}
					denominator1=denominator1+trans[cstate][action][sprime];
				}
				double rn_sum = 0;
				for(int sprime=0;sprime<number_of_states;sprime++)
				{
					
					double numerator1=trans[cstate][action][sprime];
					
					trans[cstate][action][sprime]=(numerator1)/(denominator1);
					trans[cstate][action][sprime]=rounder(trans[cstate][action][sprime]);
					rn_sum = rn_sum + trans[cstate][action][sprime];

				}
				if (rn_sum != 1) {
					System.out.println("Check");
					System.out.println(rn_sum);
					trans[cstate][action][0] = trans[cstate][action][0] + (1-rn_sum);
				}
				
				//System.out.println();
			}
			//System.out.println();
			
			
			
		}
		
				
	}
	/*
	public void updateTransitionModel() {
		for(int i=0; i < number_of_states;i++) {
			for(int j=0; j<number_of_actions;j++) {
				double numsum = 0;
				for(int e=0; e < number_of_states;e++) {
					double newnum = (double) Math.random();
					trans[i][j][e] = newnum;
					numsum = numsum + newnum;
				}
				for(int e=0; e < number_of_states;e++) {
					trans[i][j][e] = trans[i][j][e]/numsum;
				}
			}
		}
	}
	*/
	
	
	public void SurNorModelBasedBranch()
	{
		
	}
	
	//function to get belief values prior to a full run on a time step
	/*
	 * NEED TO ADD:
	 * I probably want to be able to select for row/columns
	 * What I meant above is I need to only return the double[] of values corresponding to current state and selected action
	 */
	public double[] getBelief(int cs, int selectedAction)
	{
		return alpha_probs[cs][selectedAction];
	}
	
	public double[][][] getTransitionFunction()
	{
		return trans;
	}
	
	public static final double log2 = Math.log(2);
	
	public static double klDivergence(double[] p1, double[] p2) {


	      double klDiv = 0.0;

	      for (int i = 0; i < p1.length; ++i) {
	        if (p1[i] == 0) { continue; }
	        if (p2[i] == 0.0) { continue; } // Limin

	      klDiv += p1[i] * Math.log( p1[i] / p2[i] );
	      }

	      return klDiv / log2; // moved this division out of the loop -DM
	    }
	public double getSurprise()
	{
		return CCS;
	}
	
	public double getAR()
	{
		return adaptation_rate;
	}
	
	public double rounder(double value) {
		value = (double)Math.round(value * 100000d) / 100000d;
		return value;
	}
}
