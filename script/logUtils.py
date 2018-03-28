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


def errPrintln(aMsg="", indent=0):
	"""Print the specified string with a trailing newline to stderr."""
	while indent > 0:
		indent -= 1
		aMsg = '   ' + aMsg
	sys.stderr.write(aMsg + '\n')


def regPrintln(aMsg="", indent=0):
	"""Print the specified string with a trailing newline to stdout."""
	while indent > 0:
		indent -= 1
		aMsg = '   ' + aMsg
	sys.stdout.write(aMsg + '\n')

