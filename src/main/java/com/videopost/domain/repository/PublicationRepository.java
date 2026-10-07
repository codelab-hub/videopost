package com.videopost.domain.repository;

import com.videopost.domain.model.Platform;
import com.videopost.domain.model.Publication;
import com.videopost.domain.model.PublicationStatus;

import java.util.List;
import java.util.Optional;

public interface PublicationRepository {

    void save(Publication publication);

    void update(Publication publication);

    Optional<Publication> findById(String id);

    List<Publication> findByVideoId(String videoId);

    Optional<Publication> findByVideoIdAndPlatform(String videoId, Platform platform);

    List<Publication> findFailed();

    long countByStatus(PublicationStatus status);

    void deleteByVideoId(String videoId);
}
