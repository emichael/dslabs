package dslabs.primarybackup;

import dslabs.framework.Address;
import dslabs.framework.Message;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.TimerEnvelope;
import dslabs.framework.testing.junit.DSLabsTestRunner;
import dslabs.framework.testing.junit.PrettyTestName;
import dslabs.framework.testing.junit.TestPointValue;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.Objects;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import static dslabs.framework.testing.junit.BaseJUnitTest.server;
import static dslabs.primarybackup.ViewServer.STARTUP_VIEWNUM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(DSLabsTestRunner.class)
public class ViewServerTest {
    static final Address VSA = new LocalAddress("viewserver"), TA =
            new LocalAddress("testserver");

    static final int INITIAL_VIEWNUM;

    static {
        try {
            Field iv = ViewServer.class.getDeclaredField("INITIAL_VIEWNUM");
            iv.setAccessible(true);
            INITIAL_VIEWNUM = iv.getInt(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private ViewServer vs;
    private LinkedList<MessageEnvelope> messages;
    private LinkedList<TimerEnvelope> timers;

    @Before
    public void setup() throws NoSuchMethodException, InvocationTargetException,
            IllegalAccessException {
        vs = new ViewServer(VSA);

        messages = new LinkedList<>();
        timers = new LinkedList<>();

        // TODO: clone messages and timers!!!

        vs.config(me -> messages
                        .add(new MessageEnvelope(me.getLeft(), me.getMiddle(),
                                me.getRight())), null, te -> timers
                        .add(new TimerEnvelope(te.getLeft(), te.getMiddle(),
                                te.getRight().getLeft(), te.getRight().getRight())),
                null, true);

        vs.init();
    }

    private void timeout() {
        assertFalse(timers.isEmpty());
        TimerEnvelope te = timers.remove();
        assertTrue(te.timer() instanceof PingCheckTimer);
        vs.onTimer(te.timer(), te.to());
    }

    private void sendMessage(Message m, Address from) {
        vs.handleMessage(m, from, VSA);
    }

    private void sendPing(int viewNum, Address from) {
        sendMessage(new Ping(viewNum), from);
    }

    private View getView() {
        vs.handleMessage(new GetView(), TA, VSA);
        assertFalse(messages.isEmpty());
        MessageEnvelope me = messages.getLast();
        assertEquals(VSA, me.from());
        assertEquals(TA, me.to());
        assertTrue(me.message() instanceof ViewReply);
        ViewReply m = (ViewReply) me.message();
        return m.view();
    }

    private void check(Address primary, Address backup, Integer viewNum) {
        View v = getView();
        assertEquals("Checking view's primary", primary, v.primary());
        assertEquals("Checking view's backup", backup, v.backup());
        if (viewNum != null) {
            assertEquals("Checking view number", (int) viewNum, v.viewNum());
        }
    }

    /**
     * Sets up initial view with primary and backup. Should only be called at
     * the beginning of a test.
     *
     * @param primary
     *         the address to make primary
     * @param backup
     *         the address to make backup (possibly null)
     * @param ackView
     *         whether or not to acknowledge the view
     */
    private void setupView(Address primary, Address backup, boolean ackView) {
        sendPing(STARTUP_VIEWNUM, primary);
        check(primary, null, INITIAL_VIEWNUM);

        if (backup != null) {
            sendPing(INITIAL_VIEWNUM, primary);
            sendPing(STARTUP_VIEWNUM, backup);
            check(primary, backup, INITIAL_VIEWNUM + 1);
        }

        if (ackView) {
            if (backup == null) {
                sendPing(INITIAL_VIEWNUM, primary);
            } else {
                sendPing(INITIAL_VIEWNUM + 1, primary);
            }
        }
    }

    private void setupView(Address primary, Address backup) {
        setupView(primary, backup, false);
    }

    /**
     * Deliver 2 timers to view server, while serversSendingPings are pinging.
     *
     * @param serversSendingPings
     *         servers to send pings every interval.
     */
    private void timeoutFully(Address... serversSendingPings) {
        View current = getView();
        for (int i = 0; i < 2; i++) {
            for (Address a : serversSendingPings) {
                sendPing(current.viewNum(), a);
            }

            timeout();
        }
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Startup view")
    @TestPointValue(5)
    public void test01StartupViewCorrect() {
        check(null, null, STARTUP_VIEWNUM);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Primary initialized")
    @TestPointValue(5)
    public void test02firstPrimary() {
        setupView(server(1), null);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Backup initialized")
    @TestPointValue(5)
    public void test03FirstBackup() {
        setupView(server(1), server(2));
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Backup pings first, initialized")
    @TestPointValue(5)
    public void test04BackupPingsFirst() {
        setupView(server(1), null);
        sendPing(STARTUP_VIEWNUM, server(2));
        sendPing(INITIAL_VIEWNUM, server(1));
        check(server(1), server(2), INITIAL_VIEWNUM + 1);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Backup takes over")
    @TestPointValue(5)
    public void test05BackupTakesOver() {
        setupView(server(1), server(2), true);

        // Now, fail the primary
        sendPing(INITIAL_VIEWNUM + 1, server(2));
        check(server(1), server(2), INITIAL_VIEWNUM + 1);
        timeout();

        sendPing(INITIAL_VIEWNUM + 1, server(2));
        check(server(1), server(2), INITIAL_VIEWNUM + 1);
        timeout();

        check(server(2), null, INITIAL_VIEWNUM + 2);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Old primary becomes backup")
    @TestPointValue(5)
    public void test06OldServerBecomesBackup() {
        setupView(server(1), server(2), true);

        // Now, fail the primary
        timeoutFully(server(2));
        check(server(2), null, INITIAL_VIEWNUM + 2);

        // Acknowledge the view
        sendPing(INITIAL_VIEWNUM + 2, server(2));

        // Bring the first server back
        sendPing(INITIAL_VIEWNUM + 1, server(1));
        check(server(2), server(1), INITIAL_VIEWNUM + 3);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Idle server becomes backup")
    @TestPointValue(5)
    public void test07IdleThirdServerBecomesBackup() {
        setupView(server(1), server(2), true);
        timeoutFully(server(2), server(3));
        check(server(2), server(3), INITIAL_VIEWNUM + 2);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Wait for primary ACK")
    @TestPointValue(5)
    public void test08WaitForPrimaryAck() {
        // Make sure primary acks before adding backup
        sendPing(STARTUP_VIEWNUM, server(1));
        sendPing(STARTUP_VIEWNUM, server(2));
        check(server(1), null, INITIAL_VIEWNUM);
        sendPing(INITIAL_VIEWNUM, server(1));
        check(server(1), server(2), INITIAL_VIEWNUM + 1);
        sendPing(INITIAL_VIEWNUM, server(2));

        // Now, fail the primary; shouldn't promote backup
        timeoutFully(server(2));
        check(server(1), server(2), INITIAL_VIEWNUM + 1);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Dead backup removed")
    @TestPointValue(5)
    public void test09DeadBackupRemoved() {
        setupView(server(1), server(2), true);
        timeoutFully(server(1));
        check(server(1), null, INITIAL_VIEWNUM + 2);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Uninitialized server not made primary")
    @TestPointValue(5)
    public void test10UninitializedNotPromoted() {
        setupView(server(1), server(2), true);
        timeoutFully(server(2), server(3));
        check(server(2), server(3), INITIAL_VIEWNUM + 2);
        timeoutFully(server(3));
        check(server(2), server(3), INITIAL_VIEWNUM + 2);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Dead idle server shouldn't become backup")
    @TestPointValue(5)
    public void test11DeadServerNotMadeBackup() {
        setupView(server(1), null, false);
        sendPing(STARTUP_VIEWNUM, server(2));
        timeoutFully();
        sendPing(INITIAL_VIEWNUM, server(1));
        check(server(1), null, INITIAL_VIEWNUM);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Consecutive views have different configurations")
    @TestPointValue(5)
    public void test12NewViewNotStarted() {
        setupView(server(1), null, false);
        timeoutFully(server(1));
        check(server(1), null, INITIAL_VIEWNUM);
        timeoutFully();
        check(server(1), null, INITIAL_VIEWNUM);
        sendPing(INITIAL_VIEWNUM, server(1));
        timeoutFully(server(1));
        check(server(1), null, INITIAL_VIEWNUM);
        timeoutFully();
        check(server(1), null, INITIAL_VIEWNUM);
        sendPing(STARTUP_VIEWNUM, server(2));
        check(server(1), server(2), INITIAL_VIEWNUM + 1);
        sendPing(INITIAL_VIEWNUM + 1, server(1));
        check(server(1), server(2), INITIAL_VIEWNUM + 1);
        timeoutFully(server(1), server(2));
        check(server(1), server(2), INITIAL_VIEWNUM + 1);
        timeoutFully();
        View v = getView();
        if (Objects.equals(v.primary(), server(1)) &&
                Objects.equals(v.backup(), server(2))) {
            assertEquals(INITIAL_VIEWNUM + 1, v.viewNum());
        }
    }
}
