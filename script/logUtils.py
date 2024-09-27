#! /usr/bin/env python3

# Copyright (C) 2024 The Johns Hopkins University Applied Physics Laboratory LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

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
