package com.trdp.network;

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.channels.DatagramChannel;

/**
 * Native socket option access via JNA {@code setsockopt}/{@code getsockopt}.
 * <p>
 * Java provides no public API for unicast {@code IP_TTL}. This class extracts
 * the native file descriptor from {@code DatagramChannelImpl.fdVal} (one
 * reflection step) and calls the stable POSIX/Windows {@code setsockopt} syscall
 * via JNA.
 * <p>
 * Requires JVM flag: {@code --add-opens java.base/sun.nio.ch=ALL-UNNAMED}
 * for fd extraction.
 */
class NativeSocketOptions {

    private static final Logger logger = LoggerFactory.getLogger(NativeSocketOptions.class);

    private static final int IPPROTO_IP = 0;
    private static final int IP_TTL = Platform.isWindows() ? 4 : 2;

    private interface CLib extends Library {
        int setsockopt(int sockfd, int level, int optname,
                       IntByReference optval, int optlen) throws LastErrorException;

        int getsockopt(int sockfd, int level, int optname,
                       IntByReference optval, IntByReference optlen) throws LastErrorException;
    }

    private static final CLib CLIB;

    static {
        String libName = Platform.isWindows() ? "Ws2_32" : "c";
        CLIB = Native.load(libName, CLib.class);
    }

    private NativeSocketOptions() {
    }

    /**
     * Sets the unicast IP_TTL socket option via {@code setsockopt(IPPROTO_IP, IP_TTL)}.
     * <p>
     * On failure, logs a warning and degrades gracefully (unicast packets use OS default TTL).
     *
     * @param channel the datagram channel
     * @param ttl     the IP time-to-live value
     */
    static void setUnicastTtl(DatagramChannel channel, int ttl) {
        try {
            int fd = extractFd(channel);
            IntByReference optval = new IntByReference(ttl);
            CLIB.setsockopt(fd, IPPROTO_IP, IP_TTL, optval, 4);
            logger.debug("Unicast IP_TTL set to {}", ttl);
        } catch (Exception e) {
            logger.debug("Could not set unicast IP_TTL: {}. "
                    + "Unicast packets will use the OS default TTL. "
                    + "Add JVM flag: --add-opens java.base/sun.nio.ch=ALL-UNNAMED",
                    e.getMessage());
        }
    }

    /**
     * Reads the unicast IP_TTL socket option via {@code getsockopt(IPPROTO_IP, IP_TTL)}.
     *
     * @param channel the datagram channel
     * @return the current unicast TTL value, or {@code -1} on failure
     */
    static int getUnicastTtl(DatagramChannel channel) {
        try {
            int fd = extractFd(channel);
            IntByReference optval = new IntByReference();
            IntByReference optlen = new IntByReference(4);
            CLIB.getsockopt(fd, IPPROTO_IP, IP_TTL, optval, optlen);
            return optval.getValue();
        } catch (Exception e) {
            logger.debug("Could not get unicast IP_TTL: {}", e.getMessage());
            return -1;
        }
    }

    private static int extractFd(DatagramChannel channel) throws ReflectiveOperationException {
        Field fdValField = channel.getClass().getDeclaredField("fdVal");
        fdValField.setAccessible(true);
        return fdValField.getInt(channel);
    }
}
