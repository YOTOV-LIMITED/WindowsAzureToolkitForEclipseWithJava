/**
 * Copyright 2015 Microsoft Open Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.microsoftopentechnologies.azurecommons.xmlhandling;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.microsoftopentechnologies.azurecommons.messagehandler.PropUtil;


public class ParseXMLUtilMethods {
	private static String pXMLParseExcp = PropUtil.getValueFromFile("pXMLParseExcp");

	/** Parses XML file and returns XML document.
	 * @param fileName XML file to parse
	 * @return XML document or <B>null</B> if error occurred
	 * @throws Exception object
	 */
	public static Document parseFile(String fileName) throws Exception {
		DocumentBuilder docBuilder;
		Document doc = null;
		DocumentBuilderFactory docBuilderFactory =
				DocumentBuilderFactory.newInstance();
		docBuilderFactory.setIgnoringElementContentWhitespace(true);
		try {
			docBuilder = docBuilderFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new Exception(pXMLParseExcp, e);
		}
		File sourceFile = new File(fileName);
		try {
			doc = docBuilder.parse(sourceFile);
		} catch (SAXException e) {
			throw new Exception(pXMLParseExcp, e);
		} catch (IOException e) {
			throw new Exception(pXMLParseExcp, e);
		}
		return doc;
	}

	/**
	 * Save XML file and saves XML document.
	 *
	 * @param fileName
	 * @param doc
	 * @return boolean
	 * @throws Exception object
	 */
	public static boolean saveXMLDocument(String fileName, Document doc)
			throws Exception {
		// open output stream where XML Document will be saved
		File xmlOutputFile = new File(fileName);
		FileOutputStream fos = null;
		Transformer transformer;
		try {
			fos = new FileOutputStream(xmlOutputFile);
			// Use a Transformer for output
			TransformerFactory transformerFactory =
					TransformerFactory.newInstance();
			transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(fos);
			// transform source into result will do save
			transformer.transform(source, result);
		} finally {
			if (fos != null) {
				fos.close();
			}
		}

		return true;
	}
}
