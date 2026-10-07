package com.videopost.domain.model;

/**
 * Estado independente de publicação para uma plataforma específica.
 */
public enum PublicationStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}
