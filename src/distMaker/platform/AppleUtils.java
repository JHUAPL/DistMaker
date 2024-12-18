// Copyright (C) 2024 The Johns Hopkins University Applied Physics Laboratory LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package distMaker.platform;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;

import org.w3c.dom.*;

import distMaker.*;
import distMaker.jre.*;

/**
 * Collection of utility methods specific to the Macosx platform.
 *
 * @author lopeznr1
 */
public class AppleUtils
{
	/**
	 * Returns the plist file used to configure apple applications.
	 * <p>
	 * Two locations will be searched.... TODO: Add more details of those locations.
	 * <p>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static File getPlistFile()
	{
		File installPath;
		File pFile;

		// Get the top level install path
		installPath = DistUtils.getAppPath().getParentFile();

		// Attempt to locate the pList file (from one of the possible locations)
		pFile = new File(installPath, "Info.plist");
		if (pFile.isFile() == false)
			pFile = new File(installPath.getParentFile(), "Info.plist");

		// Bail if we failed to locate the plist file
		if (pFile.exists() == false)
			throw new ErrorDM("The plist file could not be located.");

		// Bail if the plist file is not a regular file.
		if (pFile.isFile() == false)
			throw new ErrorDM("The plist file does not appear to be a regular file: " + pFile);

		return pFile;
	}

	/**
	 * Utility method to update the specified version in the plist file (pFile) to the new version.
	 * <p>
	 * Note this method is very brittle, and assumes that the version will occur in the sibling node which immediately
	 * follows the node with a value of CFBundleVersion. TODO: Consider reducing brittleness.
	 * <p>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateAppVersion(String aNewVersin, File pFile)
	{
		Document doc;
		Element docElement;
		NodeList nodeL;
		Node keyNode, strNode;
		String valStr;

		// Bail if the pFile is not writable.
		if (pFile.setWritable(true) == false)
			throw new ErrorDM("The plist file is not writeable: " + pFile);

		// Load the XML document via the javax.xml.parsers.* package
		try
		{
			doc = loadDoc(pFile);
			docElement = doc.getDocumentElement();

			// Clean the XML doc (due to defective Java 9 implementation)
			cleanDoc(doc);
		}
		catch(Exception aExp)
		{
			throw new ErrorDM(aExp, "Failed to parse XML document. File: " + pFile);
		}

		nodeL = docElement.getElementsByTagName("*");
		for (int c1 = 0; c1 < nodeL.getLength(); c1++)
		{
			keyNode = nodeL.item(c1).getFirstChild();
			if (keyNode == null)
				continue;

			valStr = keyNode.getNodeValue();
			if (valStr == null)
				continue;

			if (valStr.equals("CFBundleVersion") == true)
			{
				System.out.println("Updating contents of file: " + pFile);

				strNode = nodeL.item(c1 + 1).getFirstChild();
				System.out.println("  Old App Version: " + strNode.getNodeValue());

				strNode.setNodeValue(aNewVersin);
				System.out.println("  New App Version: " + strNode.getNodeValue());
			}
		}

		// Update the file with the changed document
		saveDoc(pFile, doc);
	}

	/**
	 * Utility method to update the configuration file (pFile) to reflect the specified AppLauncherRelease.
	 * <p>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateAppLauncher(AppLauncherRelease aRelease, File pFile)
	{
		// Note the JavaAppLauncher executable appears to no longer support specifying the class path
		// Thus there is nothing to update as the AppLauncherRelease is implicitly loaded by it being in
		// the path <AppName>/Contents/Java/ folder.
		//
		// Be aware of one big caveat - if there are multiple AppLauncher jars in the implicit location it
		// is not clear which one will be selected!
		//
		// Change made as of 2018Apr11
		return;

//		List<String> inputList;
//		String evalStr, tmpStr;
//		String prevKeyValue;
//		boolean isFound;
//
//		// Bail if the pFile is not writable
//		if (pFile.setWritable(true) == false)
//			throw new ErrorDM("The pFile is not writeable: " + pFile);
//
//		// Define the regex we will be searching for
//		String regex = "<string>(.*?)</string>";
//		Pattern tmpPattern = Pattern.compile(regex);
//
//		// Process our input
//		inputL = new ArrayList<>();
//		try (BufferedReader br = MiscUtils.openFileAsBufferedReader(pFile))
//		{
//			// Read the lines
//			isFound = false;
//			prevKeyValue = "";
//			while (true)
//			{
//				evalStr = br.readLine();
//				if (evalStr == null)
//					break;
//
//				// Keep track of the last key element
//				tmpStr = evalStr.trim();
//				if (tmpStr.startsWith("<key>") == true && tmpStr.endsWith("</key>") == true)
//					prevKeyValue = tmpStr.substring(5, tmpStr.length() - 6).trim();
//
//				// The AppLauncher is specified just after the key element with the value: ClassPath
//				if (prevKeyValue.equals("ClassPath") == true && tmpPattern.matcher(evalStr).find() == true)
//				{
//					// Perform the replacement
//					String repStr = "<string>$JAVAROOT/" + PlatformUtils.getAppLauncherFileName(aRelease.getVersion()) + "</string>";
//					repStr = Matcher.quoteReplacement(repStr);
//					evalStr = tmpPattern.matcher(evalStr).replaceFirst(repStr);
//
//					isFound = true;
//				}
//
//				inputL.add(evalStr);
//			}
//		}
//		catch(IOException aExp)
//		{
//			throw new ErrorDM(aExp, "Failed while processing the pFile: " + pFile);
//		}
//
//		// Fail if there was no update performed
//		if (isFound == false)
//			throw new ErrorDM("[" + pFile + "] The pFile does not specify a 'ClassPath' section.");
//
//		// Write the pFile
//		MiscUtils.writeDoc(pFile, inputList);
	}

	/**
	 * Utility method to update the configuration file (pFile) to reflect the specified JRE version.
	 * <p>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateJreVersion(JreVersion aJreVersion, File pFile)
	{
		List<String> inputL;
		String evalStr, tmpStr;
		String prevKeyValue;
		boolean isFound;

		// Bail if the pFile is not writable
		if (pFile.setWritable(true) == false)
			throw new ErrorDM("The pFile is not writeable: " + pFile);

		// Define the regex we will be searching for
		String regex = "<string>(.*?)</string>";
		Pattern tmpPattern = Pattern.compile(regex);

		// Process our input
		isFound = false;
		inputL = new ArrayList<>();
		try (BufferedReader br = MiscUtils.openFileAsBufferedReader(pFile))
		{
			// Read the lines
			prevKeyValue = "";
			while (true)
			{
				evalStr = br.readLine();
				if (evalStr == null)
					break;

				// Keep track of the last key element
				tmpStr = evalStr.trim();
				if (tmpStr.startsWith("<key>") == true && tmpStr.endsWith("</key>") == true)
					prevKeyValue = tmpStr.substring(5, tmpStr.length() - 6).trim();

				// The JRE is specified just after the key element with the value: JVMRuntime
				if (prevKeyValue.equals("JVMRuntime") == true && tmpPattern.matcher(evalStr).find() == true)
				{
					// Perform an inline replacement
					String repStr = "<string>" + JreUtils.getExpandJrePath(aJreVersion) + "</string>";
					evalStr = tmpPattern.matcher(evalStr).replaceFirst(repStr);

					isFound = true;
				}

				inputL.add(evalStr);
			}
		}
		catch(IOException aExp)
		{
			throw new ErrorDM(aExp, "Failed while processing the pFile: " + pFile);
		}

		// Fail if there was no update performed
		if (isFound == false)
			throw new ErrorDM("[" + pFile + "] The pFile does not specify a 'JVMRuntime' section.");

		// Write the pFile
		MiscUtils.writeDoc(pFile, inputL);
	}

	/**
	 * Utility method to update the specified max memory (-Xmx) value in the plist file (pFile) to the specified
	 * maxMemVal.
	 * <p>
	 * In order for this method to succeed there must be a valid JVMOptions section followed by an array of string
	 * elements of JVM arguments. The array element may be empty but must be specified.
	 * <p>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateMaxMem(long numBytes, File pFile)
	{
		Document doc;
		Element docElement;
		String evalStr, updateStr;
		NodeList dictNL, childNL;
		Node childNode, targNode;
		Element evalE, arrE, memE;
		String tagStr, valStr, currKeyVal;

		// Bail if the pFile is not writable.
		if (pFile.setWritable(true) == false)
			throw new ErrorDM("The plist file is not writeable: " + pFile);

		// Load the XML document via the javax.xml.parsers.* package
		try
		{
			// Load the XML document
			doc = loadDoc(pFile);
			docElement = doc.getDocumentElement();

			// Clean the XML doc (due to defective Java 9 implementation)
			cleanDoc(doc);
		}
		catch(Exception aExp)
		{
			throw new ErrorDM(aExp, "Failed to parse XML document. File: " + pFile);
		}

		// Locate the <dict> element
		dictNL = docElement.getElementsByTagName("dict");
		if (dictNL.getLength() == 0)
			throw new ErrorDM("No <dict> element found! File: " + pFile);

		arrE = null;
		currKeyVal = null;
		childNL = dictNL.item(0).getChildNodes();
		for (int c1 = 0; c1 < childNL.getLength(); c1++)
		{
			childNode = childNL.item(c1);
			if (childNode.getNodeType() != Node.ELEMENT_NODE)
				continue;

			evalE = (Element)childNode;
			tagStr = evalE.getTagName();
			valStr = evalE.getNodeValue();

			if (childNode.hasChildNodes() == true)
			{
				childNode = childNode.getFirstChild();
				valStr = childNode.getNodeValue();
			}
//			System.out.println("" + c1 + " Tag:" + tagStr + ": Value:" + valStr);

			if (tagStr.equals("key") == true)
				currKeyVal = childNode.getNodeValue();

			// Bail once we locate the <array> element following the <key>JVMOptions<key>
			if (tagStr.equals("array") == true && currKeyVal != null && currKeyVal.equals("JVMOptions") == true)
			{
				arrE = evalE;
				break;
			}
		}

		// Bail if we failed to locate the array element
		if (arrE == null)
			throw new ErrorDM("Failed to locate the element <array> following the element: <key>JVMOptions</key>\nFile: " + pFile);

		memE = null;
		childNL = arrE.getChildNodes();
		for (int c1 = 0; c1 < childNL.getLength(); c1++)
		{
			childNode = childNL.item(c1);
			if (childNode.getNodeType() != Node.ELEMENT_NODE)
				continue;

			if (childNode.hasChildNodes() == false)
				continue;

			evalE = (Element)childNode;
			tagStr = evalE.getTagName();
			valStr = evalE.getNodeValue();
			if (childNode.hasChildNodes() == true)
			{
				childNode = childNode.getFirstChild();
				valStr = childNode.getNodeValue();
			}

//			System.out.println("" + c1 + " Tag:" + tagStr + ": Value:" + valStr);
			if (valStr.startsWith("-Xmx") == false)
				continue;

			memE = evalE;
		}

		// Synthesize the memElement if it does not exist (with default: -Xmx512m)
		if (memE == null)
		{
			memE = doc.createElement("string");
			arrE.appendChild(memE);
		}
		// Ensure the memElement has a valid child (text value)
		if (memE.getChildNodes().getLength() == 0)
			memE.appendChild(doc.createTextNode("-Xmx512m"));

		// Update the -Xmx value
		targNode = memE.getChildNodes().item(0);
		evalStr = targNode.getNodeValue();
		updateStr = MemUtils.transformMaxMemHeapString(evalStr, numBytes);
		if (updateStr == null)
			throw new ErrorDM("Failed to transform the memory spec value. Original value: " + evalStr + "\nFile: " + pFile);
		targNode.setNodeValue(updateStr);

		// Update the file with the changed document
		saveDoc(pFile, doc);
	}

	/**
	 * Utility helper method to clean up the specified XML document.
	 * <p>
	 * Clean up the XML to remove spurious empty line nodes. This is needed in Java 9 since the XML processing is
	 * different from Java 8 and prior. Spurious newlines seem to be introduced with Java 9 XML libs.
	 * <p>
	 * Source:
	 * <ul>
	 * <li>http://java9.wtf/xml-transformer/
	 * <li>https://stackoverflow.com/questions/12669686/how-to-remove-extra-empty-lines-from-xml-file
	 * </ul>
	 *
	 * @param aDoc
	 * @throws XPathExpressionException
	 */
	private static void cleanDoc(Document aDoc) throws XPathExpressionException
	{
		// Clean up the XML to remove spurious empty line nodes. This is needed in Java 9 since the XML processing
		// is different from Java 8 and prior. Spurious newlines seem to be introduced with Java 9 XML libs.
		// Source:
		// http://java9.wtf/xml-transformer/
		// https://stackoverflow.com/questions/12669686/how-to-remove-extra-empty-lines-from-xml-file
		XPath xp = XPathFactory.newInstance().newXPath();
		NodeList nl = (NodeList)xp.evaluate("//text()[normalize-space(.)='']", aDoc, XPathConstants.NODESET);

		for (int i = 0; i < nl.getLength(); ++i)
		{
			Node node = nl.item(i);
			node.getParentNode().removeChild(node);
		}
	}

	/**
	 * Utility helper method to load a Document from the specified file.
	 */
	private static Document loadDoc(File aFile) throws Exception
	{
		DocumentBuilderFactory dbf;
		DocumentBuilder db;
		Document doc;

		// Parse the XML file via the javax.xml.parsers.* package
		dbf = DocumentBuilderFactory.newInstance();
		db = dbf.newDocumentBuilder();
		doc = db.parse(aFile);

		return doc;
	}

	/**
	 * Utility helper method to output aDoc to the specified file.
	 * <p>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	private static void saveDoc(File aFile, Document aDoc)
	{
		try (FileOutputStream oStream = new FileOutputStream(aFile);)
		{
			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");
//			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
//			tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "roles.dtd");

			// Serialize the Document
			tr.transform(new DOMSource(aDoc), new StreamResult(oStream));
		}
		catch(Exception aExp)
		{
			throw new ErrorDM(aExp, "Failed to write the file: " + aFile);
		}
	}

}
