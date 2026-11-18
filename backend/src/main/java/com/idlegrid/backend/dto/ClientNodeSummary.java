package com.idlegrid.backend.dto;

import com.idlegrid.backend.model.Node;

public record ClientNodeSummary(String id, String ip, String status, double cpuTotal,
                                double cpuFree, long ramTotalMb, long ramFreeMb,
                                long diskTotalMb, long diskFreeMb) {
    public static ClientNodeSummary from(Node node) {
        return new ClientNodeSummary(node.getId(), node.getIp(), node.getStatus().name(),
                node.getCpuTotal(), node.getCpuFree(), node.getRamTotalMb(),
                node.getRamFreeMb(), node.getDiskTotalMb(), node.getDiskFreeMb());
    }
}
