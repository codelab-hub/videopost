package com.videopost.infrastructure.filesystem;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videopost.domain.model.VideoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Parser de metadados de vídeo a partir de arquivos JSON.
 */
public class JsonMetadataParser {

    private static final Logger log = LoggerFactory.getLogger(JsonMetadataParser.class);
    private final ObjectMapper objectMapper;

    public JsonMetadataParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetadataDto(String caption, List<String> hashtags) {}

    public VideoMetadata parse(Path jsonPath) {
        if (jsonPath == null || !Files.exists(jsonPath)) {
            return VideoMetadata.empty();
        }

        try {
            MetadataDto dto = objectMapper.readValue(jsonPath.toFile(), MetadataDto.class);
            return new VideoMetadata(
                    dto.caption() != null ? dto.caption() : "",
                    dto.hashtags() != null ? dto.hashtags() : Collections.emptyList()
            );
        } catch (IOException e) {
            log.warn("Erro ao ler arquivo de metadados JSON {}: {}. Usando metadados vazios.", jsonPath, e.getMessage());
            return VideoMetadata.empty();
        }
    }
}
