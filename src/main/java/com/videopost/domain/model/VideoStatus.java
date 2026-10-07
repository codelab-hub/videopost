package com.videopost.domain.model;

/**
 * Estados do ciclo de vida de um vídeo no VideoPost.
 */
public enum VideoStatus {
    PENDING,
    SCHEDULED,
    PROCESSING,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED
}
