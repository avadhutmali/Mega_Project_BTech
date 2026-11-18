package com.idlegrid.backend.store;

import com.idlegrid.backend.model.FederatedTrainingRun;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FederatedTrainingStore {
    private final Map<String, FederatedTrainingRun> runs = new ConcurrentHashMap<>();

    public void add(FederatedTrainingRun run) { runs.put(run.getId(), run); }
    public FederatedTrainingRun get(String id) { return runs.get(id); }
    public Collection<FederatedTrainingRun> all() { return runs.values(); }
}
