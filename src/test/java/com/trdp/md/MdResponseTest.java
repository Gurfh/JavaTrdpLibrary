package com.trdp.md;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MdResponseTest {

    @Test
    void testSingleArgConstructor() {
        byte[] data = {1, 2, 3};
        MdResponse response = new MdResponse(data);

        assertThat(response.getData()).containsExactly(1, 2, 3);
        assertThat(response.isConfirmationRequested()).isFalse();
        assertThat(response.getReplyComId()).isEqualTo(0);
    }

    @Test
    void testTwoArgConstructor() {
        byte[] data = {4, 5};
        MdResponse response = new MdResponse(data, true);

        assertThat(response.getData()).containsExactly(4, 5);
        assertThat(response.isConfirmationRequested()).isTrue();
        assertThat(response.getReplyComId()).isEqualTo(0);
    }

    @Test
    void testThreeArgConstructor() {
        byte[] data = {6, 7, 8};
        MdResponse response = new MdResponse(data, true, 9999);

        assertThat(response.getData()).containsExactly(6, 7, 8);
        assertThat(response.isConfirmationRequested()).isTrue();
        assertThat(response.getReplyComId()).isEqualTo(9999);
    }

    @Test
    void testNullData() {
        MdResponse response = new MdResponse(null);
        assertThat(response.getData()).isNull();
    }

    @Test
    void testNoConfirmation() {
        MdResponse response = new MdResponse(new byte[0], false, 100);
        assertThat(response.isConfirmationRequested()).isFalse();
        assertThat(response.getReplyComId()).isEqualTo(100);
    }
}
