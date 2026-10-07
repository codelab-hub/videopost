package com.videopost.domain.repository;

import com.videopost.domain.model.Video;
import com.videopost.domain.model.VideoStatus;

import java.util.List;
import java.util.Optional;

public interface VideoRepository {

    void save(Video video);

    void update(Video video);

    Optional<Video> findById(String id);

    Optional<Video> findByFilename(String filename);

    List<Video> findAll();

    List<Video> findByStatus(VideoStatus status);

    List<Video> findPendingOrScheduled();

    long countByStatus(VideoStatus status);

    void delete(String id);
}
