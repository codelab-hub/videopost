package com.videopost.infrastructure;

import com.videopost.domain.model.Platform;
import com.videopost.infrastructure.config.AppConfig;
import com.videopost.infrastructure.config.YamlConfigLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigTest {

    private final YamlConfigLoader configLoader = new YamlConfigLoader();

    @Test
    @DisplayName("Deve carregar e serializar config.yml preservando propriedades")
    void shouldLoadAndSaveYamlConfig(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.yml");

        AppConfig original = new AppConfig();
        original.getVideos().setDirectory("/custom/videos");
        original.getSchedule().setInterval("45m");
        original.setActiveProfile("frutinhas");
        original.setMaxAttempts(5);
        original.setPlatformEnabled(Platform.KWAI, false);

        configLoader.save(configFile, original);

        AppConfig loaded = configLoader.load(configFile);

        assertThat(loaded.getVideos().getDirectory()).isEqualTo("/custom/videos");
        assertThat(loaded.getSchedule().getInterval()).isEqualTo("45m");
        assertThat(loaded.getActiveProfile()).isEqualTo("frutinhas");
        assertThat(loaded.getMaxAttempts()).isEqualTo(5);
        assertThat(loaded.isPlatformEnabled(Platform.KWAI)).isFalse();
        assertThat(loaded.isPlatformEnabled(Platform.TIKTOK)).isTrue();
    }

    @Test
    @DisplayName("Deve converter strings de intervalo em Duration corretamente")
    void shouldParseIntervalDurations() {
        AppConfig config = new AppConfig();

        config.getSchedule().setInterval("30m");
        assertThat(config.getSchedule().parseIntervalDuration()).isEqualTo(Duration.ofMinutes(30));

        config.getSchedule().setInterval("2h");
        assertThat(config.getSchedule().parseIntervalDuration()).isEqualTo(Duration.ofHours(2));

        config.getSchedule().setInterval("45s");
        assertThat(config.getSchedule().parseIntervalDuration()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    @DisplayName("Deve validar horário de funcionamento (Working Hours)")
    void shouldValidateWorkingHours() {
        AppConfig.WorkingHoursConfig wh = new AppConfig.WorkingHoursConfig();
        wh.setEnabled(true);
        wh.setStart("08:00");
        wh.setEnd("22:00");

        assertThat(wh.isWithinHours(LocalTime.of(8, 0))).isTrue();
        assertThat(wh.isWithinHours(LocalTime.of(12, 30))).isTrue();
        assertThat(wh.isWithinHours(LocalTime.of(22, 0))).isTrue();
        assertThat(wh.isWithinHours(LocalTime.of(7, 59))).isFalse();
        assertThat(wh.isWithinHours(LocalTime.of(22, 1))).isFalse();
    }
}
