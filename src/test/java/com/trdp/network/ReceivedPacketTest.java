package com.trdp.network;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.*;

class ReceivedPacketTest {

    @Test
    void testDefensiveCopyInConstructor() throws Exception {
        byte[] original = {1, 2, 3, 4, 5};
        ReceivedPacket packet = new ReceivedPacket(original, 3, InetAddress.getLoopbackAddress(), 5000);

        // Constructor should copy only 'length' bytes
        assertThat(packet.getLength()).isEqualTo(3);
        assertThat(packet.getData()).containsExactly(1, 2, 3);

        // Mutating the original array should not affect the packet
        original[0] = 99;
        assertThat(packet.getData()[0]).isEqualTo((byte) 1);
    }

    @Test
    void testDefensiveCopyInGetData() throws Exception {
        byte[] original = {10, 20, 30};
        ReceivedPacket packet = new ReceivedPacket(original, 3, InetAddress.getLoopbackAddress(), 5000);

        byte[] data1 = packet.getData();
        data1[0] = 99;

        // Second call should return unmodified data
        byte[] data2 = packet.getData();
        assertThat(data2[0]).isEqualTo((byte) 10);
    }

    @Test
    void testSourceAddressAndPort() throws Exception {
        InetAddress addr = InetAddress.getByName("192.168.1.1");
        ReceivedPacket packet = new ReceivedPacket(new byte[]{1}, 1, addr, 12345);

        assertThat(packet.getSourceAddress()).isEqualTo(addr);
        assertThat(packet.getSourcePort()).isEqualTo(12345);
    }
}
