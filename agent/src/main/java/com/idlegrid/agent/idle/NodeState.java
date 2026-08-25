package com.idlegrid.agent.idle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Thread-safe state machine tracking whether the PC user is ACTIVE or IDLE.
 *
 * <h3>Transition rules (from implementation plan Step 5)</h3>
 * <ul>
 *   <li>ACTIVE → IDLE: only after idle threshold elapses (slow departure —
 *       we don't evict prematurely on a brief bathroom break).</li>
 *   <li>IDLE → ACTIVE: instantly on any input (fast return — container dies
 *       the moment the user touches the keyboard/mouse, before they notice lag).</li>
 * </ul>
 *
 * <p>Registered {@link StateListener}s are called synchronously on the thread that
 * calls {@link #setState}.  Keep listener implementations fast; hand off heavy work
 * (like {@code docker kill}) to a dedicated thread.
 */
public class NodeState {

    private static final Logger LOG = Logger.getLogger(NodeState.class.getName());

    // ──────────────────────────────────────────────────────────────────────────
    // State enum
    // ──────────────────────────────────────────────────────────────────────────

    public enum State {
        /** User is at the keyboard / active session — do NOT run jobs. */
        ACTIVE,
        /** No keyboard/mouse input past the idle threshold — safe to run jobs. */
        IDLE
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Listener interface
    // ──────────────────────────────────────────────────────────────────────────

    /** Receives notifications whenever the node transitions between states. */
    public interface StateListener {
        /**
         * @param previous state before the transition
         * @param current  state after the transition
         */
        void onStateChanged(State previous, State current);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // State storage
    // ──────────────────────────────────────────────────────────────────────────

    /** Default: start ACTIVE (safe assumption — we don't know idle time at boot). */
    private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);

    /**
     * CopyOnWriteArrayList: listeners are rarely modified but read on every idle check tick.
     * Thread-safe without locking on the read path.
     */
    private final List<StateListener> listeners = new CopyOnWriteArrayList<>();

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /** Returns the current state (non-blocking, no locking). */
    public State getState() {
        return state.get();
    }

    /** Returns {@code true} if the current state is {@link State#IDLE}. */
    public boolean isIdle() {
        return state.get() == State.IDLE;
    }

    /**
     * Attempts to transition to {@code newState}.
     * Listeners are notified only if the state actually changes.
     *
     * @param newState the desired next state
     */
    public void setState(State newState) {
        State prev = state.getAndSet(newState);
        if (prev != newState) {
            LOG.info("[NodeState] " + prev + " → " + newState);
            for (StateListener listener : listeners) {
                try {
                    listener.onStateChanged(prev, newState);
                } catch (Exception e) {
                    LOG.warning("StateListener threw exception: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Registers a listener to be notified on state transitions.
     * Call this before starting any scheduled loops.
     */
    public void addListener(StateListener listener) {
        listeners.add(listener);
    }
}
