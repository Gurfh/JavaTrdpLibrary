package com.trdp.md;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;
import java.io.IOException;

class MdReplierTest {
    
    private MdReplier replier;
    
    @AfterEach
    void tearDown() {
        if (replier != null) {
            replier.close();
        }
    }
    
    @Test
    void testCreateReplier() throws IOException {
        MdRequestHandler handler = (comId, data) -> "Reply".getBytes();
        replier = new MdReplier(17227, handler);
        assertThat(replier).isNotNull();
    }
    
    @Test
    void testStartReplier() throws IOException {
        MdRequestHandler handler = (comId, data) -> "Reply".getBytes();
        replier = new MdReplier(17228, handler);
        
        assertThatCode(() -> replier.start()).doesNotThrowAnyException();
    }
}
