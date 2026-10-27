package com.idlegrid.backend.controller;

import com.idlegrid.backend.dto.HeartbeatRequest;
import com.idlegrid.backend.model.Node;
import com.idlegrid.backend.store.NodeStore;
import com.idlegrid.backend.service.StorageService;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/nodes")
public class NodeController {

    private final NodeStore nodeStore;
    private final StorageService storageService;

    public NodeController(NodeStore nodeStore, StorageService storageService) {
        this.nodeStore = nodeStore;
        this.storageService = storageService;
    }

    /** Agent -> Master, every few seconds. Reports this node's current free capacity. */
    @PostMapping("/heartbeat")
    public Node heartbeat(@RequestBody HeartbeatRequest req) {
        boolean online = !"OFFLINE".equalsIgnoreCase(req.status());
        Node node = nodeStore.upsert(req.nodeId(), req.ip(), req.sshCommand(), req.cpuTotal(), req.cpuFree(),
                req.ramTotalMb(), req.ramFreeMb(), req.diskTotalMb(), req.diskFreeMb(), online);
        storageService.refreshReplicas();
        return node;
    }

    /** Manual/demo visibility — shows every known node and its current status. */
    @GetMapping("/summary")
    public Collection<Node> summary() {
        return nodeStore.all();
    }
}
