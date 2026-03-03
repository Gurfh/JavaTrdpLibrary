package com.trdp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TrdpConfigExceptionTest {

    @Test
    void messageConstructor() {
        TrdpConfigException ex = new TrdpConfigException("test error");
        assertThat(ex.getMessage()).isEqualTo("test error");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void messageAndCauseConstructor() {
        RuntimeException cause = new RuntimeException("root cause");
        TrdpConfigException ex = new TrdpConfigException("wrapped", cause);
        assertThat(ex.getMessage()).isEqualTo("wrapped");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
