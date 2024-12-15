import os, glob
from copy import deepcopy
import numpy as np

NFR = ['MC', 'MP', 'MR']

class Count_Write:
    def __init__(self, Path, res_dir):
        Paths = []
        if res_dir == 'all':
            res_dir = os.listdir(Path)
        res_dir = np.array(res_dir)
        for i in res_dir:
            Paths.append(os.path.join(Path, i))
            
        self.paths = Paths


    def initialize_counts(self):
        #MST then RT
        self.topology = [[], []]
        #MC then MP then MR
        self.sats = [[],[],[]]
        

    def read_file(self, file):
        with open(file) as f:
            arr = []
            i = 0
            n = 0
            for j, line in enumerate(f):
                if j == 0:
                    temp = line.strip().replace(',', '').split(' ')
                    n = (float(temp[3]) + float(temp[7]))/100
                if i == 0:
                    line = line.strip().replace(',', '').split(' ')
                    self.topology[0].append(float(line[3])/n)
                    self.topology[1].append(float(line[7])/n)
                if i == 1:
                    line = line.strip().replace(',', '').split(' ')
                    self.sats[0].append(float(line[3])/n)
                    self.sats[1].append(float(line[7])/n)
                    self.sats[2].append(float(line[11])/n)
                i+=1       


    def set_counts(self):

        for path in self.paths:
            for s in os.listdir(path):
                self.initialize_counts()
                path2 = os.path.join(path, s)
                for n in os.listdir(path2): 
                    sats = []
                    if (n[-4:] != ".txt"):
                        path3 = os.path.join(path2, n)
                        x = os.path.join(path3 , "Counts.txt")
                        self.read_file(x)
                    
                self.write_data(path2)



    def write_data(self, path):
        topology = np.array(self.topology)
        sats = np.array(self.sats)
        Check=False
        if Check:
            top_mean = np.percentile(topology, 50, axis=1)
            sat_mean = np.percentile(sats, 50, axis=1)
        else:
            top_mean = np.mean(topology, axis=1)
            sat_mean = np.mean(sats, axis=1)

        #Range
        top_mins = np.min(topology, axis=1)
        top_maxs = np.max(topology, axis=1)
        top_range = top_maxs - top_mins

        top_range = np.round(top_range, 2)
        
        sat_mins = np.min(sats, axis=1)
        sat_maxs = np.max(sats, axis=1)
        sat_range = sat_maxs - sat_mins

        sat_range = np.round(sat_range, 2)

        #IQR
        sq75, sq25 = np.percentile(sats, [75 ,25], axis=1)
        siqr = sq75 - sq25

        siqr = np.round(siqr, 2)

        tq75, tq25 = np.percentile(topology, [75 ,25], axis=1)
        tiqr = tq75 - tq25

        tiqr = np.round(tiqr, 2)

        #SD
        sd_tops = (topology.T - top_mean).T
        sd_tops = np.power(sd_tops, 2)
        sd_tops = np.sum(sd_tops, axis=1)
        sd_tops = np.divide(sd_tops, len(sats))
        sd_tops = np.power(sd_tops, 0.5)
        sd_tops = np.round(sd_tops,2)
        
        sd_sats = (sats.T - sat_mean).T
        sd_sats = np.power(sd_sats, 2)
        sd_sats = np.sum(sd_sats, axis=1)
        sd_sats = np.divide(sd_sats, len(sats))
        sd_sats = np.power(sd_sats, 0.5)
        sd_sats = np.round(sd_sats,2)

        #Median
        top_med = np.percentile(topology, 50, axis=1)
        sat_med = np.percentile(sats, 50, axis=1)

        #Means (rounded)
        top_mean = np.round(top_mean,2)
        sat_mean = np.round(sat_mean,2)

        st = np.round(np.mean(sat_mean),2)
        
        
        with open(os.path.join(path, 'Stats.txt'), 'w') as f:
            f.write('Topology stats\n')
            f.write('[Mst, RT]\n')
            arrtxt = ', '.join(str(x) for x in top_range)
            f.write('Range: ' + arrtxt + '\n')
            arrtxt = ', '.join(str(x) for x in tiqr)
            f.write('IQR: ' + arrtxt + '\n')
            arrtxt = ', '.join(str(x) for x in sd_tops)
            f.write('Standard Deviation: ' + arrtxt + '\n')
            arrtxt = ', '.join(str(x) for x in top_med)
            f.write('Medians: ' + arrtxt + '\n')
            arrtxt = ', '.join(str(x) for x in top_mean)
            f.write('Means: ' + arrtxt + '\n')
            

            f.write('\n')

            f.write('Satisfaction rates\n')
            f.write('[MC, MP, MR]\n')
            arrtxt = ', '.join(str(x) for x in sat_range)
            f.write('Range: ' + arrtxt + '\n')
            arrtxt = ', '.join(str(x) for x in siqr)
            f.write('IQR: ' + arrtxt + '\n')
            arrtxt = ', '.join(str(x) for x in sd_sats)
            f.write('Standard Deviation: ' + arrtxt + '\n')
            arrtxt = ', '.join(str(x) for x in sat_med)
            f.write('Medians: ' + arrtxt + '\n')
            arrtxt = ', '.join(str(x) for x in sat_mean)
            f.write('Means: ' + arrtxt + '\n')
            f.write('Mean of means: ' + str(st) + '\n')
        

c = Count_Write('Results/', 'all')
c.set_counts()

        
