#! /usr/bin/env python3

import argparse
import hashlib
import os
import time
import subprocess
import sys

import logUtils


class FancyArgumentParser(argparse.ArgumentParser):
	def __init__(self, *args, **kwargs):
		self._action_defaults = {}
		argparse.ArgumentParser.__init__(self, *args, **kwargs)

	def convert_arg_line_to_args(self, arg_line):
		# Add the line as an argument if it does not appear to be a comment
		if len(arg_line) > 0 and arg_line.strip()[0] != '#':
			yield arg_line
#			argsparse.ArgumentParser.convert_arg_line_to_args(arg_line)
#		else:
#			print('Skipping line: ' + arg_line)

#		# Example below will break all space separated lines into individual arguments
#		for arg in arg_line.split():
#			if not arg.strip():
#				continue
#			yield arg


class ErrorDM(Exception):
    """Base class for exceptions in this module."""
    pass


def checkRoot():
	"""Determines if the script is running with root priveleges."""
	# This logic will may break on SELinux systems
	if os.geteuid() != 0:
		msg = '   You need to have root privileges to run this script.\n'
		msg += '   Please run this script with sudo!\n'
		msg += '   Exiting...\n'
		exit(msg)


# Source: http://stackoverflow.com/questions/1131220/get-md5-hash-of-a-files-without-open-it-in-python
def computeDigestForFile(evalFile, digestType, block_size=2**20):
	# Select the proper hash algorithm
	if digestType == 'md5':
		hash = hashlib.md5()
	elif digestType == 'sha256':
		hash = hashlib.sha256()
	elif digestType == 'sha512':
		hash = hashlib.sha512()
	else:
		raise ErrorDM('Unrecognized hash function: ' + digestType);

	with open(evalFile, mode='rb') as tmpFO:
		while True:
			data = tmpFO.read(block_size)
			if not data:
				break
			hash.update(data)

		return hash.hexdigest()


def getPlatformTypes(aPlatformArr, aPlatformStr):
	"""Returns an object that defines the release types that should be built for the given platform. The object will
	have 2 field members: [nonJre, withJre]. If the field is set to True then the corresonding platform should be
	built. This is determined by examaning aPlatformStr and determine it occurs in aPlatformArr. For
	example to determine the build platforms for Linux one might call getPlatformArr(someArr, 'linux'). Following are
	the results of contents in someArr:
	   ['']       ---> nonJre = False, withJre = False
	   ['linux']  --->  nonJre = True, withJre = True
	   ['linux+'] --->  nonJre = False, withJre = True
	   ['linux-'] --->  nonJre = True, withJre = False"""
	class PlatformType(object):
		nonJre = False
		withJre = False

	retObj = PlatformType()
	if aPlatformStr in aPlatformArr:
		retObj.nonJre = True
		retObj.withJre = True
	if aPlatformStr + '-' in aPlatformArr:
		retObj.nonJre = True
	if aPlatformStr + '+' in aPlatformArr:
		retObj.withJre = True
	return retObj


def getInstallRoot():
	"""Returns the root path where the running script is installed."""
	argv = sys.argv;
	installRoot = os.path.dirname(argv[0])
	return installRoot


def executeAndLog(aCommand, indentStr=""):
	"""Executes the specified command via subprocess and logs all (stderr,stdout) output to the console"""
	try:
		proc = subprocess.Popen(aCommand, stderr=subprocess.STDOUT, stdout=subprocess.PIPE)
		proc.wait()
		outStr = proc.stdout.read().decode('utf-8')
		if outStr != "":
			outStr = logUtils.appendLogOutputWithText(outStr, indentStr)
			print(outStr)
	except Exception as aExp:
		print(indentStr + 'Failed to execute command: ' + str(aCommand))
		outStr = logUtils.appendLogOutputWithText(str(aExp), indentStr)
		print(outStr)
		print(indentStr + 'Stack Trace:')
		outStr = logUtils.appendLogOutputWithText(aExp.child_traceback, indentStr + '\t')
		print(outStr)

		class Proc:
			returncode = None
		proc = Proc

	return proc
#		if proc.returncode != 0:
#			print('\tError: Failed to build executable. Return code: ' + proc.returncode)


def getPathSize(aRoot):
	"""Computes the total disk space used by the specified path.
	Note if aRoot does not exist or is None then this will return 0"""
	# Check for existence
	if aRoot == None or os.path.exists(aRoot) == False:
		return 0

	# Return the file size of aRoot if just a file
	aRoot = os.path.abspath(aRoot)
	if os.path.isfile(aRoot) == True:
		return os.path.getsize(aRoot)

	# Add in all of the file sizes in the directory
	numBytes = 0
	for path, dirs, files in os.walk(aRoot):
		for f in files:
			fPath = os.path.join(path, f)
			if os.path.isfile(fPath) == True:
				numBytes += os.path.getsize(fPath)
	return numBytes


def handleSignal(signal, frame):
	"""Signal handler, typically used to capture ctrl-c."""
	print('User aborted processing!')
	sys.exit(0)


def requirePythonVersion(aVer):
	"""Checks the version of python running and if not correct will exit with
	an error message."""
	if aVer <= sys.version_info:
		return

	print('The installed version of python is too old. Please upgrade.')
	print('   Current version: ' + '.'.join(str(i) for i in sys.version_info))
	print('   Require version: ' + '.'.join(str(i) for i in aVer))
	exit(-1)


def buildAppLauncherConfig(aDstFile, aArgs):
	jvmArgsStr = ''
	for aStr in aArgs.jvmArgs:
		if len(aStr) > 2 and aStr[0:1] == '\\':
			aStr = aStr[1:]
		jvmArgsStr += aStr + ' '

	appArgsStr = ''
	for aStr in aArgs.appArgs:
		appArgsStr += ' ' + aStr

	with open(aDstFile, mode='wt', encoding='utf-8', newline='\n') as tmpFO:
		# App name section
		tmpFO.write('-name\n')
		tmpFO.write(aArgs.name + '\n')
		tmpFO.write('\n')

		# Version section
		tmpFO.write('-version\n')
		tmpFO.write(aArgs.version + '\n')
		tmpFO.write('\n')

		# Build date section
		exeDate = time.localtime()
		buildDate = time.strftime('%Y%b%d %H:%M:%S', exeDate)
		tmpFO.write('-buildDate\n')
		tmpFO.write(buildDate + '\n')
		tmpFO.write('\n')

		# MainClass section
		tmpFO.write('-mainClass\n')
		tmpFO.write(aArgs.mainClass + '\n')
		tmpFO.write('\n')

		# ClassPath section
		tmpFO.write('-classPath\n')
		for aPath in aArgs.classPath:
			fileName = os.path.basename(aPath)
			tmpFO.write(fileName + '\n')
		tmpFO.write('\n')

		# Application args section
		tmpFO.write('-appArgs\n')
		for aStr in aArgs.appArgs:
			tmpFO.write(aStr + '\n')
		tmpFO.write('\n')

