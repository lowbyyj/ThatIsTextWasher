package com.lowbyyj.textwasher;

final class DocumentState {
    String text = "";
    String uri = "";
    String name = "";
    String encoding = "UTF-8";
    boolean bom;
    boolean dirty;
    int selectionStart;
    int selectionEnd;
    int scrollY;

    DocumentState copy() {
        DocumentState copy = new DocumentState();
        copy.text = text;
        copy.uri = uri;
        copy.name = name;
        copy.encoding = encoding;
        copy.bom = bom;
        copy.dirty = dirty;
        copy.selectionStart = selectionStart;
        copy.selectionEnd = selectionEnd;
        copy.scrollY = scrollY;
        return copy;
    }
}
