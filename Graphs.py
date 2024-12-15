import os, glob
from copy import deepcopy
import numpy as np
import matplotlib.pyplot as plt
import scipy.stats as st
import string

NFR = ['MC', 'MP', 'MR']

graph_file = ''
def read_file(path, delim = ','):
    with open(path) as f:
        if delim is None:
            return [line.strip().split() for line in f]
        else:
            return [line.strip().split(delim) for line in f]

def remove_strings(inp):
    
    return inp.replace("[", "").replace("]", "").replace(",", "")

def load_data(folder):
    path = folder + '/'
    top_selection = []
    sats = []
    sats2 = []
    monitorables = []
    expected_value = []
    surps=[]
    for s in os.listdir(path):
        path2 = path
        path2 += s + '/'
        top_selection.append([])
        monitorables.append([])
        sats.append([])
        sats2.append([])
        expected_value.append([])
        surps.append([])
        for n in os.listdir(path2):
            if (n[-4:] != ".txt"):
                sat = []
                mon = []
                x = os.path.join(path2 + n, "Counts.txt")
                file = read_file(x, ' ')
                for i in range(len(file)):
                    for j in range(len(file[i])):
                        file[i][j] = file[i][j].strip(',')
                top_selection[-1].append([file[0][3], file[0][7]])

                y = [os.path.join(path2 + n, i + 'RegressionResultsSolvePOMDP.txt') for i in NFR]
                count = 0
                for r in y:
                    temp = np.array(read_file(r))
                    temp2 = temp.T
                    sat.append(temp2[2])
                    mon.append(temp2[0])
                    count += 1
                sats2[-1].append(sat)
                sat = np.array(sat)
                mon = np.array(mon)
                sat = sat.astype(float)
                mon = mon.astype(float)
                sat = 100*(np.sum(sat, axis= 1)/np.shape(sat)[1])
                sats[-1].append(sat)
                monitorables[-1].append(mon)
                
                z = os.path.join(path2 + n, "Action_info.txt")
                file = read_file(z, ' ')
                expected_value[-1].append([[], []])
                for i in range(len(file)):
                    expected_value[-1][-1][0].append(file[i][9])
                    expected_value[-1][-1][1].append(file[i][17])

                z = os.path.join(path2 + n, "WM_Surprises.txt")
                file = read_file(z, ' ')
                surps[-1].append([[],[], []])
                for i in file:
                    i[5] = remove_strings(i[5])
                    i[6] = remove_strings(i[6])
                    i[7] = remove_strings(i[7])
                    surps[-1][-1][0].append(i[5])
                    surps[-1][-1][1].append(i[6])
                    surps[-1][-1][2].append(i[7])
                    
    
    top_selection = np.array(top_selection)
    top_selection = top_selection.astype(float)
    x = np.mean(top_selection, axis=1, keepdims=True)
    top_selection = np.concatenate((top_selection, x), axis=1)

    sats = np.array(sats)
    sats = sats.astype(float)
    sats2 = np.array(sats2)
    sats2 = sats2.astype(float)
    y = np.mean(sats, axis=2, keepdims=True)
    sats = np.concatenate((sats, y), axis=2)
    x = np.mean(sats, axis=1, keepdims=True)
    sats = np.concatenate((sats, x), axis=1)
    sats = np.round(sats, 2)

    monitorables = np.array(monitorables)
    monitorables = monitorables.astype(float)
    x = np.mean(monitorables, axis=1, keepdims=True)
    monitorables = np.concatenate((monitorables, x), axis=1)

    expected_value = np.array(expected_value)
    expected_value = expected_value.astype(float)

    surps = np.array(surps)
    surps = surps.astype(float)

    create_dirs(folder, data = top_selection, num_graphs=5)
    record_topology(top_selection)
    record_sats(sats)
    #record_sats2(sats2[:, :, :, 500:])
    Confidence_intervals(monitorables)
    record_surprise(surps)
    record_actions(expected_value)
    #record_actions2(expected_value)
    

def create_dirs(res_name='', data=[], num_graphs=2):
    idx = ress.index(res_name)
    nname = Graph_loc[idx]
    name = nname + ' (Graphs)'
    global graph_file
    graph_file = name
    path = './' + name
    if not os.path.exists(path):
        os.mkdir(path)
    for i in range(num_graphs):
        path2 = os.path.join(path, 'Graph ' + str(i))
        if not os.path.exists(path2):
            os.mkdir(path2)
        for j in range(0, len(data)):
            run = 'Scenario ' + str(j)
            path3 = os.path.join(path2, run)
            if not os.path.exists(path3):
                os.mkdir(path3)

