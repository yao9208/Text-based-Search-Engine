#!/usr/bin/env python
# coding=utf-8
forigin = open("qry.txt")
fd = open("query.txt", 'w+')
lines = forigin.readlines()
result = []
w=["0.2",'0.8']
weight = ['0.30', '0.30', '0.40']
for l in lines:
    temp = l.split(":")
    term = temp[1].split(" ")
    line = "#WAND("
    termii = weight[0] + " #And("
    term[-1] = term[-1].strip()
    for i in term:
        termii = termii + " " + i
    line = temp[0] + ":" + line + termii + ") "
    termii = weight[1]+" #AND(" 
    for i in range(len(term)-1):
        termii = termii + " #NEAR/1( " + term[i]+" "+term[i+1]+")"
    line = line + termii + ") "
    termii = weight[2] + " #AND("
    for i in range(len(term)-1):
        termii = termii + " #window/8(" + term[i] + " " + term[i+1]+")"  
    line = line + termii + ") "
    result.append(line.strip() + ")\n")
fd.writelines(result)
