package io.github.ldev22live.gdxunity.engine.core.resource;

import com.badlogic.gdx.utils.Disposable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResourceManagerTest {

    @Test
    void shouldDisposeAllRegisteredResourcesWhenDisposed() {
        ResourceManager manager = new ResourceManager();
        AtomicBoolean disposedA = new AtomicBoolean(false);
        AtomicBoolean disposedB = new AtomicBoolean(false);

        manager.register(() -> disposedA.set(true));
        manager.register(() -> disposedB.set(true));

        assertEquals(2, manager.getRegisteredCount());
        manager.dispose();

        assertTrue(disposedA.get());
        assertTrue(disposedB.get());
        assertEquals(0, manager.getRegisteredCount());
        assertTrue(manager.isDisposed());
    }

    @Test
    void shouldDisposeInReverseRegistrationOrderWhenDisposed() {
        ResourceManager manager = new ResourceManager();
        List<String> order = new ArrayList<>();

        manager.register(() -> order.add("first"));
        manager.register(() -> order.add("second"));
        manager.register(() -> order.add("third"));

        manager.dispose();

        assertEquals(List.of("third", "second", "first"), order);
    }

    @Test
    void shouldDisposeResourcesExactlyOnceEvenWithDuplicateRegistrations() {
        ResourceManager manager = new ResourceManager();
        AtomicInteger disposeCount = new AtomicInteger(0);
        Disposable shared = disposeCount::incrementAndGet;

        manager.register(shared);
        manager.register(shared); // duplicate ignored

        assertEquals(1, manager.getRegisteredCount());

        manager.dispose();
        assertEquals(1, disposeCount.get());

        // Idempotent manager disposal
        manager.dispose();
        assertEquals(1, disposeCount.get());
    }

    @Test
    void shouldContinueDisposingRemainingResourcesWhenOneThrows() {
        ResourceManager manager = new ResourceManager();
        AtomicBoolean disposedAfterFault = new AtomicBoolean(false);

        manager.register(() -> disposedAfterFault.set(true));
        manager.register(() -> {
            throw new RuntimeException("Simulated native cleanup failure");
        });

        ResourceDisposalException ex = assertThrows(ResourceDisposalException.class, manager::dispose);
        assertEquals(1, ex.getSuppressed().length);
        assertEquals("Simulated native cleanup failure", ex.getSuppressed()[0].getMessage());
        assertTrue(disposedAfterFault.get(), "Disposal of subsequent resources must continue");
        assertTrue(manager.isDisposed());
    }

    @Test
    void shouldRejectNewRegistrationsWhenAlreadyDisposed() {
        ResourceManager manager = new ResourceManager();
        manager.dispose();

        assertThrows(IllegalStateException.class, () -> manager.register(() -> {}));
    }

    @Test
    void shouldAllowUnregisteringResourcesBeforeDisposal() {
        ResourceManager manager = new ResourceManager();
        AtomicBoolean disposed = new AtomicBoolean(false);
        Disposable res = () -> disposed.set(true);

        manager.register(res);
        assertTrue(manager.unregister(res));
        assertEquals(0, manager.getRegisteredCount());

        manager.dispose();
        assertFalse(disposed.get());
    }
}
