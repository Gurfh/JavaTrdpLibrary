package com.trdp.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TrdpTopologyUtilsTest {

    @Test
    void bothZero_alwaysValid() {
        assertThat(TrdpTopologyUtils.isValidTopology(0, 0, 0, 0)).isTrue();
    }

    @Test
    void localZero_wildcard() {
        assertThat(TrdpTopologyUtils.isValidTopology(0, 0, 5, 10)).isTrue();
    }

    @Test
    void remoteZero_wildcard() {
        assertThat(TrdpTopologyUtils.isValidTopology(5, 10, 0, 0)).isTrue();
    }

    @Test
    void matchingCounters_valid() {
        assertThat(TrdpTopologyUtils.isValidTopology(5, 10, 5, 10)).isTrue();
    }

    @Test
    void etbMismatch_invalid() {
        assertThat(TrdpTopologyUtils.isValidTopology(5, 10, 6, 10)).isFalse();
    }

    @Test
    void opTrnMismatch_invalid() {
        assertThat(TrdpTopologyUtils.isValidTopology(5, 10, 5, 11)).isFalse();
    }

    @Test
    void bothMismatch_invalid() {
        assertThat(TrdpTopologyUtils.isValidTopology(5, 10, 6, 11)).isFalse();
    }

    @Test
    void partialWildcard_etbZeroLocal() {
        assertThat(TrdpTopologyUtils.isValidTopology(0, 10, 5, 10)).isTrue();
    }

    @Test
    void partialWildcard_opTrnZeroRemote() {
        assertThat(TrdpTopologyUtils.isValidTopology(5, 10, 5, 0)).isTrue();
    }

    @Test
    void partialWildcard_etbZeroWithOpTrnMismatch() {
        assertThat(TrdpTopologyUtils.isValidTopology(0, 10, 5, 11)).isFalse();
    }
}
