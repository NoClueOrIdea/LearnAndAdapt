/*******************************************************************************
 * SolvePOMDP
 * Copyright (C) 2017 Erwin Walraven
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/

package program;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;

import org.json.simple.parser.ParseException;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

import pruning.PruneStandard;
import rdm.management.NetworkManagment;
import rdm.management.RDMSimulator;
import remotemirroring.RDMSimConnector;
import remotemirroring.RDMTransitionProb;
import remotemirroring.ResultsLog;
import pruning.PruneAccelerated;
import pruning.PruneMethod;
import solver.AlphaVector;
import solver.BeliefPoint;
import solver.Solver;
import solver.SolverApproximate;
import solver.SolverExact;

import lpsolver.LPGurobi;
import lpsolver.LPModel;
import lpsolver.LPSolve;
import lpsolver.LPjoptimizer;

import SurNoRModelBased.WorldModel;
import SurNoRModelBased.ConfigEdit;




public class SolvePOMDP {
	private SolverProperties sp;     // object containing user-defined properties
	private PruneMethod pm;          // pruning method used by incremental pruning
	private LPModel lp;              // linear programming solver used by incremental pruning
	private Solver solver;           // the solver that we use to solve a POMDP, which is exact or approximate
	private String domainDirName;    // name of the directory containing .POMDP files
	private String domainDir;        // full path of the domain directory
	private double pc;
	public SolvePOMDP(double val) {
		// read parameters from config file
		readConfigFile();
		
		// check if required directories exist
		configureDirectories();
		
		this.pc = val;

		// configure LP solver
		lp.setEpsilon(sp.getEpsilon());
		lp.setAcceleratedLPThreshold(sp.getAcceleratedLPThreshold());
		lp.setAcceleratedLPTolerance(sp.getAcceleratedLPTolerance());
		lp.setCoefficientThreshold(sp.getCoefficientThreshold());
		lp.init();
	}
	
	/**
	 * Read the solver.config file. It creates a properties object and it initializes
	 * the pruning method and LP solver.
	 */
	private void readConfigFile() {
		this.sp = new SolverProperties();
		
		Properties properties = new Properties();
		
		try {
			FileInputStream file = new FileInputStream("./solver.config");
			properties.load(file);
			file.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		sp.setEpsilon(Double.parseDouble(properties.getProperty("epsilon")));
		sp.setValueFunctionTolerance(Double.parseDouble(properties.getProperty("valueFunctionTolerance")));
		sp.setAcceleratedLPThreshold(Integer.parseInt(properties.getProperty("acceleratedLPThreshold")));
		sp.setAcceleratedLPTolerance(Double.parseDouble(properties.getProperty("acceleratedTolerance")));
		sp.setCoefficientThreshold(Double.parseDouble(properties.getProperty("coefficientThreshold")));
		sp.setOutputDirName(properties.getProperty("outputDirectory"));
		sp.setTimeLimit(Double.parseDouble(properties.getProperty("timeLimit")));
		sp.setBeliefSamplingRuns(Integer.parseInt(properties.getProperty("beliefSamplingRuns")));
		sp.setBeliefSamplingSteps(Integer.parseInt(properties.getProperty("beliefSamplingSteps")));
		this.domainDirName = properties.getProperty("domainDirectory");
		String algorithmType = properties.getProperty("algorithmType");
		
		if(!algorithmType.equals("perseus") && !algorithmType.equals("gip")) {
			throw new RuntimeException("Unexpected algorithm type in properties file");
		}
		
		String dumpPolicyGraphStr = properties.getProperty("dumpPolicyGraph");
		if(!dumpPolicyGraphStr.equals("true") && !dumpPolicyGraphStr.equals("false")) {
			throw new RuntimeException("Policy graph property must be either true or false");
		}
		else {
			sp.setDumpPolicyGraph(dumpPolicyGraphStr.equals("true") && algorithmType.equals("gip"));
		}
		
		String dumpActionLabelsStr = properties.getProperty("dumpActionLabels");
		if(!dumpActionLabelsStr.equals("true") && !dumpActionLabelsStr.equals("false")) {
			throw new RuntimeException("Action label property must be either true or false");
		}
		else {
			sp.setDumpActionLabels(dumpActionLabelsStr.equals("true"));
		}
		
		System.out.println();
		System.out.println("=== SOLVER PARAMETERS ===");
		System.out.println("Epsilon: "+sp.getEpsilon());
		System.out.println("Value function tolerance: "+sp.getValueFunctionTolerance());
		System.out.println("Accelerated LP threshold: "+sp.getAcceleratedLPThreshold());
		System.out.println("Accelerated LP tolerance: "+sp.getAcceleratedLPTolerance());
		System.out.println("LP coefficient threshold: "+sp.getCoefficientThreshold());
		System.out.println("Time limit: "+sp.getTimeLimit());
		System.out.println("Belief sampling runs: "+sp.getBeliefSamplingRuns());
		System.out.println("Belief sampling steps: "+sp.getBeliefSamplingSteps());
		System.out.println("Dump policy graph: "+sp.dumpPolicyGraph());
		System.out.println("Dump action labels: "+sp.dumpActionLabels());
		
		// load required LP solver
		String lpSolver = properties.getProperty("lpsolver");
		if(lpSolver.equals("gurobi")) {
			this.lp = new LPGurobi();
		}
		else if(lpSolver.equals("joptimizer")) {
			this.lp = new LPjoptimizer();
		}
		else if(lpSolver.equals("lpsolve")) {
			this.lp = new LPSolve();
		}
		else {
			throw new RuntimeException("Unexpected LP solver in properties file");
		}
		
		// load required pruning algorithm
		String pruningAlgorithm = properties.getProperty("pruningMethod");
		if(pruningAlgorithm.equals("standard")) {
			this.pm = new PruneStandard();
			this.pm.setLPModel(lp);
		}
		else if(pruningAlgorithm.equals("accelerated")) {
			this.pm = new PruneAccelerated();
			this.pm.setLPModel(lp);
		}
		else {
			throw new RuntimeException("Unexpected pruning method in properties file");
		}
		
		// load required POMDP algorithm
		if(algorithmType.equals("gip")) {
			this.solver = new SolverExact(sp, lp, pm);
		}
		else if(algorithmType.equals("perseus")) {
			this.solver = new SolverApproximate(sp, new Random(222));
		}
		else {
			throw new RuntimeException("Unexpected algorithm type in properties file");
		}
		
		System.out.println("Algorithm: "+algorithmType);
		System.out.println("LP solver: "+lp.getName());
	}
	
	/**
	 * Checks if the desired domain and output directories exist, and it sets the full path to these directories.
	 */
	private void configureDirectories() {
		String path = SolvePOMDP.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		String decodedPath = "";
		
		try {
			decodedPath = URLDecoder.decode(path, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		if(decodedPath.endsWith(".jar")) {
			// solver has been started from jar, so we assume that output exists in the same directory as the jar file			
			int endIndex = decodedPath.lastIndexOf("/");
			String workingDir = decodedPath.substring(0, endIndex);
			sp.setWorkingDir(workingDir);
			domainDir = workingDir+"/"+domainDirName;
		}
		else {
			// solver has not been started from jar, so we assume that output exists in the current directory
			sp.setWorkingDir("");
			domainDir = domainDirName;
		}	

		File dir = new File(sp.getOutputDir());
		if(!dir.exists() || !dir.isDirectory()) {
			throw new RuntimeException("Output directory could not be found");
		}
		
		dir = new File(domainDir);
		if(!dir.exists() || !dir.isDirectory()) {
			throw new RuntimeException("Domain directory could not be found");
		}
		
		System.out.println("Output directory: "+sp.getOutputDir());
		System.out.println("Domain directory: "+domainDir);
	}
	
	/**
	 * Close the LP solvers
	 */
	public void close () {
		lp.close();
	}
	
	/**
	 * Solve a POMDP defined by a .POMDP file
	 * @param pomdpFileName filename of a domain in the domain directory
	 */
	public void run(String pomdpFileName) {
		
		if(pomdpFileName.equals("IoT.POMDP"))
		{
			runCaseIoT(pomdpFileName);
		}
		else if(pomdpFileName.equals("RDM.POMDP"))
		{
			runCaseRDM(pomdpFileName);
		}
		
		
	}
	
	/**
	 * Method to run experiments for RDM Case Study
	 * @param pomdpFileName
	 */
	public void runCaseRDM(String pomdpFileName)
	{
		

			
			
		// read POMDP file
		 int mst_cnt=0,rt_cnt=0;
	     RDMSimConnector con=new RDMSimConnector();
	     RDMTransitionProb rdmTran=new RDMTransitionProb();
	     
	     WorldModel wm = new WorldModel(8, 2);
	     
	     POMDP pomdp = Parser.readPOMDP(domainDir+"/"+pomdpFileName);
	     
	     

	     
	     
	     int rt[] = {0, 0, 0};
	     double state_counts[] = {0, 0, 0, 0, 0, 0, 0, 0, 0};
	     
	     double last_nal = -1;
	     
	     double[] obs_now;
	     
	     double[] surprise_before;
	     double[] surprise_now;
	     
	    
	     int currentscenario_case=RDMSimConnector.network_management.simulation_properties.getUncertaintyScenario().getCurrentScenario();
	     
	     //Code for generating results
	     int count = 1;
	     double temp = (this.pc*100);
	     int cpc = (int) temp;
	     String dirname = "Results/" + "PC " + cpc + ")/Scenario "+ currentscenario_case +"/Run " +count + "/";
	     while(true) {
	    	 dirname = "Results/"+ "PC " + cpc + "/Scenario "+ currentscenario_case +"/Run " +count + "/";
	    	 File directory = new File(dirname);
	    	 if(directory.exists()) {
	    		 count = count+1;
	    	 }
	    	 else {
	    		 directory.mkdirs();
	    		 break;
	    	 }
	    	 
	     }
	     System.out.println(dirname);
	     System.out.println(dirname+"MCRegressionResultsSolvePOMDP.txt");
	     
			try {
				//Results Regression
				FileWriter fw_mc_regr=new FileWriter(dirname+"MCRegressionResultsSolvePOMDP.txt");
				PrintWriter pw_mc_regr=new PrintWriter(fw_mc_regr);
				FileWriter fw_mr_regr=new FileWriter(dirname+"MRRegressionResultsSolvePOMDP.txt");
				PrintWriter pw_mr_regr=new PrintWriter(fw_mr_regr);
				FileWriter fw_mp_regr=new FileWriter(dirname+"MPRegressionResultsSolvePOMDP.txt");
				PrintWriter pw_mp_regr=new PrintWriter(fw_mp_regr);
				
				//Bug Testing
				FileWriter fw_action=new FileWriter(dirname+"SelectedActions.txt");
				PrintWriter pw_action=new PrintWriter(fw_action);
				FileWriter fw_state=new FileWriter(dirname+"Current States.txt");
				PrintWriter pw_state=new PrintWriter(fw_state);
				FileWriter fw_trans=new FileWriter(dirname+"Transitions.txt");
				PrintWriter pw_trans=new PrintWriter(fw_trans);
				FileWriter fw_trans1=new FileWriter(dirname+"Final_Transition.txt");
				PrintWriter pw_trans1=new PrintWriter(fw_trans1);
				FileWriter fw_trans2=new FileWriter(dirname+"Transitions2.txt");
				PrintWriter pw_trans2=new PrintWriter(fw_trans2);
				FileWriter fw_wmBel=new FileWriter(dirname+"WM_beliefs.txt");
				PrintWriter pw_wmBel=new PrintWriter(fw_wmBel);
				FileWriter fw_pomBel=new FileWriter(dirname+"POM_beliefs.txt");
				PrintWriter pw_pomBel=new PrintWriter(fw_pomBel);
				FileWriter fw_surp=new FileWriter(dirname+"WM_Surprises.txt");
				PrintWriter pw_surp=new PrintWriter(fw_surp);
				FileWriter fw_counts=new FileWriter(dirname+"Counts.txt");
				PrintWriter pw_counts=new PrintWriter(fw_counts);
				FileWriter fw_trans3=new FileWriter(dirname+"Final_Transition2.txt");
				PrintWriter pw_trans3=new PrintWriter(fw_trans3);
				FileWriter fw_bel=new FileWriter(dirname+"Final_Beliefs.txt");
				PrintWriter pw_bel=new PrintWriter(fw_bel);
				
				FileWriter fw_obs=new FileWriter(dirname+"Observations.txt");
				PrintWriter pw_obs=new PrintWriter(fw_obs);
				
				FileWriter fw_actinf=new FileWriter(dirname+"Action_info.txt");
				PrintWriter pw_actinf=new PrintWriter(fw_actinf);
	     
	     if(currentscenario_case==0)
	     {
	    	 //pomdp.transitionFunction=wm.getTransitionFunction();
        	 pomdp.transitionFunction=RDMTransitionProb.getTransitionFunction();
        	 
	     }
	     else
	     {
	    	 //pomdp.transitionFunction=wm.getTransitionFunction();
	    	 pomdp.transitionFunction=RDMTransitionProb.getTransitionFunctionCase(currentscenario_case);
	     }
	     
	     RDMSimConnector.p=pomdp;
	     int selectedAction = 0;
	     
	     int num_timesteps = RDMSimConnector.network_management.simulation_properties.getSimulationRuns();
	     int todeviate=2;
			System.out.println(todeviate);
	     if(todeviate==2)
			{
				//RDMTransitionProb.deviation_timesteps = (int)(Math.random() * (RDMConfigurationConnected.deviation_timesteps_max- RDMConfigurationConnected.deviation_timesteps_min + 1) + RDMConfigurationConnected.deviation_timesteps_min);
				
				RDMTransitionProb.random_int = (int)(Math.random() * (12- 9 + 1) + 9);
				//System.out.println("Random Number: "+RDMTransitionProb.random_int);
				//timestepcounter=0;
				/////For case 3
				RDMTransitionProb.random_int1 = (int)(Math.random() * (12- 9 + 1) + 9);
				RDMTransitionProb.random_int2 = (int)(Math.random() * (12- 9 + 1) + 9);


			}
			else
			{
				RDMTransitionProb.deviation_timesteps = 0;
				RDMTransitionProb.random_int = 0;
				//timestepcounter=0;

				RDMTransitionProb.random_int1 = 0;
				RDMTransitionProb.random_int2 = 0;
				System.out.println("no deviation for this timestep");


			}

	     
	         if(currentscenario_case==0)
		     {
		    	 //pomdp.transitionFunction=wm.getTransitionFunction();
	        	 pomdp.transitionFunction=RDMTransitionProb.getTransitionFunction();
	        	 
		     }
		     else
		     {
		    	 //pomdp.transitionFunction=wm.getTransitionFunction();
		    	 pomdp.transitionFunction=RDMTransitionProb.getTransitionFunctionCase(currentscenario_case);
		     }
		    
	         RDMSimConnector.p=pomdp;
		     
	    	 RDMSimConnector.monitorables=RDMSimConnector.network_management.getMonitorables();
	    	 int cs=pomdp.getInitialStateRDM();
	    	 pomdp.setCurrentState(cs);
	    
	     for(RDMSimConnector.timestep=0;RDMSimConnector.timestep<RDMSimConnector.network_management.simulation_properties.getSimulationRuns();RDMSimConnector.timestep++)
	     {
	     
	    	 
	    	 System.out.println("timestep: "+RDMSimConnector.timestep);
	    	 
	    	  //This is the current state
			 System.out.println("Initial state: "+cs);
			 
			 
			 System.out.println("current state: "+ pomdp.getCurrentState());
			 
			 
			 BeliefPoint initialbelief=pomdp.getInitialBelief(); //REPLACE

			 double b[]=initialbelief.getBelief();
			 //double b[]=wm.getBelief(cs, selectedAction);
			 
			 System.out.println("Initial Belief: "+b[0]+" "+b[1]+" "+b[2]+" "+b[3]+" "+b[4]+" "+b[5]+" "+b[6]+" "+b[7]);
			 double mcsatprob=b[0]+b[1]+b[2]+b[3];
			 double mrsatprob=b[0]+b[1]+b[4]+b[5];
			 double mpsatprob=b[0]+b[2]+b[4]+b[6];
			 
			 ////Results Log Regression////////
			 double bwc = ResultsLog.bandwidthconsumption;
			 double nal = ResultsLog.activelinks;
			 double rws = ResultsLog.timetowrite;
			 pw_mc_regr.println(bwc +","+mcsatprob+","+ResultsLog.satmc);
			 pw_mr_regr.println(nal+","+mrsatprob+","+ResultsLog.satmr);
			 pw_mp_regr.println(rws +","+mpsatprob+","+ResultsLog.satmp);
			 rt[0] = rt[0] + ResultsLog.satmc;
			 rt[1] = rt[1] + ResultsLog.satmp;
			 rt[2] = rt[2] + ResultsLog.satmr;
			 ////////////////////////////////
			 
			 obs_now = pomdp.getObservationFunction(selectedAction, cs);
			 ArrayList<AlphaVector> V1=solver.solve(pomdp);
			 //System.out.println("Value size: "+V1.size()+"  Action label: "+ V1.get(0).getAction());
			int[] val_label_count = {0,0};
			double[] largest_expected = {0, 0};
			for(int i=0;i<V1.size();i++)
			{
				//System.out.println("~~~~~~~~~~~~~~~~~~~~~~~");
				//System.out.println("Action label: "+ V1.get(i).getAction());
				//System.out.println("~~~~~~~~~~~~~~~~~~~~~~~");
				double expectedvalue=V1.get(i).getDotProduct(pomdp.getInitialBelief().getBelief());
				//double expectedvalue=V1.get(i).getDotProduct(wm.getBelief(cs, selectedAction));
				//System.out.println("Expected Value: "+ expectedvalue);
				val_label_count[V1.get(i).getAction()] = val_label_count[V1.get(i).getAction()] + 1;
				if(largest_expected[V1.get(i).getAction()] < expectedvalue) {
					largest_expected[V1.get(i).getAction()] = expectedvalue;
				}
				
			}
			pw_actinf.println("Timestep: " + RDMSimConnector.timestep + " Action count 0: " + val_label_count[0] + " Largest expected value: " + largest_expected[0] + " Action count 1: " + val_label_count[1] + " Largest expected value: " + largest_expected[1]);
	    	 
			
			
			int bestindex=AlphaVector.getBestVectorIndex(pomdp.getInitialBelief().getBelief(), V1);
			selectedAction=V1.get(bestindex).getAction();
			/*
			if(RDMSimConnector.timestep<50) {
				if(Math.random()<0.25) {
					selectedAction=1;
				}
				else {
					selectedAction=0;
				}
			}
			*/
			System.out.println("Selected Action: "+selectedAction);
			
			/*
			if(Math.round(largest_expected[0]) == Math.round(largest_expected[1])) {
				if(Math.random() < 0.75) {
					selectedAction=1;
				}
				else {
					selectedAction=0;
				}
			}
			*/
			
			if(selectedAction==0)
			{
				mst_cnt++;
			}
			else
			{
				rt_cnt++;
			}
			//pwaction.println(timestep+" "+selectedAction);
			//pwaction.flush();
			
			//obj.put("Selected Action", selectedAction+"");
			pomdp.setInitialBelief(initialbelief);
			RDMSimConnector.p=pomdp;
			
			///Check Perform Action
			con.performAction(selectedAction);
			pomdp=RDMSimConnector.p;
			//System.out.println("Current State: "+pomdp.getCurrentState());
			state_counts[cs] = state_counts[cs]+1;
			
			//double b[]=wm.getInitialBelief()[cs][selectedAction];
			
			/* 
			 * 
			 * ADDED Below
			 * 
			*/
			
			if(todeviate==2)
			{
				//RDMTransitionProb.deviation_timesteps = (int)(Math.random() * (RDMConfigurationConnected.deviation_timesteps_max- RDMConfigurationConnected.deviation_timesteps_min + 1) + RDMConfigurationConnected.deviation_timesteps_min);
				
				RDMTransitionProb.random_int = (int)(Math.random() * (12- 9 + 1) + 9);
				//System.out.println("Random Number: "+RDMTransitionProb.random_int);
				//timestepcounter=0;
				/////For case 3
				RDMTransitionProb.random_int1 = (int)(Math.random() * (12- 9 + 1) + 9);
				RDMTransitionProb.random_int2 = (int)(Math.random() * (12- 9 + 1) + 9);


			}
			else
			{
				RDMTransitionProb.deviation_timesteps = 0;
				RDMTransitionProb.random_int = 0;
				//timestepcounter=0;

				RDMTransitionProb.random_int1 = 0;
				RDMTransitionProb.random_int2 = 0;
				System.out.println("no deviation for this timestep");


			}

	     
	         if(currentscenario_case==0)
		     {
		    	 //pomdp.transitionFunction=wm.getTransitionFunction();
	        	 pomdp.transitionFunction=RDMTransitionProb.getTransitionFunction();
	        	 
		     }
		     else
		     {
		    	 //pomdp.transitionFunction=wm.getTransitionFunction();
		    	 pomdp.transitionFunction=RDMTransitionProb.getTransitionFunctionCase(currentscenario_case);
		     }
		    
	         RDMSimConnector.p=pomdp;
		     
	    	 RDMSimConnector.monitorables=RDMSimConnector.network_management.getMonitorables();
			
			double new_pc[] = RDMSimConnector.monitorables.getDevs();
			int csw = pomdp.getCurrentState();
			cs=pomdp.getInitialStateRDM();
	    	pomdp.setCurrentState(cs);
			
			wm.calculateSurprise(csw, selectedAction, cs);
	   	   	wm.computeAdaptationRate(new_pc);
	   	   	wm.updateBelief(csw, selectedAction, cs);
			wm.updateTransitionModel();
			
			//wm.updateBelief(cs, selectedAction, pomdp.getCurrentState());

			pw_state.println("Timestep: " + RDMSimConnector.timestep + ", Current State: " + csw + ", Next State: " + cs);
			pw_action.println("Timestep: " + RDMSimConnector.timestep + ", Selected Action: " + selectedAction);
			pw_obs.println("Timestep: " + RDMSimConnector.timestep + ", Selected Action: " + selectedAction + ", Observation probs: " + Arrays.toString(obs_now));
			pw_trans.println("Timestep: " + RDMSimConnector.timestep + ", Trans probs: " + Arrays.toString(wm.getTransitionFunction()[cs][selectedAction]));
			pw_trans2.println("Timestep: " + RDMSimConnector.timestep + ", Trans probs: " + Arrays.toString(RDMTransitionProb.getTransitionFunctionCase(currentscenario_case)[cs][selectedAction]));
			pw_wmBel.println("Timestep: " + RDMSimConnector.timestep + ", Current state: " + csw);
			pw_wmBel.println(Arrays.toString(wm.getBelief(cs,0)));
			pw_wmBel.println(Arrays.toString(wm.getBelief(cs,1)));
			pw_pomBel.println("Timestep: " + RDMSimConnector.timestep + " State: " + csw + " Beliefs: " + Arrays.toString(b));
			pw_surp.println("Pc: " + Arrays.toString(wm.getPc()) + ", Surprise: " + Arrays.toString(wm.getSurprise()) + ", Adaptaton rate: " + Arrays.toString(wm.getAR()) + ", Value size: "+V1.size());

			
			
			System.out.println("\nTopology Count:: MST: "+mst_cnt+" RT: "+rt_cnt);
			System.out.println(Arrays.toString(rt));
	    	 
	     }  //End of for loop
	     pw_counts.println("Mst Selection rate: " + mst_cnt + ", RT Selection rate: " + rt_cnt);
	     pw_counts.println("MC Sat Rate: " + rt[0] + ", MP Sat Rate: " + rt[1] + ", MR Sat Rate: " + rt[2]);
	     pw_counts.println("MC Sat Rate: " + rt[0]/(num_timesteps/100) + ", MP Sat Rate: " + rt[1]/(num_timesteps/100) + ", MR Sat Rate: " + rt[2]/(num_timesteps/100));
	     pw_counts.println((rt[0] + rt[1]+ rt[2])/(3*num_timesteps/100));
	     pw_counts.println("State occurence (from 1 to 8 respectively): " + Arrays.toString(state_counts));
	     for(int i = 0; i < state_counts.length; i++) {
	    	 state_counts[i] = state_counts[i]/(state_counts.length);
	     }
	     pw_counts.println("State percentage (from 1 to 8 respectively): " + Arrays.toString(state_counts));
	     
	     for(int a = 0; a<2; a++) {
	    	 pw_trans1.println("Action: " + a);
	    	 pw_trans3.println("Action: " + a);
	    	 pw_bel.println("Action: " + a);
	    	 for(int cstate = 0; cstate<8; cstate++) {
	    		 pw_trans1.println(Arrays.toString(wm.getTransitionFunction()[cstate][a]));
	    		 pw_trans3.println(Arrays.toString(RDMTransitionProb.getTransitionFunctionCase(currentscenario_case)[cstate][a]));

	    	 }
	    	 for (int cnfr = 0; cnfr < 6; cnfr++) {
	    		 pw_bel.println(Arrays.toString(wm.getBelief(cnfr, a)));
	    	 }
	    	 pw_trans1.println("");
	    	 pw_trans3.println("");
	    	 pw_bel.println("");
	     }
			
	     pw_mc_regr.flush();
	     pw_mp_regr.flush();
	     pw_mr_regr.flush();
	     pw_mc_regr.close();
	     pw_mr_regr.close();
	     pw_mp_regr.close();
	     
	     pw_state.flush();
	     pw_action.flush();
	     pw_trans.flush();
	     pw_trans1.flush();
	     pw_trans2.flush();
	     pw_wmBel.flush();
	     pw_pomBel.flush();
	     pw_surp.flush();
	     pw_counts.flush();
	     pw_actinf.flush();
	     pw_trans3.flush();
	     pw_bel.flush();
	     pw_obs.flush();
	     
		}
		catch(IOException ioex)
		{
			ioex.printStackTrace();
		}
	     
		
	}
	
	
	
	
	
	/**
	 * Method to run experiments for DeltaIoT case 
	 * @param pomdpFileName
	 */
	public void runCaseIoT(String pomdpFileName)
	{
		///Results Log
		try
		{
		FileWriter fwMECSatProb=new FileWriter("MECSatProb.txt");
		PrintWriter pwMECSatProb=new PrintWriter(fwMECSatProb);
		FileWriter fwRPLSatProb=new FileWriter("RPLSatProb.txt");
		PrintWriter pwRPLSatProb=new PrintWriter(fwRPLSatProb);
		
		FileWriter fwMECSat=new FileWriter("MECSat.txt");
		PrintWriter pwMECSat=new PrintWriter(fwMECSat);
		FileWriter fwRPLSat=new FileWriter("RPLSat.txt");
		PrintWriter pwRPLSat=new PrintWriter(fwRPLSat);
		FileWriter fwaction=new FileWriter("SelectedAction.txt");
		PrintWriter pwaction=new PrintWriter(fwaction);
		
		FileWriter fwMECSattimestep=new FileWriter("MECSattimestep.txt");
		PrintWriter pwMECSattimestep=new PrintWriter(fwMECSattimestep);
		FileWriter fwRPLSattimestep=new FileWriter("RPLSattimestep.txt");
		PrintWriter pwRPLSattimestep=new PrintWriter(fwRPLSattimestep);
		
		JsonArray rlist = new JsonArray();
		
		
		// read POMDP file
		POMDP pomdp = Parser.readPOMDP(domainDir+"/"+pomdpFileName);
		//iot.DeltaIOTConnector.p=pomdp;
		
	
		
		////////IoT Code///////////
		
		//iot.DeltaIOTConnector.timestepiot=timestep;
		//System.out.println("timestep: "+timestep);
		//iot.DeltaIOTConnector.networkMgmt = new SimulationClient();
		
		//iot.DeltaIOTConnector dc=new iot.DeltaIOTConnector();
		//iot.DeltaIOTConnector.timestepiot=0;
		/*
		for(int timestep=0;timestep<100;timestep++)
		{
			JsonObject obj =new JsonObject();
			obj.put("timestep", timestep+"");
			//iot.DeltaIOTConnector.motes = iot.DeltaIOTConnector.networkMgmt.getProbe().getAllMotes();
		
			System.out.println("motes recieved");
		
			//int cs=pomdp.getInitialState();
		
			//pomdp.setCurrentState(cs);
			//System.out.println("current state"+ pomdp.getCurrentState());
			int cs=pomdp.getInitialState();
			System.out.println("Initial state: "+cs);
			pomdp.setCurrentState(cs);
			
			System.out.println("current state: "+ pomdp.getCurrentState());
			
		
	
		for(Mote m:iot.DeltaIOTConnector.motes)
		{
			
			System.out.println("\nTime Step: "+timestep);
			iot.DeltaIOTConnector.networkMgmt.simulator.doSingleRun();
			
			iot.DeltaIOTConnector.selectedmote=m;
			System.out.println("Mote Id"+iot.DeltaIOTConnector.selectedmote.getMoteid());
			//System.out.println("Mote Load"+iot.DeltaIOTConnector.selectedmote.getBattery());
			
			obj.put("Mote Id", iot.DeltaIOTConnector.selectedmote.getMoteid()+"");
		
			//int cs=pomdp.getInitialState();
			//System.out.println("Initial state: "+cs);
			//pomdp.setCurrentState(cs);
		
			//System.out.println("current state: "+ pomdp.getCurrentState());
	
		
			BeliefPoint initialbelief=pomdp.getInitialBelief();
			double b[]=initialbelief.getBelief();
			System.out.println(b[0]+" "+b[1]+" "+b[2]+" "+b[3]);
			double mecsatprob=b[0]+b[1];
			double rplsatprob=b[0]+b[2];
			pwMECSatProb.println(timestep+" "+mecsatprob);
			pwRPLSatProb.println(timestep+" "+rplsatprob);
			pwMECSatProb.flush();
			pwRPLSatProb.flush();
		
			ArrayList<AlphaVector> V1=solver.solve(pomdp);
			System.out.println("Value size: "+V1.size()+"  Action labels: "+ V1.get(0).getAction());
		
			for(int i=0;i<V1.size();i++)
			{
				System.out.println("~~~~~~~~~~~~~~~~~~~~~~~");
				System.out.println("Action labels: "+ V1.get(i).getAction());
				System.out.println("~~~~~~~~~~~~~~~~~~~~~~~");
				double expectedvalue=V1.get(i).getDotProduct(pomdp.getInitialBelief().getBelief());
				System.out.println("Expected Value: "+ expectedvalue);
			
			}
			int bestindex=AlphaVector.getBestVectorIndex(pomdp.getInitialBelief().getBelief(), V1);
			int selectedAction=V1.get(bestindex).getAction();
			System.out.println("Selected Action: "+selectedAction);
			
			
			pwaction.println(timestep+" "+selectedAction);
			pwaction.flush();
			
			obj.put("Selected Action", selectedAction+"");
			pomdp.setInitialBelief(initialbelief);
			iot.DeltaIOTConnector.p=pomdp;
			dc.performAction(selectedAction);
			pomdp=iot.DeltaIOTConnector.p;
		 
			System.out.println("Current State: "+pomdp.getCurrentState());
		 	ArrayList<QoS> result = DeltaIOTConnector.networkMgmt.getNetworkQoS(iot.DeltaIOTConnector.timestepiot+1);
		 //System.out.println("QOS list size: "+result.size());
		//ArrayList<QoS> result=(ArrayList<QoS>)DeltaIOTConnector.networkMgmt.simulator.getQosValues();
		 	System.out.println("QOS list size: "+result.size());
			
		 	double pl=result.get(result.size()-1).getPacketLoss();
		 	double ec=result.get(result.size()-1).getEnergyConsumption();
		 	System.out.println("packet loss: "+pl+"   Energy Consumption: "+ec);
		 	
		 	pwMECSat.println(timestep+" "+ec);
		 	pwRPLSat.println(timestep+" "+pl);
		 	pwMECSat.flush();
		 	pwRPLSat.flush();
		 	
		 	obj.put("packet loss", pl+"");
		 	obj.put("Energy Consumption",ec+"");
		 	iot.DeltaIOTConnector.timestepiot++;
		 	
		 	
		 //	ArrayList<QoS> result1 = (ArrayList<QoS>)DeltaIOTConnector.networkMgmt.simulator.getQosValues();
			
		 	rlist.add(obj);
		 	
		}///End of Motes loop
		
		
		String plstimestep="";
		String ecstimestep="";
		ArrayList<QoS> result1 = (ArrayList<QoS>)DeltaIOTConnector.networkMgmt.simulator.getQosValues();
		
		double pl1=result1.get(result1.size()-1).getPacketLoss();
		double ec1=result1.get(result1.size()-1).getEnergyConsumption();
		plstimestep=timestep+" ";
		ecstimestep=timestep+" ";
		plstimestep=plstimestep+pl1;
		ecstimestep=ecstimestep+ec1;
		
		System.out.println("packet loss: "+plstimestep+"energy consumption"+ecstimestep);
		pwMECSattimestep.println(ecstimestep);
		pwRPLSattimestep.println(plstimestep);
		pwMECSattimestep.flush();
		pwRPLSattimestep.flush();
		
		
		
		}
	
		////////////////////////////////
		
		pwMECSat.close();
		pwRPLSat.close();
		pwaction.close();
		fwaction.close();
		fwMECSat.close();
		fwRPLSat.close();	
		pwMECSattimestep.close();
		pwRPLSattimestep.close();
		pwMECSatProb.close();
		pwRPLSatProb.close();
		fwMECSatProb.close();
		fwRPLSatProb.close();
		*/
		
		// print results
		/*
		String outputFilePG = sp.getOutputDir()+"/"+pomdp.getInstanceName()+".pg";
		String outputFileAlpha = sp.getOutputDir()+"/"+pomdp.getInstanceName()+".alpha";
		System.out.println();
		System.out.println("=== RESULTS ===");
		System.out.println("Expected value: "+solver.getExpectedValue());
		System.out.println("Alpha vectors: "+outputFileAlpha);
		if(sp.dumpPolicyGraph()) System.out.println("Policy graph: "+outputFilePG);
		System.out.println("Running time: "+solver.getTotalSolveTime()+" sec");
		*/
		}
		catch(IOException ioex)
		{
			ioex.printStackTrace();
		}
	}
	
	/**
	 * Main entry point of the SolvePOMDP software
	 * @param args first argument should be a filename of a .POMDP file
	 */
	public static void main(String[] args) {		
		System.out.println("SolvePOMDP v0.0.3");
		System.out.println("Author: Erwin Walraven");
		System.out.println("Web: erwinwalraven.nl/solvepomdp");
		System.out.println("Delft University of Technology");
		
		if(args.length == 0) {
			System.out.println();
			System.out.println("First argument must be the name of a file in the domains directory!");
			//System.exit(0);
		}
		
		//int[] scen = {4,1,2};
		//int[] scen = {0,3,5,6};
		int[] scen = {0, 1, 2, 3, 4, 5, 6};
		//int[] scen = {4};
		//double pc_totest[] = {0.5, 0.25,0, 0.75, 0.9};
		//double pc_totest[] = { 0.1, 0.5, 0.9, 0.3, 0.7};
		//double pc_totest[] = {0, 0.2, 0.4, 0.6, 0.8};
		double pc_totest[] = {0.0};
		long stime = System.currentTimeMillis();
			for(int u = 0; u<pc_totest.length; u++){
				for(int j=0;j<scen.length;j++) {
					ConfigEdit sc = new ConfigEdit();
		
					try {
						sc.updateScenario("config_log_files/configuration.json", scen[j]);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						System.exit(1);
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						System.exit(1);
					}
					
					
					for(int i=0; i<10; i++) {
						SolvePOMDP ps = new SolvePOMDP(pc_totest[u]);
						//ps.run(args[0]);
						ps.run("RDM.POMDP");
						ps.close();
				}
			}
		}
		long ftime = System.currentTimeMillis();
		long fintime = ftime-stime;
		System.out.print("Total time in millis = " + fintime);
		
	}
}
