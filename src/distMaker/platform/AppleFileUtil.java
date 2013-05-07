package distMaker.platform;

import glum.io.IoUtil;

import java.io.File;
import java.io.FileOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;

/**
 * Utility class which contains a set of methods to interact with an Apple Info.plist file.
 */
public class AppleFileUtil
{
	/**
	 * Utility method to update the specified max memory (-Xmx) value in the plist file (aFile) to the specified
	 * maxMemVal.
	 * <P>
	 * Note this method is very brittle, and assumes that the value occurs within <string> tags. The assumption is that
	 * the occurrence of the string, -Xmx, will be once and refer to the max heap memory.
	 */
	public static boolean updateMaxMem(File aFile, long numBytes)
	{
		DocumentBuilderFactory dbf;
		Document dom;
		Element doc;
		String evalStr, memStr, oldStr;
		String[] evalArr;

		// Determine the memStr to use
		if (numBytes % (1024 * 1024 * 1024) == 0)
			memStr = "-Xmx" + (numBytes / (1024 * 1024 * 1024)) + "G";
		else
			memStr = "-Xmx" + (numBytes / (1024 * 1024)) + "M";

		// Make an instance of the DocumentBuilderFactory
		dbf = DocumentBuilderFactory.newInstance();
		try
		{
			// use the factory to take an instance of the document builder
			DocumentBuilder db = dbf.newDocumentBuilder();

			// Parse using the builder to get the DOM mapping of the XML file
			dom = db.parse(aFile);
			doc = dom.getDocumentElement();

			NodeList nodeList;
			Node keyNode;

			nodeList = doc.getElementsByTagName("string");
			for (int c1 = 0; c1 < nodeList.getLength(); c1++)
			{
				keyNode = nodeList.item(c1).getFirstChild();
				if (keyNode != null)
				{
					evalStr = keyNode.getNodeValue();
					if (evalStr != null && evalStr.contains("-Xmx") == true)
					{
						evalArr = evalStr.split(" ");
						for (int c2 = 0; c2 < evalArr.length; c2++)
						{
							oldStr = evalArr[c2];
							if (oldStr.startsWith("-Xmx") == true)
								evalArr[c2] = memStr;
						}

						System.out.println("Updating contents of file: " + aFile);
						System.out.println("  Old Version: " + evalStr);

						// Reconstitute the new evalStr
						evalStr = "";
						for (String aStr : evalArr)
							evalStr += " " + aStr;
						if (evalStr.length() > 0)
							evalStr = evalStr.substring(1);

						System.out.println("  New Version: " + evalStr);
						keyNode.setNodeValue(evalStr);
						break;
					}
				}
			}

			// Update the file with the changed document
			writeDoc(aFile, dom);

			return true;
		}
		catch (Exception aExp)
		{
			aExp.printStackTrace();
		}

		return false;
	}

	/**
	 * Utility method to update the specified version in the plist file (aFile) to the new version.
	 * <P>
	 * Note this method is very brittle, and assumes that the version will occur in the sibling node which immediately
	 * follows the node with a value of CFBundleVersion.
	 */
	public static boolean updateVersion(File aFile, String aNewVersin)
	{
		DocumentBuilderFactory dbf;
		Document dom;
		Element doc;

		// Make an instance of the DocumentBuilderFactory
		dbf = DocumentBuilderFactory.newInstance();
		try
		{
			// use the factory to take an instance of the document builder
			DocumentBuilder db = dbf.newDocumentBuilder();

			// Parse using the builder to get the DOM mapping of the XML file
			dom = db.parse(aFile);
			doc = dom.getDocumentElement();

			NodeList nodeList;
			Node keyNode, strNode;

			nodeList = doc.getElementsByTagName("*");
			for (int c1 = 0; c1 < nodeList.getLength(); c1++)
			{
				keyNode = nodeList.item(c1).getFirstChild();
				if (keyNode != null && keyNode.getNodeValue().equals("CFBundleVersion") == true)
				{
					System.out.println("Updating contents of file: " + aFile);

					strNode = nodeList.item(c1 + 1).getFirstChild();
					System.out.println("  Old Version: " + strNode.getNodeValue());

					strNode.setNodeValue(aNewVersin);
					System.out.println("  New Version: " + strNode.getNodeValue());
				}
			}

			// Update the file with the changed document
			writeDoc(aFile, dom);

			return true;
		}
		catch (Exception aExp)
		{
			aExp.printStackTrace();
		}

		return false;
	}

	/**
	 * Helper method to output the specified document to aFile
	 */
	private static boolean writeDoc(File aFile, Document aDoc)
	{
		FileOutputStream oStream;

		oStream = null;
		try
		{
			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
//			tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "roles.dtd");
//			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

			// Serialize the Document
			oStream = new FileOutputStream(aFile);
			tr.transform(new DOMSource(aDoc), new StreamResult(oStream));

			return true;
		}
		catch (Exception aExp)
		{
			aExp.printStackTrace();
		}
		finally
		{
			IoUtil.forceClose(oStream);
		}

		return false;
	}

}
