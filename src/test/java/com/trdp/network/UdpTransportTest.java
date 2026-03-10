package com.trdp.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.net.InetAddress;

class UdpTransportTest {
    
    private UdpTransport transport;
    
    @AfterEach
    void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }
    
    @Test
    void testCreateTransport() throws IOException {
        transport = new UdpTransport();
        assertThat(transport.getLocalPort()).isGreaterThan(0);
    }
    
    @Test
    void testCreateTransportWithPort() throws IOException {
        int port = 18000;
        transport = new UdpTransport(port);
        assertThat(transport.getLocalPort()).isEqualTo(port);
    }
    
    @Test
    void testSendAndReceive() throws IOException {
        int port = 18001;
        transport = new UdpTransport(port);
        
        byte[] testData = "Hello TRDP".getBytes();
        transport.send(testData, InetAddress.getLoopbackAddress(), port);
        
        byte[] buffer = new byte[1024];
        int received = transport.receive(buffer, 1000);
        
        assertThat(received).isEqualTo(testData.length);
        assertThat(buffer).startsWith(testData);
    }
    
    @Test
    void testCreateTransportWithCustomOptions() throws IOException {
        transport = new UdpTransport(0, null, 32, UdpTransport.qosToTrafficClass(5));
        assertThat(transport.getLocalPort()).isGreaterThan(0);
        assertThat(transport.getTimeToLive()).isEqualTo(32);
        assertThat(transport.getTrafficClass()).isEqualTo(UdpTransport.qosToTrafficClass(5));
    }

    @Test
    void testCreateTransportWithBindAddress() throws IOException {
        transport = new UdpTransport(0, InetAddress.getLoopbackAddress(), 64, 0);
        assertThat(transport.getLocalPort()).isGreaterThan(0);
        assertThat(transport.getTimeToLive()).isEqualTo(64);
    }

    @Test
    void testDefaultTtlIs64() throws IOException {
        transport = new UdpTransport(0);
        assertThat(transport.getTimeToLive()).isEqualTo(64);
    }

    @Test
    void testQosToTrafficClass() {
        assertThat(UdpTransport.qosToTrafficClass(0)).isEqualTo(0x00);
        assertThat(UdpTransport.qosToTrafficClass(3)).isEqualTo(0x60);
        assertThat(UdpTransport.qosToTrafficClass(5)).isEqualTo(0xA0);
        assertThat(UdpTransport.qosToTrafficClass(7)).isEqualTo(0xE0);
    }

    @Test
    void testUnicastTtlIsSet() throws IOException {
        transport = new UdpTransport(0, null, 42, 0);
        assertThat(transport.getUnicastTtl()).isEqualTo(42);
    }

    @Test
    void testReceiveTimeout() throws IOException {
        transport = new UdpTransport(18002);
        
        byte[] buffer = new byte[1024];
        long startTime = System.currentTimeMillis();
        int received = transport.receive(buffer, 500);
        long elapsed = System.currentTimeMillis() - startTime;
        
        assertThat(received).isZero();
        assertThat(elapsed).isGreaterThanOrEqualTo(500);
    }
}
