import hashlib
import os
import time
import sys


def appendLogOutputWithText(aStr, aText):
	""" Method which given a string from a log will insert all occurances of newlines with the 
	specified text. Note if the last character is a newline then it will be stripped before the replacement"""
	if aStr.endswith("\n") == True:
		aStr = aStr[:-1]
	retStr = aText + aStr.replace('\n', '\n' + aText)

	return retStr;


def log(aStr, tabReplace=None):
	""" Method which will print aStr to the console and optionally replace  all tab
	characters with tabReplace"""
	if tabReplace != None:
		aStr.replace('\t', tabReplace)

	print(aStr)

