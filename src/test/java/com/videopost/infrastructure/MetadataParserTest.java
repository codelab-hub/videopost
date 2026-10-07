package com.videopost.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videopost.domain.model.VideoMetadata;
import com.videopost.infrastructure.filesystem.JsonMetadataParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataParserTest {

    private final JsonMetadataParser parser = new JsonMetadataParser(new ObjectMapper());

    @Test
    @DisplayName("Deve extrair caption e hashtags de arquivo .json conforme especificação")
    void shouldParseJsonMetadataFile(@TempDir Path tempDir) throws IOException {
        String jsonContent = """
        {
          "caption": "Você sabia disso sobre o Pix? 🍓",
          "hashtags": [
            "#pix",
            "#brasil",
            "#politica"
          ]
        }
        """;

        Path jsonFile = tempDir.resolve("video-001.json");
        Files.writeString(jsonFile, jsonContent);

        VideoMetadata metadata = parser.parse(jsonFile);

        assertThat(metadata.caption()).isEqualTo("Você sabia disso sobre o Pix? 🍓");
        assertThat(metadata.hashtags()).containsExactly("#pix", "#brasil", "#politica");
        assertThat(metadata.formattedCaption()).isEqualTo("Você sabia disso sobre o Pix? 🍓\n\n#pix #brasil #politica");
    }

    @Test
    @DisplayName("Deve retornar metadados vazios de forma segura caso arquivo JSON não exista")
    void shouldReturnEmptyWhenFileNotFound() {
        Path nonExistent = Path.of("nao_existe.json");
        VideoMetadata metadata = parser.parse(nonExistent);

        assertThat(metadata.caption()).isEmpty();
        assertThat(metadata.hashtags()).isEmpty();
        assertThat(metadata.formattedCaption()).isEmpty();
    }
}
