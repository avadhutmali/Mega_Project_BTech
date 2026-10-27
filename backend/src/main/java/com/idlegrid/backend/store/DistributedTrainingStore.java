package com.idlegrid.backend.store;

import com.idlegrid.backend.model.DistributedTrainingRun;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DistributedTrainingStore {
    private final Map<String, DistributedTrainingRun> runs = new ConcurrentHashMap<>();

    public void add(DistributedTrainingRun run) { runs.put(run.getId(), run); }
    public void remove(String id) { runs.remove(id); }
    public DistributedTrainingRun get(String id) { return runs.get(id); }
    public Collection<DistributedTrainingRun> all() { return runs.values(); }
}
