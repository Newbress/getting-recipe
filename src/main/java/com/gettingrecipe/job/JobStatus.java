package com.gettingrecipe.job;

public enum JobStatus {
    PENDING, PROCESSING, COMPLETED, FAILED;

    public boolean isActive() {
        return this == PENDING || this == PROCESSING;
    }
}
