package com.eleybourn.bookcatalogue.utils.xml;

import android.util.Log;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

/**
 * DEBUG.
 */
public class XmlDumpParser
        extends DefaultHandler {

    private static final String TAG = "XmlDumpParser";

    private boolean mNamespaceBegin = false;

    private String mCurrentNamespace;

    private String mCurrentNamespaceUri;

    @SuppressWarnings("FieldCanBeLocal")
    private Locator mLocator;

    public void setDocumentLocator(final Locator locator) {
        this.mLocator = locator;
    }

    public void startDocument() {
        Log.d(TAG, "<?xml version=\"1.0\"?>");
    }

    public void endDocument() {
    }

    public void startPrefixMapping(final String prefix,
                                   final String uri) {
        mNamespaceBegin = true;
        mCurrentNamespace = prefix;
        mCurrentNamespaceUri = uri;
    }

    public void endPrefixMapping(final String prefix) {
    }

    public void startElement(final String namespaceURI,
                             final String localName,
                             final String qName,
                             final Attributes attributes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(qName);
        if (mNamespaceBegin) {
            sb.append(" xmlns:").append(mCurrentNamespace).append("=\"")
              .append(mCurrentNamespaceUri).append("\"");
            mNamespaceBegin = false;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            sb.append(" ").append(attributes.getQName(i)).append("=\"")
              .append(attributes.getValue(i)).append("\"");
        }
        sb.append(">");
        Log.d(TAG, sb.toString());
    }

    public void endElement(final String namespaceURI,
                           final String localName,
                           final String qName) {
        Log.d(TAG, "</" + qName + ">");
    }

    public void characters(final char[] ch,
                           final int start,
                           final int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + length; i++) {
            sb.append(ch[i]);
        }
        Log.d(TAG, sb.toString());
    }

    public void ignorableWhitespace(final char[] ch,
                                    final int start,
                                    final int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + length; i++) {
            sb.append(ch[i]);
        }
        Log.d(TAG, sb.toString());
    }

    public void processingInstruction(final String target,
                                      final String data) {
        Log.d(TAG, "<?" + target + " " + data + "?>");
    }

    public void skippedEntity(final String name) {
        Log.d(TAG, "&" + name + ";");
    }
}
