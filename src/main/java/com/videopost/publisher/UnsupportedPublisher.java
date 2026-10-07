package com.videopost.publisher;

import com.videopost.domain.model.Platform;
import com.videopost.domain.model.PublishResult;
import com.videopost.domain.model.Video;

import java.util.Objects;

/**
 * Publicador substituto para plataformas sem suporte a API oficial de publicação direta aberta.
 * Garante que o sistema nunca tente scraping, contorno de CAPTCHAs ou acesso não autorizado.
 */
public class UnsupportedPublisher implements VideoPublisher {

    private final Platform platform;
    private final String reason;

    public UnsupportedPublisher(Platform platform, String reason) {
        this.platform = Objects.requireNonNull(platform, "platform cannot be null");
        this.reason = reason != null ? reason : "Plataforma não possui API oficial pública para publicação direta automatizada.";
    }

    @Override
    public Platform platform() {
        return platform;
    }

    @Override
    public boolean isAuthenticated() {
        return false;
    }

    @Override
    public PublishResult publish(Video video) {
        return PublishResult.failure(
                "Publicação em " + platform.getDisplayName() + " não suportada: " + reason,
                false
        );
    }
}
