package com.idlegrid.agent.eviction;

import com.idlegrid.agent.idle.NodeState;
import com.idlegrid.agent.job.JobExecutor;

import java.util.logging.Logger;

/**
 * Reacts to {@link NodeState} transitions and evicts any running job the instant
 * the user returns from idle (IDLE → ACTIVE).
 *
 * <h3>Design (Step 9)</h3>
 * This class is a thin bridge between the idle detection loop and the job executor.
 * Its only responsibility is to recognise the IDLE → ACTIVE transition and delegate
 * the actual eviction to {@link JobExecutor#evict()}.
 *
 * <p>The actual {@code docker kill} call and Master notification happen inside
 * {@code JobExecutor.evict()} — keeping all container lifecycle logic in one place.
 *
 * <h3>Threading</h3>
 * {@link NodeState#setState} calls listeners synchronously on the idle-check thread.
 * To avoid blocking the idle checker (which is a scheduled task on a shared pool),
 * we hand off the eviction to a dedicated daemon thread and return immediately.
 *
 * <h3>ACTIVE → IDLE transitions</h3>
 * We ignore these here.  The heartbeat payload already carries {@code idleState}
 * so the Master learns about IDLE transitions automatically without any extra call.
 */
public class EvictionManager implements NodeState.StateListener {

    private static final Logger LOG = Logger.getLogger(EvictionManager.class.getName());

    private final JobExecutor executor;

    public EvictionManager(JobExecutor executor) {
        this.executor = executor;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // StateListener implementation
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Called synchronously by {@link NodeState} on every state transition.
     *
     * <p>We only care about IDLE → ACTIVE (user returning to session).
     * When that happens, we spawn a daemon thread to kill the running container
     * so the idle-check scheduler is not delayed.
     */
    @Override
    public void onStateChanged(NodeState.State previous, NodeState.State current) {
        if (previous == NodeState.State.IDLE && current == NodeState.State.ACTIVE) {
            LOG.info("IDLE → ACTIVE detected — scheduling eviction");

            Thread evictionThread = new Thread(executor::evict, "eviction-thread");
            evictionThread.setDaemon(true);
            evictionThread.start();
            // Return immediately — let the eviction thread do the heavy lifting
        }
        // ACTIVE → IDLE: no action needed here (heartbeat carries the state)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Registration
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Registers this eviction manager as a listener on the given {@link NodeState}.
     * Call this before starting any scheduled loops to ensure no transitions are missed.
     */
    public void register(NodeState nodeState) {
        nodeState.addListener(this);
        LOG.info("EvictionManager registered on NodeState");
    }
}
