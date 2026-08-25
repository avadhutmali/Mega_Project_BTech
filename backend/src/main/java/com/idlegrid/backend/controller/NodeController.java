package com.idlegrid.backend.controller;

import com.idlegrid.backend.dto.HeartbeatRequest;
import com.idlegrid.backend.model.Node;
import com.idlegrid.backend.store.NodeStore;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/nodes")
public class NodeController {

    private final NodeStore nodeStore;

    public NodeController(NodeStore nodeStore) {
        this.nodeStore = nodeStore;
    }

    /** Agent -> Master, every few seconds. Reports this node's current free capacity. */
    @PostMapping("/heartbeat")
    public Node heartbeat(@RequestBody HeartbeatRequest req) {
        return nodeStore.upsert(req.nodeId(), req.ip(), req.cpuFree(), req.ramFreeMb());
    }

    /** Manual/demo visibility — shows every known node and its current status. */
    @GetMapping("/summary")
    public Collection<Node> summary() {
        return nodeStore.all();
    }
}
