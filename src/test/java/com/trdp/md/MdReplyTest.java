package com.trdp.md;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MdReplyTest {

    @Test
    void testConstructorAndGetters() {
        byte[] data = {10, 20, 30};
        MdReply reply = new MdReply(5000, data, 7);

        assertThat(reply.getComId()).isEqualTo(5000);
        assertThat(reply.getData()).containsExactly(10, 20, 30);
        assertThat(reply.getSequenceNumber()).isEqualTo(7);
    }

    @Test
    void testNullData() {
        MdReply reply = new MdReply(100, null, 0);
        assertThat(reply.getData()).isNull();
    }

    @Test
    void testToString() {
        MdReply reply = new MdReply(3000, new byte[]{1, 2, 3, 4, 5}, 99);
        String str = reply.toString();

        assertThat(str).contains("MdReply");
        assertThat(str).contains("3000");
        assertThat(str).contains("99");
        assertThat(str).contains("5"); // dataLen
    }

    @Test
    void testToStringWithNullData() {
        MdReply reply = new MdReply(100, null, 0);
        String str = reply.toString();
        assertThat(str).contains("dataLen=0");
    }
}