def Confidence_intervals(data):
    scns = ['Scenario ' + str(i) for i in range(1, len(data)+1)]
    NFR = ['MC', 'MP', 'MR', 'avg']
    Monit = ['Bandwidth consumption', 'Read/Write time', 'Number of active links']
    lines = [3600, 2700, 105]
    path = './' +graph_file + '/Graph 2/'

    TEMP = []
    
    colours = [(100,0,0), (0,100,0), (0,0,100), (0, 0, 0)]
    colours=np.array(colours)/200

    from matplotlib.lines import Line2D
    custom_lines = [Line2D([0], [0], color=i, lw=4) for i in colours]
    for i, s in enumerate(data):
        TEMP.append([])
        for j, r in enumerate(s):
            temp = []
            for e, nfr in enumerate(r):
                x = st.t.interval(confidence=0.95, df=len(nfr)-1, loc=np.mean(nfr), scale=st.sem(nfr))
                x = list(x)
                temp.append(x)
                temp[-1].append(np.mean(nfr))
            TEMP[-1].append(temp)
            
    TEMP = np.array(TEMP)
    TEMP = np.transpose(TEMP, (0, 2, 1, 3))
    cmap = get_cmap(TEMP.shape[2])
    #pyplot.get_cmap(TEMP.shape[2])
    temp = os.listdir(path)
    
    count = 0
    while True:
        if os.path.isfile(os.path.join(path, temp[count])):
            del temp[count]
        else:
            count += 1
        if count >= len(temp):
            break
            
    for s_name, s in zip(temp, TEMP):
        path2 = os.path.join(path, s_name)
        print(path2)
        for i, nfr in enumerate(s):
            for j, run in enumerate(nfr):
                bottom = j - 0.1
                top = j + 0.1
                plt.plot([run[0], run[1]], [j, j], color=cmap(j))
                plt.plot([run[1], run[1]], [bottom, top], color=cmap(j))
                plt.plot([run[0], run[0]], [bottom, top], color=cmap(j))
                plt.plot(run[2], j, 'o', color='#f44336')
            plt.axvline(x = lines[i], color = 'r')
            npath = os.path.join(path2, NFR[i] + '.png')
            y_names = ['Run ' + str(u + 1) for u in range(TEMP.shape[2] - 1)]
            y_names.append('Run avg')
            plt.yticks(range(TEMP.shape[2]), y_names)
            plt.ylabel('Runs')
            plt.xlabel(Monit[i])
            plt.title(NFR[i] + ': Monitorable confidence')
            plt.savefig(npath, dpi=400)
            plt.clf()
            plt.close()
    TEMP = np.transpose(TEMP, (1, 0, 2, 3))
    cmap = get_cmap(TEMP.shape[1])
    for i, nfr in enumerate(TEMP):
        for j, s in enumerate(nfr):
            run = s[-1]
            bottom = j - 0.1
            top = j + 0.1
            plt.plot([run[0], run[1]], [j, j], color=cmap(j))
            plt.plot([run[1], run[1]], [bottom, top], color=cmap(j))
            plt.plot([run[0], run[0]], [bottom, top], color=cmap(j))
            plt.plot(run[2], j, 'o', color='#f44336')
        plt.axvline(x = lines[i], color = 'r')
        npath = os.path.join(path, NFR[i] + '.png')
        y_names = ['Scenario ' + str(u) for u in range(TEMP.shape[1])]
        plt.yticks(range(TEMP.shape[1]), y_names)
        plt.ylabel('Scenarios')
        plt.xlabel(Monit[i])
        plt.title(NFR[i] + ': Monitorable confidence')
        plt.savefig(npath, dpi=400)
        plt.clf()
        plt.close()

