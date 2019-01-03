import matplotlib
import matplotlib.pyplot as pyplot
import csv
import sys
import os
from operator import itemgetter

def createPlot(xList, xLabel, yLabel, title):
    pyplot.hist(xList, bins=50)
    pyplot.title(title)
    pyplot.xlabel(xLabel)
    pyplot.ylabel(yLabel)
    
def sortByNumberOfLines(fileList):
	l = []
	for fName in fileList:
		dic = {}
		dic["file name"] = fName
		dic["line count"] = countLines(fName)
		l.append(dic)
	sortedList = sorted(l, key=itemgetter('line count'), reverse=True)
	return [dic["file name"] for dic in sortedList]
	
def countLines(fName):
    with open(fName) as f:
        for i, l in enumerate(f):
            pass
    return i + 1
    
if __name__ == "__main__":
	legendNames = []
	sortedFilenames = sortByNumberOfLines(sys.argv[1:])
	for inFileName in sortedFilenames:
		fileName, fileExtension = os.path.splitext(inFileName)
		with open(inFileName, 'rb') as csvFile:
			legendNames.append(fileName)
			lines = csvFile.readlines()
			xLabel = lines[0].split("\t")[0]
			yLabel = lines[0].split("\t")[1]
			lines = lines[1:]
			xList = [float(line.split("\t")[0]) for line in lines]
			createPlot(xList, xLabel, yLabel, fileName)
	pyplot.legend(legendNames, loc='upper right')
	pyplot.savefig("chart.png")
	pyplot.show()







