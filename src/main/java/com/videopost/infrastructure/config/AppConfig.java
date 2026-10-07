package com.videopost.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.videopost.domain.model.Platform;

import java.time.Duration;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Modelo de configuração principal do VideoPost (mapeado para config.yml).
 */
public class AppConfig {

    private VideosConfig videos = new VideosConfig();
    private ScheduleConfig schedule = new ScheduleConfig();
    private Map<String, PlatformSettings> platforms = new HashMap<>();
    private int maxAttempts = 3;
    private String captionFormat = "{caption}\n\n{hashtags}";
    private String activeProfile = "default";

    public AppConfig() {
        platforms.put("tiktok", new PlatformSettings(true));
        platforms.put("facebook", new PlatformSettings(true));
        platforms.put("kwai", new PlatformSettings(true));
    }

    public static class VideosConfig {
        private String directory = "./videos";

        public VideosConfig() {}

        public VideosConfig(String directory) {
            this.directory = directory;
        }

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }
    }

    public static class ScheduleConfig {
        private String interval = "30m";
        private WorkingHoursConfig workingHours = new WorkingHoursConfig();

        public ScheduleConfig() {}

        public String getInterval() {
            return interval;
        }

        public void setInterval(String interval) {
            this.interval = interval;
        }

        public WorkingHoursConfig getWorkingHours() {
            return workingHours;
        }

        public void setWorkingHours(WorkingHoursConfig workingHours) {
            this.workingHours = workingHours;
        }

        @JsonIgnore
        public Duration parseIntervalDuration() {
            if (interval == null || interval.isBlank()) {
                return Duration.ofMinutes(30);
            }
            String trimmed = interval.trim().toLowerCase();
            try {
                if (trimmed.endsWith("m")) {
                    long minutes = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
                    return Duration.ofMinutes(minutes);
                } else if (trimmed.endsWith("h")) {
                    long hours = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
                    return Duration.ofHours(hours);
                } else if (trimmed.endsWith("s")) {
                    long seconds = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
                    return Duration.ofSeconds(seconds);
                } else {
                    return Duration.ofMinutes(Long.parseLong(trimmed));
                }
            } catch (NumberFormatException e) {
                return Duration.ofMinutes(30);
            }
        }
    }

    public static class WorkingHoursConfig {
        private boolean enabled = false;
        private String start = "08:00";
        private String end = "22:00";

        public WorkingHoursConfig() {}

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getStart() {
            return start;
        }

        public void setStart(String start) {
            this.start = start;
        }

        public String getEnd() {
            return end;
        }

        public void setEnd(String end) {
            this.end = end;
        }

        public boolean isWithinHours(LocalTime time) {
            if (!enabled) {
                return true;
            }
            try {
                LocalTime startTime = LocalTime.parse(start);
                LocalTime endTime = LocalTime.parse(end);
                return !time.isBefore(startTime) && !time.isAfter(endTime);
            } catch (Exception e) {
                return true;
            }
        }
    }

    public static class PlatformSettings {
        private boolean enabled = true;

        public PlatformSettings() {}

        public PlatformSettings(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public boolean isPlatformEnabled(Platform platform) {
        if (platform == null || platforms == null) {
            return false;
        }
        PlatformSettings settings = platforms.get(platform.name().toLowerCase());
        return settings != null && settings.isEnabled();
    }

    public void setPlatformEnabled(Platform platform, boolean enabled) {
        if (platform != null) {
            if (platforms == null) {
                platforms = new HashMap<>();
            }
            platforms.put(platform.name().toLowerCase(), new PlatformSettings(enabled));
        }
    }

    public VideosConfig getVideos() {
        return videos;
    }

    public void setVideos(VideosConfig videos) {
        this.videos = videos;
    }

    public ScheduleConfig getSchedule() {
        return schedule;
    }

    public void setSchedule(ScheduleConfig schedule) {
        this.schedule = schedule;
    }

    public Map<String, PlatformSettings> getPlatforms() {
        return platforms;
    }

    public void setPlatforms(Map<String, PlatformSettings> platforms) {
        this.platforms = platforms;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public String getCaptionFormat() {
        return captionFormat;
    }

    public void setCaptionFormat(String captionFormat) {
        this.captionFormat = captionFormat;
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public void setActiveProfile(String activeProfile) {
        this.activeProfile = activeProfile;
    }
}
