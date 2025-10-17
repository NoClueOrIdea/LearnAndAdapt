# LearnAndAdapt
Find in this repository the code and relevant folders to run the RDMSim with the surprise based learner
## Structure

Most files and folders are related directly to the running of the RDMSim and learner
Also find two python scripts, Graph.py and ReadStats.py which are used to produce graphs and relevant metrics respectively, output folders being in the Graphs and Results folder
The Results folder is where the main outputs of the RDMSim are stored, the structure of this folder breaks down into Overall runs (usually a specific name to indicate the parameters), then scenarios, then runs, with specific viles in the run folders.
- The inner folders of the Results indicate slightly different implementations of the learner, "Pc X" folders indicate differences in how PC was calculated with the "X" being a stand in for the value we divided the flucations by, each of these used confidence corrected surprise. The folders named after types of surprise were implementations using those surprises, with Pc simply set to "13" for each of these runs.
If the ReadStats script had been run the output would be found with scenarios portion of the folder, titled "stats.txt" and summarizing several metrics such as mean/median and standard deviation/interquartile range of values in each run.
Within the Run folders several output files exist, a broad understanding of the structure is as follows:
- Files with "Final" in the tital will be summaries of values throughout the entire run "Final_Transition" and "Final_Transition2" give the transition functions for the learner and expert at the end of the run respectively
- Files with "WM" contain values related to the learner and world model directly "WM_beliefs" stands in contrast to "POM_beliefs" as the former gives the beliefs of our learner and the later the beleif of the POMDP manager
- "Regression" Files contain summaries of values related to specific quality attribute (QAs), "MCRegressionResultsSolvePOMDP" contains (in order) the value of the monitorable variable, the probability it will be satisfied (as decided by the POMDP rather than learner), and if the QA is currently satisfied or not.
- Other important files include "Counts", "Action_info", and "Current States" which contains summaries of an entire run, information related to action and expected utility, and information related to selected states respectively.

The graph folder can be broken down in a similar way to how the results, with titles explaining parameters, followed by "graph type", then scenarios, with graphs of specific runs contained within. Some graphs are also done as an average of each run to get an idea in summary (these may be sensitive to outliers though).
The Graph types are labeled numerically, use the following list as key:
- Graph 0 corresponds to graphs of topology, a simple bar chart showing MST and RT selection
- Graph 1 corresponds to graphs of satisfaction, a bar chart showing the percentage satisfaction of each QA
- Graph 2 shows a confidence interval graph with the monitorables of each quality attribue displayed alongside a red bar indicating the cut off point for satisfaction (MC and MP should be above and MR below this bar). This is done using the average of each run for a scenario
- Graph 3 contains the surprises for each QA and is displayed as a line graph
- Graph 4 contains the expected utility of each action and is also a line graph
