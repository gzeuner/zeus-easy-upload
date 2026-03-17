package com.zeus.upload.flow;

import java.util.Objects;

public class FlowConfiguration {

    private final SourceConfiguration source;
    private final TargetConfiguration target;

    public FlowConfiguration(SourceConfiguration source, TargetConfiguration target) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.target = Objects.requireNonNull(target, "target must not be null");
    }

    public SourceConfiguration getSource() {
        return source;
    }

    public TargetConfiguration getTarget() {
        return target;
    }
}
