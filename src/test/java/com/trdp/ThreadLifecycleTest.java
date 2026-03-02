package com.trdp;

import com.trdp.md.MdReplier;
import com.trdp.md.MdRequester;
import com.trdp.md.MdResponse;
import com.trdp.pd.TrdpPdSession;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies that close() on all threaded components completes promptly
 * (I/O resources closed before awaiting thread termination) and that
 * listener threads do not survive close().
 *
 * Before the fix, close() would block for 2-5 seconds because
 * awaitTermination() ran while threads were still blocked on socket I/O.
 */
class ThreadLifecycleTest {

    /** close() must finish well under the 2s awaitTermination timeout */
    private static final long MAX_CLOSE_MS = 1000;

    // --- TrdpPdSession ---

    @Test
    void testTrdpPdSessionClosesPromptly() throws Exception {
        TrdpPdSession session = new TrdpPdSession(0);
        session.addPublisher(9900, "127.0.0.1", 17224, 100_000);
        session.addSubscriber(9901, null, 0, new com.trdp.pd.PdEventListener() {
            @Override public void onData(com.trdp.pd.PdEvent event) {}
            @Override public void onTimeout(com.trdp.pd.PdEvent event) {}
            @Override public void onValidityRestored(com.trdp.pd.PdEvent event) {}
        });
        session.start();
        Thread.sleep(100);

        long elapsed = timedClose(session::close);

        assertThat(elapsed)
            .as("TrdpPdSession.close() should not block on socket timeout")
            .isLessThan(MAX_CLOSE_MS);
    }

    @Test
    void testTrdpPdSessionThreadsDieAfterClose() throws Exception {
        TrdpPdSession session = new TrdpPdSession(0);
        int port = session.getPort();
        session.addPublisher(9902, "127.0.0.1", 17224, 100_000);
        session.start();
        Thread.sleep(100);

        assertThat(findThreadsByPrefix("PD-Session-Recv-" + port))
            .as("Receive thread should be running before close")
            .isNotEmpty();
        assertThat(findThreadsByPrefix("PD-Session-Send-" + port))
            .as("Send thread should be running before close")
            .isNotEmpty();

        session.close();
        Thread.sleep(200);

        assertThat(findThreadsByPrefix("PD-Session-Recv-" + port))
            .as("Receive thread should be dead after close")
            .isEmpty();
        assertThat(findThreadsByPrefix("PD-Session-Send-" + port))
            .as("Send thread should be dead after close")
            .isEmpty();
    }

    // --- MdReplier ---

    @Test
    void testMdReplierClosesPromptly() throws Exception {
        MdReplier replier = new MdReplier(0, req -> new MdResponse("ok".getBytes()));
        replier.start();
        Thread.sleep(100);

        long elapsed = timedClose(replier::close);

        assertThat(elapsed)
            .as("MdReplier.close() should not block on socket timeout")
            .isLessThan(MAX_CLOSE_MS);
    }

    // --- MdRequester ---

    @Test
    void testMdRequesterClosesPromptly() throws Exception {
        MdRequester requester = new MdRequester(0);
        Thread.sleep(100);

        long elapsed = timedClose(requester::close);

        assertThat(elapsed)
            .as("MdRequester.close() should not block on socket timeout")
            .isLessThan(MAX_CLOSE_MS);
    }

    @Test
    void testMdRequesterListenerThreadDiesAfterClose() throws Exception {
        MdRequester requester = new MdRequester(0);
        Thread.sleep(100);

        assertThat(findThreadsByPrefix("MD-Requester-Listener"))
            .as("UDP listener thread should be running before close")
            .isNotEmpty();

        requester.close();
        Thread.sleep(200);

        assertThat(findThreadsByPrefix("MD-Requester-Listener"))
            .as("Listener thread should be dead after close")
            .isEmpty();
    }

    // --- MdRequester TCP Evictor ---

    @Test
    void testMdRequesterEvictorThreadDiesAfterClose() throws Exception {
        MdRequester requester = new MdRequester(0);
        Thread.sleep(100);

        assertThat(findThreadsByPrefix("MD-Requester-TCP-Evictor"))
            .as("Evictor thread should be running before close")
            .isNotEmpty();

        requester.close();
        Thread.sleep(200);

        assertThat(findThreadsByPrefix("MD-Requester-TCP-Evictor"))
            .as("Evictor thread should be dead after close")
            .isEmpty();
    }

    // --- MdReplier Confirm Timeout Checker ---

    @Test
    void testMdReplierConfirmCheckerDiesAfterClose() throws Exception {
        MdReplier replier = new MdReplier(0, req -> new MdResponse("ok".getBytes()));
        replier.start();
        Thread.sleep(100);

        assertThat(findThreadsByPrefix("MD-Replier-Confirm-Timeout"))
            .as("Confirm timeout checker should be running before close")
            .isNotEmpty();

        replier.close();
        Thread.sleep(200);

        assertThat(findThreadsByPrefix("MD-Replier-Confirm-Timeout"))
            .as("Confirm timeout checker should be dead after close")
            .isEmpty();
    }

    // --- Helpers ---

    private static long timedClose(Runnable closeAction) {
        long start = System.nanoTime();
        closeAction.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static Set<String> findThreadsByPrefix(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
            .map(Thread::getName)
            .filter(name -> name.startsWith(prefix))
            .collect(Collectors.toSet());
    }
}
