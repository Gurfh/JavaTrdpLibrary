package com.trdp.pd;

import java.io.IOException;

/**
 * Handle to a publisher registered with a {@link TrdpPdSession}.
 * Provides data staging and immediate send operations.
 */
public interface PdPublisherHandle {

    /**
     * Updates the current process data without sending immediately.
     * The data will be sent on the next cyclic interval (if configured)
     * or in response to a pull request.
     *
     * @param data The process data to stage.
     * @throws IllegalArgumentException if data exceeds maximum PD data size.
     */
    void putData(byte[] data);

    /**
     * Updates the data and immediately sends it to the configured destination.
     * Does not reset the cyclic timer.
     *
     * @param data The process data to send.
     * @throws IOException If sending fails.
     * @throws IllegalArgumentException if data exceeds maximum PD data size.
     */
    void putDataImmediate(byte[] data) throws IOException;

    /**
     * Returns the ComID of this publisher.
     */
    int getComId();

    /**
     * Returns the cyclic send interval in microseconds. 0 means no cyclic send.
     */
    long getIntervalUs();

    /**
     * Returns the number of packets successfully sent.
     */
    long getPacketsSent();

    /**
     * Returns the number of send errors encountered.
     */
    long getSendErrors();

    /**
     * Resets all statistics counters to zero.
     */
    void resetStatistics();
}
