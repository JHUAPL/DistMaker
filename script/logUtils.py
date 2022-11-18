#! /usr/bin/env python3

import hashlib
import os
import time
import sys

# Globals
indentStr = '   '

def appendLogOutputWithText(aStr, aText):
	""" Method which given a string from a log will insert all occurances of newlines with the
	specified text. Note if the last character is a newline then it will be stripped before the replacement"""
	if aStr.endswith("\n") == True:
		aStr = aStr[:-1]
	retStr = aText + aStr.replace('\n', '\n' + aText)

	return retStr;


def errPrintln(aMsg=""):
	"""Print the specified string with a trailing newline to stderr."""
	aMsg = aMsg.replace('\t', indentStr)
	sys.stderr.write(aMsg + '\n')


def regPrintln(aMsg=""):
	"""Print the specified string with a trailing newline to stdout."""
	aMsg = aMsg.replace('\t', indentStr)
	sys.stdout.write(aMsg + '\n')


def setIndentStr(aStr):
	"""Sets in the string that will be used as the "indent" text. Any future
	calls to the methods errPrintln() and regPrintln() will have any textual
	tab characters automatically replaced with the specified string."""
	global indentStr
	indentStr = aStr
