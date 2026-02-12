package com.trdp.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static org.assertj.core.api.Assertions.*;

class TcpTransportTest {

    private ServerSocket serverSocket;
    private TcpTransport transport;

    @AfterEach
    void tearDown() throws Exception {
        if (transport != null) transport.close();
        if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
    }

    @Test
    void testConnectAndSend() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        // Accept in background
        byte[][] received = new byte[1][];
        Thread acceptThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                InputStream in = client.getInputStream();
                received[0] = in.readNBytes(5);
            } catch (IOException e) {
                // ignore
            }
        });
        acceptThread.start();

        transport = new TcpTransport("127.0.0.1", port);
        transport.send(new byte[]{1, 2, 3, 4, 5});

        acceptThread.join(3000);
        assertThat(received[0]).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void testReceive() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        // Server sends data after accepting
        Thread acceptThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                OutputStream out = client.getOutputStream();
                out.write(new byte[]{10, 20, 30});
                out.flush();
            } catch (IOException e) {
                // ignore
            }
        });
        acceptThread.start();

        transport = new TcpTransport("127.0.0.1", port);
        byte[] buffer = new byte[100];
        int len = transport.receive(buffer, 3000);

        assertThat(len).isEqualTo(3);
        assertThat(buffer[0]).isEqualTo((byte) 10);
        assertThat(buffer[1]).isEqualTo((byte) 20);
        assertThat(buffer[2]).isEqualTo((byte) 30);

        acceptThread.join(3000);
    }

    @Test
    void testReceiveTimeout() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        // Server accepts but doesn't send anything
        Thread acceptThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                Thread.sleep(5000);
            } catch (Exception e) {
                // ignore
            }
        });
        acceptThread.start();

        transport = new TcpTransport("127.0.0.1", port);
        byte[] buffer = new byte[100];
        int len = transport.receive(buffer, 200);

        assertThat(len).isEqualTo(0); // Timeout returns 0

        acceptThread.interrupt();
        acceptThread.join(3000);
    }

    @Test
    void testGetInputStream() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        Thread acceptThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                Thread.sleep(1000);
            } catch (Exception e) {
                // ignore
            }
        });
        acceptThread.start();

        transport = new TcpTransport("127.0.0.1", port);
        InputStream in = transport.getInputStream();
        assertThat(in).isNotNull();

        acceptThread.interrupt();
        acceptThread.join(3000);
    }

    @Test
    void testSetSoTimeout() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        Thread acceptThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                Thread.sleep(1000);
            } catch (Exception e) {
                // ignore
            }
        });
        acceptThread.start();

        transport = new TcpTransport("127.0.0.1", port);
        assertThatCode(() -> transport.setSoTimeout(500)).doesNotThrowAnyException();

        acceptThread.interrupt();
        acceptThread.join(3000);
    }

    @Test
    void testCloseAndIsClosed() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        Thread acceptThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                Thread.sleep(1000);
            } catch (Exception e) {
                // ignore
            }
        });
        acceptThread.start();

        transport = new TcpTransport("127.0.0.1", port);
        assertThat(transport.isClosed()).isFalse();

        transport.close();
        assertThat(transport.isClosed()).isTrue();

        acceptThread.interrupt();
        acceptThread.join(3000);
        transport = null; // Prevent double-close in tearDown
    }

    @Test
    void testSendAfterClose() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        Thread acceptThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                Thread.sleep(1000);
            } catch (Exception e) {
                // ignore
            }
        });
        acceptThread.start();

        transport = new TcpTransport("127.0.0.1", port);
        transport.close();

        assertThatThrownBy(() -> transport.send(new byte[]{1}))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("not connected");

        acceptThread.interrupt();
        acceptThread.join(3000);
        transport = null;
    }

    @Test
    void testReceiveAfterClose() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        Thread acceptThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                Thread.sleep(1000);
            } catch (Exception e) {
                // ignore
            }
        });
        acceptThread.start();

        transport = new TcpTransport("127.0.0.1", port);
        transport.close();

        assertThatThrownBy(() -> transport.receive(new byte[10], 1000))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("not connected");

        acceptThread.interrupt();
        acceptThread.join(3000);
        transport = null;
    }

    @Test
    void testGetInputStreamAfterClose() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        Thread acceptThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                Thread.sleep(1000);
            } catch (Exception e) {
                // ignore
            }
        });
        acceptThread.start();

        transport = new TcpTransport("127.0.0.1", port);
        transport.close();

        assertThatThrownBy(() -> transport.getInputStream())
            .isInstanceOf(IOException.class)
            .hasMessageContaining("not connected");

        acceptThread.interrupt();
        acceptThread.join(3000);
        transport = null;
    }

    @Test
    void testSetSoTimeoutAfterClose() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        Thread acceptThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                Thread.sleep(1000);
            } catch (Exception e) {
                // ignore
            }
        });
        acceptThread.start();

        transport = new TcpTransport("127.0.0.1", port);
        transport.close();

        assertThatThrownBy(() -> transport.setSoTimeout(500))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("not connected");

        acceptThread.interrupt();
        acceptThread.join(3000);
        transport = null;
    }

    @Test
    void testConnectionRefused() {
        assertThatThrownBy(() -> new TcpTransport("127.0.0.1", 1))
            .isInstanceOf(IOException.class);
    }
}
