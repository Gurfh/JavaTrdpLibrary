package com.trdp.pd;

/**
 * Handle to a subscriber registered with a {@link TrdpPdSession}.
 * Provides status and statistics access.
 */
public interface PdSubscriberHandle {

    /**
     * Returns the ComID of this subscriber.
     */
    int getComId();

    /**
     * Returns whether this subscriber has timed out (no valid data within timeout period).
     */
    boolean isTimedOut();

    /**
     * Returns the number of missed packets detected via sequence counter gaps.
     */
    long getMissedCount();

    /**
     * Returns the number of duplicate/old packets discarded.
     */
    long getDuplicateCount();

    /**
     * Returns the number of packets discarded due to topology mismatch.
     */
    long getTopoErrorCount();

    /**
     * Resets all statistics counters to zero.
     */
    void resetStatistics();
}
