/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2025 Appose developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.apposed.appose.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Helper class for working with XML documents.
 * 
 * @author Curtis Rueden
 */
class XML {

    /** The parsed XML DOM. */
	private final Document doc;

	/** XPath evaluation mechanism. */
	private final XPath xpath;

	/** Parses XML from the given file. */
	XML(final File file) throws IOException {
		this(loadXML(file));
	}


	/** Parses XML from the given URL. */
	XML(final URL url) throws IOException {
		this(loadXML(url));
	}

	private XML(Document doc) {
		this.doc = doc;

		XPath xp;
		final Thread thread = Thread.currentThread();
		final ClassLoader contextClassLoader = thread.getContextClassLoader();
		try {
			ClassLoader loader = contextClassLoader;
			while (true) {
				try {
					xp = XPathFactory.newInstance().newXPath();
					try {
						// Make sure that the current xalan/xerces pair can evaluate
						// expressions (i.e. *not* throw NoSuchMethodErrors).
						xp.evaluate("//dummy", doc);
					} catch (Throwable t) {
						throw new Error(t);
					}
					break;
				}
				catch (Error e) {
					loader = loader.getParent();
					if (loader == null) throw e;
					thread.setContextClassLoader(loader);
				}
			}
			xpath = xp;
		}
		finally {
			thread.setContextClassLoader(contextClassLoader);
		}
	}

	// -- XML methods --

	/** Obtains the CDATA identified by the given XPath expression. */
	String cdata(final String expression) {
		final NodeList nodes = xpath(expression);
		if (nodes == null || nodes.getLength() == 0) return null;
		return cdata(nodes.item(0));
	}

	/** Obtains the nodes identified by the given XPath expression. */
	NodeList xpath(final String expression) {
		final Object result;
		try {
			result = xpath.evaluate(expression, doc, XPathConstants.NODESET);
		}
		catch (final XPathExpressionException e) {
			return null;
		}
		return (NodeList) result;
	}

	// -- Helper methods --

	/** Gets the CData beneath the given node. */
	private static String cdata(final Node item) {
		final NodeList children = item.getChildNodes();
		if (children.getLength() == 0) return null;
		for (int i = 0; i < children.getLength(); i++) {
			final Node child = children.item(i);
			if (child.getNodeType() != Node.TEXT_NODE) continue;
			return child.getNodeValue();
		}
		return null;
	}

	/** Loads an XML document from the given URL. */
	private static Document loadXML(final URL url) throws IOException
	{
		final InputStream in = url.openStream();
		final Document document = loadXML(in);
		in.close();
		return document;
	}

	/** Loads an XML document from the given input stream. */
	private static Document loadXML(final InputStream in) throws IOException {
		try {
			return createBuilder().parse(in);
		}
		catch (SAXException | ParserConfigurationException e) {
            throw new IOException(e);
        }
    }

	/** Loads an XML document from the given file. */
	private static Document loadXML(final File file) throws IOException {
		try {
			return createBuilder().parse(file.getAbsolutePath());
		}
		catch (SAXException | ParserConfigurationException e) {
			throw new IOException(e);
		}
	}

	/** Creates an XML document builder. */
	private static DocumentBuilder createBuilder()
		throws ParserConfigurationException
	{
		return DocumentBuilderFactory.newInstance().newDocumentBuilder();
	}
}