def record_sats(data):
    path = './' +graph_file + '/Graph 1/'
    NFR = ['MC', 'MP', 'MR', 'avg']
    for s_name, s in zip(os.listdir(path), data):
        path2 = os.path.join(path, s_name)
        print(path2)
        for i, run in enumerate(s):
            x_labs = [NFR[i] + ' ' + str(run[i]) for i in range(len(run))]
            plt.bar(x_labs, run)
            title = str(i + 1)
            npath = os.path.join(path2, 'Run ' + str(i+1) + '.png')
            if i == len(s) - 1:
                title = 'avg'
                npath = os.path.join(path2, 'Run avg.png')
            plt.title(s_name + ' Satisfaction, ' + 'Run ' + title)
            plt.xlabel('NFR')
            plt.ylabel('Satisfaction ammount (%)')
            plt.savefig(npath, dpi=400)
            plt.clf()
            plt.close()

def record_sats2(data):
    path = './' +graph_file + '/Graph 1/'
    narr = []
    
    for s_name, s in zip(os.listdir(path), data):
        path2 = os.path.join(path, s_name)
        print(path2)
        for i, run in enumerate(s):
            ts = np.linspace(1,len(run[0]),len(run[0]))
            fig = plt.figure()
            fig.add_subplot(111)
            plt.plot(ts, run[0], label="MC")
            plt.plot(ts, run[1], label="MP")
            plt.plot(ts, run[2], label="MR")
            title = str(i + 1)
            npath = os.path.join(path2, 'Run ' + str(i+1) + '.png')
            plt.title(s_name + ' Satisfaction, ' + 'Run ' + title)
            plt.legend(loc='upper right')
            plt.xlabel('Timestep')
            plt.ylabel('Satisfaction')
            fig.set_size_inches(30, 10)
            plt.savefig(npath, dpi=400)
            plt.clf()
            plt.close()

def record_sats3(data):
    path = './' +graph_file + '/Graph 1/'
    narr = []
    
    for s_name, s in zip(os.listdir(path), data):
        path2 = os.path.join(path, s_name)
        print(path2)
        for i, run in enumerate(s):
            ts = np.linspace(1,len(run[0]),len(run[0]))
            figure, axis = plt.subplots(1, 3)
            
            axis[0].plot(ts, run[0], label="MC")
            title = str(i + 1)
            axis[0].set_title(s_name + ' MC, ' + 'Run ' + title)
            axis[0].set_xlabel('Timestep')
            axis[0].set_ylabel('Satisfaction')

            axis[1].plot(ts, run[1], label="MP")
            title = str(i + 1)
            axis[1].set_title(s_name + ' NP, ' + 'Run ' + title)
            axis[1].set_xlabel('Timestep')
            axis[1].set_ylabel('Satisfaction')

            axis[2].plot(ts, run[2], label="MR")
            title = str(i + 1)
            axis[2].set_title(s_name + ' MR, ' + 'Run ' + title)
            axis[2].set_xlabel('Timestep')
            axis[2].set_ylabel('Satisfaction')

            npath = os.path.join(path2, 'Run ' + str(i+1) + '.png')
            figure.set_size_inches(50, 5)
            plt.savefig(npath, dpi=100)
            plt.clf()
            plt.close()
        

