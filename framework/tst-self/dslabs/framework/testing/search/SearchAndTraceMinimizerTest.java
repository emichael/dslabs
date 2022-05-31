/*
 * Copyright (c) 2022 Ellis Michael (emichael@cs.washington.edu)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dslabs.framework.testing.search;

import dslabs.framework.Address;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.StatePredicate.PredicateResult;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.search.SearchResults.EndCondition;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.java.Log;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Log
public class SearchAndTraceMinimizerTest {
    static final Address a = new LocalAddress("a"), b = new LocalAddress("b");

    static final StateGenerator gen = StateGenerator.builder().serverSupplier(
                                                            address -> address.equals(a) ? new A() : new B())
                                                    .clientSupplier(() -> null)
                                                    .workloadSupplier(
                                                            (Workload) null)
                                                    .build();

    private SearchState initSearchState;

    @Before
    public void setupSearchTest() {
        initSearchState = new SearchState(gen);
        initSearchState.addServer(a);
        initSearchState.addServer(b);
    }

    @After
    public void teardown() {
        initSearchState = null;
    }

    @Test
    public void testMinimizeExceptionalTrace() {
        initSearchState = initSearchState.stepMessage(
                new MessageEnvelope(a, b, new Foo()), null, false);
        assert initSearchState != null;
        initSearchState = initSearchState.stepMessage(
                new MessageEnvelope(a, b, new Foo()), null, false);
        assert initSearchState != null;
        initSearchState = initSearchState.stepMessage(
                new MessageEnvelope(b, a, new Foo()), null, false);
        assert initSearchState != null;
        assertNotNull(initSearchState.thrownException());
        assertEquals(3, initSearchState.depth());

        final SearchState minimized =
                TraceMinimizer.minimizeExceptionCausingTrace(initSearchState);
        assertEquals(initSearchState, minimized);
        assertEquals(2, minimized.depth());
    }

    StatePredicate foo = StatePredicate.statePredicateWithMessage("foo", s -> {
        A sa = (A) s.server(a);
        if (sa.foo) {
            return Pair.of(false, "asdf");
        } else {
            return Pair.of(true, "1234");
        }
    });

    StatePredicate fooException =
            StatePredicate.statePredicateWithMessage("fooException", s -> {
                A sa = (A) s.server(a);
                if (sa.foo) {
                    throw new RuntimeException();
                } else {
                    return Pair.of(true, "1234");
                }
            });

    @Test
    public void testMinimizeInvariantViolatingTrace() {
        initSearchState = initSearchState.stepMessage(
                new MessageEnvelope(a, b, new Foo()), null, false);
        assert initSearchState != null;
        initSearchState = initSearchState.stepMessage(
                new MessageEnvelope(a, b, new Foo()), null, false);
        assert initSearchState != null;
        initSearchState = initSearchState.stepMessage(
                new MessageEnvelope(b, a, new Bar()), null, false);
        assert initSearchState != null;
        assertNull(initSearchState.thrownException());

        PredicateResult r = foo.test(initSearchState);
        assertEquals(r.predicate(), foo);
        assertEquals(false, r.value());
        assertEquals("asdf", r.detail());
        assertFalse(r.exceptionThrown());
        assertEquals(3, initSearchState.depth());

        final SearchState minimized =
                TraceMinimizer.minimizeTrace(initSearchState, r);
        assertEquals(initSearchState, minimized);
        assertEquals(2, minimized.depth());
    }

    @Test
    public void testMinimizeInvariantExceptionThrowingTrace() {
        initSearchState = initSearchState.stepMessage(
                new MessageEnvelope(a, b, new Foo()), null, false);
        assert initSearchState != null;
        initSearchState = initSearchState.stepMessage(
                new MessageEnvelope(a, b, new Foo()), null, false);
        assert initSearchState != null;
        initSearchState = initSearchState.stepMessage(
                new MessageEnvelope(b, a, new Bar()), null, false);
        assert initSearchState != null;
        assertNull(initSearchState.thrownException());

        PredicateResult r = fooException.test(initSearchState);
        assertEquals(r.predicate(), fooException);
        assertNull(r.value());
        assertNull(r.detail());
        assertTrue(r.exceptionThrown());
        assertEquals(3, initSearchState.depth());

        final SearchState minimized =
                TraceMinimizer.minimizeTrace(initSearchState, r);
        assertEquals(initSearchState, minimized);
        assertEquals(2, minimized.depth());
    }

    private final MessageEnvelope[] trace =
            new MessageEnvelope[]{new MessageEnvelope(a, b, new Foo()),
                    new MessageEnvelope(a, b, new Foo()),
                    new MessageEnvelope(b, a, new Bar())};

    @Test
    public void testSearchMinimizesInvariantViolation() {
        SearchSettings settings = new SearchSettings();
        settings.addInvariant(foo);
        ReplaySearch rs = new ReplaySearch(settings, trace, true);
        SearchResults r = rs.run(initSearchState);
        assertEquals(EndCondition.INVARIANT_VIOLATED, r.endCondition());
        assertNull(r.exceptionalState());
        SearchState s = r.invariantViolatingState();
        assertNotNull(s);
        PredicateResult p = r.invariantViolated();
        assertNotNull(p);

        assertEquals(p.predicate(), foo);
        assertEquals(false, p.value());
        assertEquals("asdf", p.detail());
        assertTrue(p.errorMessage().startsWith("State violates"));

        assertEquals(2, s.depth());

        rs = new ReplaySearch(settings, trace, false);
        r = rs.run(initSearchState);
        assertEquals(EndCondition.INVARIANT_VIOLATED, r.endCondition());
        assertNull(r.exceptionalState());
        s = r.invariantViolatingState();
        assertNotNull(s);
        p = r.invariantViolated();
        assertNotNull(p);

        assertEquals(p.predicate(), foo);
        assertEquals(false, p.value());
        assertEquals("asdf", p.detail());
        assertTrue(p.errorMessage().startsWith("State violates"));

        assertEquals(3, s.depth());
    }

    private final MessageEnvelope[] trace2 =
            new MessageEnvelope[]{new MessageEnvelope(a, b, new Foo()),
                    new MessageEnvelope(a, b, new Foo()),
                    new MessageEnvelope(b, a, new Foo())};

    @Test
    public void testSearchMinimizesExceptionThrown() {
        SearchSettings settings = new SearchSettings();
        settings.addInvariant(foo);
        ReplaySearch rs = new ReplaySearch(settings, trace2, true);
        SearchResults r = rs.run(initSearchState);
        assertEquals(EndCondition.EXCEPTION_THROWN, r.endCondition());
        SearchState s = r.exceptionalState();
        assertNotNull(s);
        PredicateResult p = r.invariantViolated();
        assertNull(p);
        assertEquals(2, s.depth());

        rs = new ReplaySearch(settings, trace2, false);
        r = rs.run(initSearchState);
        assertEquals(EndCondition.EXCEPTION_THROWN, r.endCondition());
        s = r.exceptionalState();
        assertNotNull(s);
        p = r.invariantViolated();
        assertNull(p);

        assertEquals(3, s.depth());
    }

    @Test
    public void testSearchMinimizesExceptionalPredicate() {
        SearchSettings settings = new SearchSettings();
        settings.addInvariant(fooException);
        ReplaySearch rs = new ReplaySearch(settings, trace, true);
        SearchResults r = rs.run(initSearchState);
        assertEquals(EndCondition.INVARIANT_VIOLATED, r.endCondition());
        assertNull(r.exceptionalState());
        SearchState s = r.invariantViolatingState();
        assertNotNull(s);
        PredicateResult p = r.invariantViolated();
        assertNotNull(p);

        assertEquals(p.predicate(), fooException);
        assertEquals(null, p.value());
        assertEquals(null, p.detail());
        assertTrue(p.exceptionThrown());
        assertTrue(p.errorMessage().startsWith("Exception thrown"));

        assertEquals(2, s.depth());

        rs = new ReplaySearch(settings, trace, false);
        r = rs.run(initSearchState);
        assertNull(r.exceptionalState());
        s = r.invariantViolatingState();
        assertNotNull(s);
        p = r.invariantViolated();
        assertNotNull(p);

        assertEquals(p.predicate(), fooException);
        assertEquals(null, p.value());
        assertEquals(null, p.detail());
        assertTrue(p.exceptionThrown());
        assertTrue(p.errorMessage().startsWith("Exception thrown"));

        assertEquals(3, s.depth());
    }

    StatePredicate alwaysException =
            StatePredicate.statePredicateWithMessage("fooException", s -> {
                throw new RuntimeException();
            });

    @Test
    public void exceptionsInGoal() {
        Logger l = LOG;
        while (null != l.getParent()) {
            l = l.getParent();
        }
        final var oldHandlers = l.getHandlers();
        for (var h : oldHandlers) {
            l.removeHandler(h);
        }
        StringBuilder sb = new StringBuilder();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                sb.append(record.getLevel());
                sb.append(record.getMessage());
            }

            @Override
            public void flush() {

            }

            @Override
            public void close() throws SecurityException {

            }
        };
        l.addHandler(handler);

        SearchSettings settings = new SearchSettings();
        settings.addGoal(alwaysException);
        ReplaySearch rs = new ReplaySearch(settings, trace, true);
        SearchResults r = rs.run(initSearchState);
        assertEquals(EndCondition.SPACE_EXHAUSTED, r.endCondition());
        assertNull(r.exceptionalState());
        SearchState s = r.invariantViolatingState();
        assertNull(s);
        PredicateResult p = r.invariantViolated();
        assertNull(s);

        String err = sb.toString();
        assertTrue(err.contains("SEVERE"));
        assertTrue(err.contains(
                "Exception thrown while evaluating \"fooException"));
        assertTrue(err.indexOf("SEVERE") != err.lastIndexOf("SEVERE"));

        l.removeHandler(handler);
        for (var h : oldHandlers) {
            l.addHandler(h);
        }
    }

    @Test
    public void exceptionsInPrune() {
        Logger l = LOG;
        while (null != l.getParent()) {
            l = l.getParent();
        }
        final var oldHandlers = l.getHandlers();
        for (var h : oldHandlers) {
            l.removeHandler(h);
        }
        StringBuilder sb = new StringBuilder();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                sb.append(record.getLevel());
                sb.append(record.getMessage());
            }

            @Override
            public void flush() {

            }

            @Override
            public void close() throws SecurityException {

            }
        };
        l.addHandler(handler);

        SearchSettings settings = new SearchSettings();
        settings.addPrune(alwaysException);
        ReplaySearch rs = new ReplaySearch(settings, trace, true);
        try {
            SearchResults r = rs.run(initSearchState);
            fail();
        } catch (RuntimeException e) {
            assertEquals("pruned", e.getMessage());
        }

        String err = sb.toString();
        assertTrue(err.contains("SEVERE"));
        assertTrue(err.contains(
                "Exception thrown while evaluating \"fooException"));
        assertTrue(err.indexOf("SEVERE") == err.lastIndexOf("SEVERE"));

        l.removeHandler(handler);
        for (var h : oldHandlers) {
            l.addHandler(h);
        }
    }

    @Test
    public void goalMinimization() {
        final StatePredicate foon = foo.negate();

        SearchSettings settings = new SearchSettings();
        settings.addGoal(foon);
        ReplaySearch rs = new ReplaySearch(settings, trace, true);
        SearchResults r = rs.run(initSearchState);
        assertEquals(EndCondition.GOAL_FOUND, r.endCondition());
        assertNull(r.exceptionalState());
        SearchState s = r.goalMatchingState();
        assertNotNull(s);
        PredicateResult p = r.goalMatched();
        assertNotNull(p);

        assertEquals(p.predicate(), foon);
        assertEquals(true, p.value());
        assertEquals("asdf", p.detail());
        assertTrue(p.errorMessage().startsWith("State matches"));

        assertEquals(2, s.depth());

        rs = new ReplaySearch(settings, trace, false);
        r = rs.run(initSearchState);
        assertEquals(EndCondition.GOAL_FOUND, r.endCondition());
        assertNull(r.exceptionalState());
        s = r.goalMatchingState();
        assertNotNull(s);
        p = r.goalMatched();
        assertNotNull(p);

        assertEquals(p.predicate(), foon);
        assertEquals(true, p.value());
        assertEquals("asdf", p.detail());
        assertTrue(p.errorMessage().startsWith("State matches"));

        assertEquals(3, s.depth());
    }
}

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
class A extends Node {
    protected A() {
        super(SearchAndTraceMinimizerTest.a);
    }

    @Override
    public void init() {
        send(new Foo(), SearchAndTraceMinimizerTest.b);
        send(new Foo(), SearchAndTraceMinimizerTest.b);
    }

    void handleFoo(Foo foo, Address sender) {
        throw new RuntimeException();
    }

    boolean foo = false;

    void handleBar(Bar bar, Address sender) {
        foo = true;
    }
}

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
class B extends Node {
    protected B() {
        super(SearchAndTraceMinimizerTest.b);
    }

    @Override
    public void init() {
    }

    void handleFoo(Foo foo, Address sender) {
        send(foo, sender);
        send(new Bar(), sender);
    }
}

@Data
class Foo implements Message {
}

@Data
class Bar implements Message {
}


class ReplaySearch extends Search {
    private SearchState initialState;
    private final MessageEnvelope[] trace;
    private final boolean shouldMinimize;

    private boolean startedReplay = false, eventsExhausted = false;


    protected ReplaySearch(SearchSettings settings, MessageEnvelope[] trace,
                           boolean shouldMinimize) {
        super(settings == null ? new SearchSettings() : settings);
        this.trace = trace;
        this.shouldMinimize = shouldMinimize;
        this.settings.singleThreaded(true);
        this.settings.outputFreqSecs(-1);
        assert !this.settings.shouldOutputStatus();
    }

    @Override
    protected void initSearch(SearchState initialState) {
        this.initialState = initialState;
    }

    @Override
    protected String searchType() {
        throw new NotImplementedException();
    }

    @Override
    protected String status(double elapsedSecs) {
        throw new NotImplementedException();
    }

    @Override
    protected boolean spaceExhausted() {
        return eventsExhausted;
    }

    @Override
    protected Runnable getWorker() {
        if (startedReplay) {
            return null;
        }
        startedReplay = true;
        return this::replayTrace;
    }

    private void replayTrace() {
        SearchState s = initialState;
        StateStatus status = checkState(s, shouldMinimize);
        // hacky way of doing this, but whatever
        if (status == StateStatus.PRUNED) {
            throw new RuntimeException("pruned");

        }
        if (status == StateStatus.TERMINAL) {
            return;
        }
        for (MessageEnvelope m : trace) {
            final SearchState prev = s;
            s = s.stepMessage(m, settings, false);
            assert s != null;

            status = checkState(s, shouldMinimize);

            // hacky way of doing this, but whatever
            if (status == StateStatus.PRUNED) {
                throw new RuntimeException("pruned");
            }

            if (status == StateStatus.TERMINAL) {
                return;
            }
        }
        eventsExhausted = true;
    }

    @Override
    protected SearchResults run(SearchState initialState) {
        return super.run(initialState);
    }
}
