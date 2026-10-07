package com.videopost.publisher;

import com.videopost.domain.model.Platform;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registro e fábrica de adaptadores VideoPublisher.
 * Permite adicionar novas plataformas de forma extensível sem alterar o núcleo do sistema.
 */
public class PublisherRegistry {

    private final Map<Platform, VideoPublisher> publishers = new EnumMap<>(Platform.class);

    public PublisherRegistry register(VideoPublisher publisher) {
        if (publisher != null) {
            publishers.put(publisher.platform(), publisher);
        }
        return this;
    }

    public Optional<VideoPublisher> getPublisher(Platform platform) {
        return Optional.ofNullable(publishers.get(platform));
    }

    public Map<Platform, VideoPublisher> getAllPublishers() {
        return Collections.unmodifiableMap(publishers);
    }
}
