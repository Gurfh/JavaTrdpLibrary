package com.trdp.util;

/**
 * Shared topology validation logic per IEC 61375-2-3.
 * A zero value in either local or remote counter acts as a wildcard (always matches).
 */
public final class TrdpTopologyUtils {

    private TrdpTopologyUtils() {
    }

    /**
     * Validates that local and remote topology counters are compatible.
     *
     * @param localEtb    local ETB topology counter (0 = wildcard)
     * @param localOpTrn  local operational train topology counter (0 = wildcard)
     * @param remoteEtb   remote ETB topology counter (0 = wildcard)
     * @param remoteOpTrn remote operational train topology counter (0 = wildcard)
     * @return true if the topology is valid (counters match or either side is wildcard)
     */
    public static boolean isValid(int localEtb, int localOpTrn, int remoteEtb, int remoteOpTrn) {
        return (localEtb == 0 || remoteEtb == 0 || localEtb == remoteEtb)
            && (localOpTrn == 0 || remoteOpTrn == 0 || localOpTrn == remoteOpTrn);
    }
}
