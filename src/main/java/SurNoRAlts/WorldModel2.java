package SurNoRModelBased;
public class WorldModel {
	
	//Two below represent state/actions for SAS
	int number_of_states;
	int number_of_actions;
	
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
	
	double curiousity[] = {1,1,1,1,1,1,1,1};
	
	public WorldModel(int statesnum, int actionsnum)
	{
		number_of_states=statesnum;
		number_of_actions=actionsnum;
		
		//////////////////////////////////////////
		trans=new double[number_of_states][number_of_actions][number_of_states];
		trans_t1=new double[number_of_states][number_of_actions][number_of_states];
		//trans_tm1=new double[number_of_states][number_of_actions][number_of_states];
		
		alpha=new double[number_of_states][number_of_actions][number_of_states];
		alpha_t1=new double[number_of_states][number_of_actions][number_of_states];
		
		
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
			for(int i=0;i<number_of_states;i++)
			{
					for(int j=0;j<number_of_states;j++)
					{
						
						alpha_t1[i][a][j]=(double) 1;
						alpha[i][a][j]=(double) 1;
						if (j==0) {
							alpha[i][a][j]=(double) 64;
							alpha_t1[i][a][j]=(double) 2;
						} else if ((j<3)||(j==4)) {
							alpha[i][a][j]=(double) 16;
							alpha_t1[i][a][j]=(double) 1;
							if ((a==0) && (j==2)) {
								alpha[i][a][j]=(double) 8;
							} else if ((a==1)&&(j!=2)) {
								alpha[i][a][j]=(double) 8;
							}

						} else if (j < 7) {
							alpha[i][a][j]=(double) 4;
						} else {
							alpha[i][a][j]=(double) 1;
						}
						
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
	
		for(int nextstate=0;nextstate<number_of_states;nextstate++)
		{
			if(nextstate==sprime)
			{
				kroneckers_delta=1.0;
			}
			else {
				kroneckers_delta=0.0;
			}


			//We update our beliefs using the adaptation rtate, the current belief, and the (I think) new belief
			//This is the pseudo-count/belief equation from novelty is not surprise
			
			//We only perform this update on the current state-action pair, with an additional value if the next state is the one actually selected
			alpha[cstate][selected_action][nextstate]=((1-adaptation_rate)*alpha[cstate][selected_action][nextstate])+(adaptation_rate*alpha_t1[cstate][selected_action][nextstate])+kroneckers_delta;
			//double temp =((1-adaptation_rate)*Math.log(alpha[cstate][selected_action][nextstate]))+(adaptation_rate*Math.log(alpha_t1[cstate][selected_action][nextstate]))+kroneckers_delta;
			//alpha[cstate][selected_action][nextstate] = Math.exp(temp);
			//alpha[cstate][selected_action][nextstate] = 1/alpha[cstate][selected_action][nextstate];
			
			//alpha[cstate][selected_action][nextstate]=((1-adaptation_rate)*alpha[cstate][selected_action][nextstate])+kroneckers_delta;
			/*
			if(kroneckers_delta==0) {
				curiousity[nextstate] = curiousity[nextstate]+1;
				if (Math.random() > 0.98 + (1/(curiousity[nextstate]))) {
					alpha_t1[cstate][selected_action][nextstate] = alpha_t1[cstate][selected_action][nextstate] + 1;
				}
			}
			else {
				curiousity[nextstate] = 1;
				if (alpha_t1[cstate][selected_action][nextstate] > 2) {
					alpha_t1[cstate][selected_action][nextstate] = alpha_t1[cstate][selected_action][nextstate] - 1;
				}
				else {
					alpha_t1[cstate][selected_action][nextstate] = 1;
				}
			}
			*/
			/*
			 * Noticed that the methods seemed to try and maximize belief size when it should try and keep it close to one (initial dist)
			 * to reduce surprise
			 */
			
			//System.out.print(alpha[cstate][selected_action][nextstate]+" ");
		
						
			///////////////////////////////////////////////			
						
						
		
			
		}
		
	}
	
	//Equation 3 (I think) from Novelty is not surprise

	public void updateTransitionModel()
	{
		//trans_tm1 = trans;
		System.out.println("Update Transition Model");
		for(int action=0;action<number_of_actions;action++)
		{
			System.out.println("action: "+action);
			for(int sprime=0;sprime<number_of_states;sprime++)
			{
				for(int cstate=0;cstate<number_of_states;cstate++)
				{
					
					double numerator1=alpha[cstate][action][sprime];
					//double numerator1=alpha[cstate][action][sprime]+adaptation_rate*alpha_t1[cstate][action][sprime];
					double denominator1=0;
					
					
					for(int i=0;i<number_of_states;i++)
					{
						denominator1=denominator1+alpha[cstate][action][i];
						//denominator1=denominator1+alpha[cstate][action][i]+adaptation_rate*alpha_t1[cstate][action][i];
					}
					
					trans[cstate][action][sprime]=(numerator1)/(denominator1);
					

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
		return alpha[cs][selectedAction];
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
}
