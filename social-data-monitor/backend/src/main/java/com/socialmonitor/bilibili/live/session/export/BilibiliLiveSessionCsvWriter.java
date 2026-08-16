package com.socialmonitor.bilibili.live.session.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public final class BilibiliLiveSessionCsvWriter {

    private static final char UTF_8_BOM = '\uFEFF';

    private final Writer writer;

    public BilibiliLiveSessionCsvWriter(OutputStream outputStream) throws IOException {
        this.writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        writer.write(UTF_8_BOM);
    }

    public void writeRow(Object... values) throws IOException {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                writer.write(',');
            }
            writeField(values[index]);
        }
        writer.write("\r\n");
    }

    public void flush() throws IOException {
        writer.flush();
    }

    private void writeField(Object value) throws IOException {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (value instanceof CharSequence && isFormula(text)) {
            text = "'" + text;
        }
        boolean quote = text.indexOf(',') >= 0
                || text.indexOf('"') >= 0
                || text.indexOf('\r') >= 0
                || text.indexOf('\n') >= 0;
        if (quote) {
            writer.write('"');
        }
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"') {
                writer.write("\"\"");
            } else {
                writer.write(character);
            }
        }
        if (quote) {
            writer.write('"');
        }
    }

    private boolean isFormula(String value) {
        if (value.isEmpty()) {
            return false;
        }
        int index = 0;
        boolean leadingControlWhitespace = false;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            if (value.charAt(index) < ' ') {
                leadingControlWhitespace = true;
            }
            index++;
        }
        if (leadingControlWhitespace) {
            return true;
        }
        if (index == value.length()) {
            return false;
        }
        char first = value.charAt(index);
        return first == '=' || first == '+' || first == '-' || first == '@';
    }
}
