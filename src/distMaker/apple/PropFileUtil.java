package distMaker.apple;

import glum.io.IoUtil;

import java.io.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

/**
 * Utility class which contains a set of methods to interact with an Apple Info.plist file.
 */
public class PropFileUtil
{
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
		catch (ParserConfigurationException aExp)
		{
			aExp.printStackTrace();
//			System.out.println(aExp);
		}
		catch (SAXException aExp)
		{
			aExp.printStackTrace();
//			System.out.println(aExp);
		}
		catch (IOException aExp)
		{
			aExp.printStackTrace();
//			System.err.println(aExp);
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
		}
		catch (TransformerException aExp)
		{
			aExp.printStackTrace();
//			System.out.println(aExp);
			return false;
		}
		catch (IOException aExp)
		{
			aExp.printStackTrace();
//			System.out.println(aExp);
			return false;
		}
		finally
		{
			IoUtil.forceClose(oStream);
		}

		return true;
	}

}
