package com.idlegrid.backend.store;

import com.idlegrid.backend.model.Node;
import com.idlegrid.backend.model.NodeStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory node registry.
 *
 * Phase 1 deliberately has no database — this is a prototype milestone.
 * All state is lost on restart, which is fine for the current demo scope.
 */
@Component
public class NodeStore {

    private final Map<String, Node> nodes = new ConcurrentHashMap<>();

    /** Called on every heartbeat. Creates the node if it's new, otherwise refreshes it. */
    public Node upsert(String id, String ip, int cpuFree, int ramFreeMb) {
        Node node = nodes.computeIfAbsent(id, k -> new Node(id, ip, cpuFree, ramFreeMb));
        node.setIp(ip);
        node.setCpuFree(cpuFree);
        node.setRamFreeMb(ramFreeMb);
        node.setLastHeartbeat(Instant.now());
        node.setStatus(NodeStatus.ONLINE);
        return node;
    }

    public Node get(String id) {
        return nodes.get(id);
    }

    public Collection<Node> all() {
        return nodes.values();
    }
}