def record_topology(data):
    path = './' +graph_file + '/Graph 0/'
    tops = ['MST', 'RT']
    tops2 = ['m', 'r']
    narr = []
    for s_name, s in zip(os.listdir(path), data):
        path2 = os.path.join(path, s_name)
        print(path2)
        narr.append(s[-1])
        for i, run in enumerate(s):
            x_labs = [tops[0] + ' ' + str(run[0]), tops[1] + ' ' + str(run[1])]
            plt.bar(x_labs, run)
            title = str(i + 1)
            npath = os.path.join(path2, 'Run ' + str(i+1) + '.png')
            if i == len(s) - 1:
                title = 'avg'
                npath = os.path.join(path2, 'Run avg.png')
            plt.title(s_name + ' Topology Selection, ' + 'Run ' + title)
            plt.xlabel('Topology')
            plt.ylabel('Occurence rate')
            plt.savefig(npath, dpi=400)
            plt.clf()
            plt.close()

    narr = np.array(narr)
    narr = narr.flatten()

    x_labs = [tops2[i%2] + str(i//2) for i in range(len(narr))]
    HOLD = plt.bar(x_labs, narr)
    for i in range(0, len(narr), 2):
        HOLD[i].set_color("r")
    title = "Scenarios"
    npath = os.path.join(path, 'Scenarios.png')

    plt.title('Topology Selection, ' + title)
    plt.xlabel('Topology')
    plt.ylabel('Occurence rate')
    plt.savefig(npath, dpi=400)
    plt.clf()
    plt.close()

def record_surprise(data):
    path = './' +graph_file + '/Graph 3/'
    narr = []
    
    for s_name, s in zip(os.listdir(path), data):
        path2 = os.path.join(path, s_name)
        print(path2)
        for i, run in enumerate(s):
            ts = np.linspace(1,len(run[0]),len(run[0]))
            plt.plot(ts, run[0], label="MC")
            plt.plot(ts, run[1], label="MP")
            plt.plot(ts, run[2], label="MR")
            title = str(i + 1)
            npath = os.path.join(path2, 'Run ' + str(i+1) + '.png')
            plt.title(s_name + ' Surprise amount, ' + 'Run ' + title)
            plt.xlabel('Timestep')
            plt.ylabel('Surprise')
            plt.legend(loc="best")
            plt.savefig(npath, dpi=400)
            plt.clf()
            plt.close()

def record_actions(data):
    path = './' +graph_file + '/Graph 4/'
    narr = []
    
    for s_name, s in zip(os.listdir(path), data):
        path2 = os.path.join(path, s_name)
        print(path2)
        for i, run in enumerate(s):
            ts = np.linspace(2,len(run[0]),len(run[0])-1)
            fig = plt.figure()
            fig.add_subplot(111)
            plt.plot(ts, run[0, 1:], label="MST")
            plt.plot(ts, run[1, 1:], label="RT")
            title = str(i + 1)
            npath = os.path.join(path2, 'Run ' + str(i+1) + '.png')
            plt.title(s_name + ' Expected Value, ' + 'Run ' + title)
            plt.legend(loc='upper right')
            plt.xlabel('Timestep')
            plt.ylabel('Expected Value')
            plt.ylim(250, 400)
            #fig.set_size_inches(50, 10)
            plt.savefig(npath, dpi=400)
            plt.clf()
            plt.close()

def record_actions2(data):
    path = './' +graph_file + '/Graph 4/'
    narr = []
    
    for s_name, s in zip(os.listdir(path), data):
        path2 = os.path.join(path, s_name)
        print(path2)
        for i, run in enumerate(s):
            ts = np.linspace(2,len(run[0]),len(run[0])-1)
            figure, axis = plt.subplots(1, 2)
            axis[0].plot(ts, run[0, 1:], label="MST")
            title = str(i + 1)
            axis[0].set_title(s_name + ' MST, ' + 'Run ' + title)
            axis[0].set_xlabel('Timestep')
            axis[0].set_ylabel('Expected Value')

            axis[1].plot(ts, run[1, 1:], label="RT")
            title = str(i + 1)
            npath = os.path.join(path2, 'Expected Value ' + str(i+1) + '.png')
            axis[1].set_title(s_name + ' RT, ' + 'Run ' + title)
            axis[1].set_xlabel('Timestep')
            axis[1].set_ylabel('Expected Value')
            figure.set_size_inches(50, 10)
            plt.savefig(npath, dpi=100)
            plt.clf()
            plt.close()

def record_utility(data):
    path = './' +graph_file + '/Graph 2/'
    narr = []
    
    for s_name, s in zip(os.listdir(path), data):
        path2 = os.path.join(path, s_name)
        print(path2)
        for i, run in enumerate(s):
            ts = np.linspace(1,len(run[0]),len(run[0]))
            fig = plt.figure()
            fig.add_subplot(111)
            plt.plot(ts, run[0], label="MST")
            plt.plot(ts, run[1], label="RT")
            title = str(i + 1)
            npath = os.path.join(path2, 'Run ' + str(i+1) + '.png')
            plt.title(s_name + ' Expected Value, ' + 'Run ' + title)
            plt.legend(loc='upper right')
            plt.xlabel('Timestep')
            plt.ylabel('Expected Value')
            fig.set_size_inches(50, 10)
            plt.savefig(npath, dpi=100)
            plt.clf()
            plt.close()
        
        
def get_cmap(n, name='hsv'):
    return plt.get_cmap(name, n)
        
def get_loc(Folder):
    x = os.listdir(Folder)
    y = []
    if not os.path.exists('Graphs'):
        os.mkdir('Graphs')
    for i, j in enumerate(x):
        x[i] = os.path.join(Folder, j)
        y.append(os.path.join('Graphs', j))
    return x, y

ress, Graph_loc = get_loc('Results')
x = os.path.split(ress[0])


for i in ress:
    np.array(load_data(i))
