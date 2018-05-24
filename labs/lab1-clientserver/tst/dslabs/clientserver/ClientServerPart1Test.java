package dslabs.clientserver;

import dslabs.framework.Client;
import dslabs.framework.testing.junit.PrettyTestName;
import dslabs.framework.testing.junit.RunTests;
import dslabs.framework.testing.junit.TestPointValue;
import dslabs.framework.testing.junit.UnreliableTests;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

import static dslabs.framework.testing.StatePredicate.RESULTS_OK;
import static dslabs.kvstore.KVStoreWorkload.APPENDS_LINEARIZABLE;
import static dslabs.kvstore.KVStoreWorkload.appendDifferentKeyWorkload;
import static dslabs.kvstore.KVStoreWorkload.appendSameKeyWorkload;
import static dslabs.kvstore.KVStoreWorkload.get;
import static dslabs.kvstore.KVStoreWorkload.simpleWorkload;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class ClientServerPart1Test extends ClientServerBaseTest {

    @Test(timeout = 2 * 1000, expected = InterruptedException.class)
    @PrettyTestName("Client throws InterruptedException")
    @TestPointValue(5)
    public void test01ThrowsException() throws InterruptedException {
        final Thread mainThread = Thread.currentThread();
        Client client = runState.addClient(client(1));
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return;
            }

            mainThread.interrupt();
        }, "Interrupter").start();
        client.sendCommand(get("FOO"));
        // Should never return since the runState wasn't started
        client.getResult();
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Single client basic operations")
    @Category(RunTests.class)
    @TestPointValue(20)
    public void test02SingleClient() throws InterruptedException {
        runState.addClientWorker(client(1), simpleWorkload);

        runState.run(runSettings);

        runSettings.addInvariant(RESULTS_OK);
        assertRunInvariantsHold();
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Multi-client different key appends")
    @Category(RunTests.class)
    @TestPointValue(20)
    public void test03MultiClient() throws InterruptedException {
        int numRounds = 100, numClients = 10;

        for (int i = 1; i <= numClients; i++) {
            runState.addClientWorker(client(i),
                    appendDifferentKeyWorkload(numRounds));
        }

        runState.run(runSettings);

        runSettings.addInvariant(RESULTS_OK);
        assertRunInvariantsHold();
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Multi-client same key appends")
    @Category(RunTests.class)
    @TestPointValue(30)
    public void test04MultiClientAppends() throws InterruptedException {
        int numRounds = 5, numClients = 10;

        for (int i = 1; i <= numClients; i++) {
            runState.addClientWorker(client(i),
                    appendSameKeyWorkload(numRounds));
        }

        runState.run(runSettings);

        runSettings.addInvariant(APPENDS_LINEARIZABLE);
        assertRunInvariantsHold();
    }

    @Test(timeout = 30 * 1000)
    @PrettyTestName("Single client can finish operations")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(20)
    public void test05SingleClientFinishesUnreliable()
            throws InterruptedException {
        int numRounds = 25;

        runState.addClientWorker(client(1),
                appendDifferentKeyWorkload(numRounds));
        runSettings.networkUnreliable(true);

        runState.run(runSettings);
    }
}
