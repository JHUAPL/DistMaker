#! /usr/bin/env python
import hashlib
import os
import time
import subprocess
import sys

import logUtils


def checkRoot():
	"""Determines if the script is running with root priveleges."""
	# This logic will may break on SELinux systems
	if os.geteuid() != 0:
		msg = '   You need to have root privileges to run this script.\n'
		msg += '   Please run this script with sudo!\n'
		msg += '   Exiting...\n'
		exit(msg)


# Source: http://stackoverflow.com/questions/1131220/get-md5-hash-of-a-files-without-open-it-in-python
def computeMd5ForFile(evalFile, block_size=2**20):
	f = open(evalFile, 'rb')
	md5 = hashlib.md5()
	while True:
		data = f.read(block_size)
		if not data:
			break
		md5.update(data)
	f.close()
	return md5.hexdigest()


def getInstallRoot():
	"""Returns the root path where the running script is installed."""
	argv = sys.argv;
	installRoot = os.path.dirname(argv[0])
#	print('appInstallRoot: ' + appInstallRoot)
	return installRoot


def executeAndLog(command, indentStr=""):
	"""Executes the specified command via subprocess and logs all (stderr,stdout) output to the console"""
#	proc = subprocess.
#	proc.returncode = -1
	try:
		proc = subprocess.Popen(command, stderr=subprocess.STDOUT, stdout=subprocess.PIPE)
		proc.wait()
		outStr = proc.stdout.read()
		if outStr != "":
			outStr = logUtils.appendLogOutputWithText(outStr, indentStr)
			print(outStr)
	except Exception as aExp:
		print(indentStr + 'Failed to execute command: ' + str(command))
		outStr = logUtils.appendLogOutputWithText(str(aExp), indentStr)
		print(outStr)
		print(indentStr + 'Stack Trace:')
		outStr = logUtils.appendLogOutputWithText(aExp.child_traceback, indentStr + '\t')
		print(outStr)
		
		class Proc:
			returncode = None
		proc = Proc
		
#		proc = None
	
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


def buildAppLauncherConfig(destFile, args):
	classPathStr = ''
	for aStr in args.classPath:
		classPathStr += 'java/' + aStr + ':'
	if len(classPathStr) > 0:
		classPathStr = classPathStr[0:-1]

	jvmArgsStr = ''
	for aStr in args.jvmArgs:
		if len(aStr) > 2 and aStr[0:1] == '\\':
			aStr = aStr[1:]
		jvmArgsStr += aStr + ' '

	appArgsStr = ''
	for aStr in args.appArgs:
		appArgsStr += ' ' + aStr
		
	f = open(destFile, 'wb')
	
	# App name section
	f.write('-name\n')
	f.write(args.name + '\n')
	f.write('\n')

	# Version section
	f.write('-version\n')
	f.write(args.version + '\n')
	f.write('\n')

	# Build date section
	exeDate = time.localtime()
	buildDate = time.strftime('%Y%b%d %H:%M:%S', exeDate)
	f.write('-buildDate\n')
	f.write(buildDate + '\n')
	f.write('\n')

	# MainClass section
	f.write('-mainClass\n')
	f.write(args.mainClass + '\n')
	f.write('\n')

	# ClassPath section
	f.write('-classPath\n')
	for aStr in args.classPath:
		f.write(aStr + '\n')
	f.write('\n')

	# Application args section
	f.write('-appArgs\n')
	for aStr in args.appArgs:
		f.write(aStr + '\n')
	f.write('\n')

	f.close()

