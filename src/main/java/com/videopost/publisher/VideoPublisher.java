package com.videopost.publisher;

import com.videopost.domain.model.Platform;
import com.videopost.domain.model.PublishResult;
import com.videopost.domain.model.Video;

/**
 * Interface comum para publicadores de vídeo em redes sociais.
 */
public interface VideoPublisher {

    Platform platform();

    boolean isAuthenticated();

    PublishResult publish(Video video);

}
