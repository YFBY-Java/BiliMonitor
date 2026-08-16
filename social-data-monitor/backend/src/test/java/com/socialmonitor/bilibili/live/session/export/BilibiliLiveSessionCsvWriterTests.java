package com.socialmonitor.bilibili.live.session.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BilibiliLiveSessionCsvWriterTests {

    @Test
    void writesUtf8BomCrLfRfc4180EscapingNullAndFormulaProtection() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BilibiliLiveSessionCsvWriter writer = new BilibiliLiveSessionCsvWriter(output);

        writer.writeRow("plain", "unsafe", "quoted", "nullable");
        writer.writeRow("ok", "=2+2", "a,b\"c", null);
        writer.writeRow("+cmd", "-1", "@SUM(A1:A2)", "\tformula", " =2+2");
        writer.flush();

        byte[] bytes = output.toByteArray();
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8)).isEqualTo(
                "plain,unsafe,quoted,nullable\r\n"
                        + "ok,'=2+2,\"a,b\"\"c\",\r\n"
                        + "'+cmd,'-1,'@SUM(A1:A2),'\tformula,' =2+2\r\n"
        );
    }

    @Test
    void doesNotTreatTypedNegativeNumbersAsFormulas() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BilibiliLiveSessionCsvWriter writer = new BilibiliLiveSessionCsvWriter(output);

        writer.writeRow(-42L, -1.5d, false);
        writer.flush();

        byte[] bytes = output.toByteArray();
        assertThat(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8))
                .isEqualTo("-42,-1.5,false\r\n");
    }

    @Test
    void neutralizesLeadingLfAndOtherControlWhitespace() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BilibiliLiveSessionCsvWriter writer = new BilibiliLiveSessionCsvWriter(output);

        writer.writeRow("\n=2+2", "\u000b@SUM(A1:A2)", "\fplain", "   \n-cmd");
        writer.flush();

        byte[] bytes = output.toByteArray();
        assertThat(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8)).isEqualTo(
                "\"'\n=2+2\",'\u000b@SUM(A1:A2),'\fplain,\"'   \n-cmd\"\r\n"
        );
    }
}
