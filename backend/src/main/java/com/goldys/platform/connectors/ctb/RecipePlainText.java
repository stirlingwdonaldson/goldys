package com.goldys.platform.connectors.ctb;

import java.io.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;

/** The JDK HTML parser extracts text without running scripts or fetching resources. */
final class RecipePlainText {
  static String extract(String source) {
    if (source == null) return null;
    if (!source.contains("<") && !source.contains("&")) return source;
    StringBuilder text = new StringBuilder();
    try {
      new ParserDelegator()
          .parse(
              new StringReader(source),
              new HTMLEditorKit.ParserCallback() {
                private boolean hidden;
                private final java.util.Deque<Integer> lists = new java.util.ArrayDeque<>();

                private void line() {
                  if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') text.append('\n');
                }

                @Override
                public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrs, int pos) {
                  if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) hidden = true;
                  if (tag.isBlock()) line();
                  if (tag == HTML.Tag.OL) lists.push(0);
                  if (tag == HTML.Tag.UL) lists.push(-1);
                  if (tag == HTML.Tag.LI) {
                    if (lists.isEmpty() || lists.peek() < 0) text.append("• ");
                    else {
                      int next = lists.pop() + 1;
                      lists.push(next);
                      text.append(next).append(". ");
                    }
                  }
                }

                @Override
                public void handleEndTag(HTML.Tag tag, int pos) {
                  if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) hidden = false;
                  if ((tag == HTML.Tag.OL || tag == HTML.Tag.UL) && !lists.isEmpty()) lists.pop();
                  if (tag.isBlock()) line();
                }

                @Override
                public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attrs, int pos) {
                  if (tag == HTML.Tag.BR || tag == HTML.Tag.HR) line();
                }

                @Override
                public void handleText(char[] data, int pos) {
                  if (!hidden) text.append(data);
                }
              },
              true);
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot extract recipe text", e);
    }
    return text.toString().replace('\u00a0', ' ').strip();
  }
}
