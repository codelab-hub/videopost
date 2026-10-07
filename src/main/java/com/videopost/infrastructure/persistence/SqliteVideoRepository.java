package com.videopost.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videopost.domain.model.Video;
import com.videopost.domain.model.VideoStatus;
import com.videopost.domain.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Implementação SQLite do VideoRepository via JDBC.
 */
public class SqliteVideoRepository implements VideoRepository {

    private static final Logger log = LoggerFactory.getLogger(SqliteVideoRepository.class);

    private final DatabaseConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    public SqliteVideoRepository(DatabaseConnectionManager connectionManager, ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(Video video) {
        String sql = """
            INSERT INTO videos (id, filename, path, status, created_at, scheduled_at, published_at, caption, hashtags)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindVideoParameters(ps, video);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Erro ao salvar vídeo id={}: {}", video.getId(), e.getMessage());
            throw new RuntimeException("Erro ao persistir vídeo no SQLite", e);
        }
    }

    @Override
    public void update(Video video) {
        String sql = """
            UPDATE videos
            SET filename = ?, path = ?, status = ?, scheduled_at = ?, published_at = ?, caption = ?, hashtags = ?
            WHERE id = ?
        """;

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, video.getFilename());
            ps.setString(2, video.getPath());
            ps.setString(3, video.getStatus().name());
            ps.setString(4, video.getScheduledAt() != null ? video.getScheduledAt().toString() : null);
            ps.setString(5, video.getPublishedAt() != null ? video.getPublishedAt().toString() : null);
            ps.setString(6, video.getCaption());
            ps.setString(7, serializeHashtags(video.getHashtags()));
            ps.setString(8, video.getId());

            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Erro ao atualizar vídeo id={}: {}", video.getId(), e.getMessage());
            throw new RuntimeException("Erro ao atualizar vídeo no SQLite", e);
        }
    }

    @Override
    public Optional<Video> findById(String id) {
        String sql = "SELECT * FROM videos WHERE id = ?";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToVideo(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Erro ao buscar vídeo por id {}: {}", id, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<Video> findByFilename(String filename) {
        String sql = "SELECT * FROM videos WHERE filename = ?";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filename);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToVideo(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Erro ao buscar vídeo por filename {}: {}", filename, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<Video> findAll() {
        String sql = "SELECT * FROM videos ORDER BY scheduled_at ASC, created_at ASC";
        List<Video> list = new ArrayList<>();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToVideo(rs));
            }
        } catch (SQLException e) {
            log.error("Erro ao listar todos os vídeos: {}", e.getMessage());
        }
        return list;
    }

    @Override
    public List<Video> findByStatus(VideoStatus status) {
        String sql = "SELECT * FROM videos WHERE status = ? ORDER BY scheduled_at ASC, created_at ASC";
        List<Video> list = new ArrayList<>();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToVideo(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Erro ao listar vídeos por status {}: {}", status, e.getMessage());
        }
        return list;
    }

    @Override
    public List<Video> findPendingOrScheduled() {
        String sql = "SELECT * FROM videos WHERE status IN ('PENDING', 'SCHEDULED') ORDER BY scheduled_at ASC, created_at ASC";
        List<Video> list = new ArrayList<>();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToVideo(rs));
            }
        } catch (SQLException e) {
            log.error("Erro ao listar vídeos pendentes/agendados: {}", e.getMessage());
        }
        return list;
    }

    @Override
    public long countByStatus(VideoStatus status) {
        String sql = "SELECT COUNT(*) FROM videos WHERE status = ?";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("Erro ao contar vídeos por status {}: {}", status, e.getMessage());
        }
        return 0;
    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM videos WHERE id = ?";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Erro ao deletar vídeo id {}: {}", id, e.getMessage());
        }
    }

    private void bindVideoParameters(PreparedStatement ps, Video video) throws SQLException {
        ps.setString(1, video.getId());
        ps.setString(2, video.getFilename());
        ps.setString(3, video.getPath());
        ps.setString(4, video.getStatus().name());
        ps.setString(5, video.getCreatedAt().toString());
        ps.setString(6, video.getScheduledAt() != null ? video.getScheduledAt().toString() : null);
        ps.setString(7, video.getPublishedAt() != null ? video.getPublishedAt().toString() : null);
        ps.setString(8, video.getCaption());
        ps.setString(9, serializeHashtags(video.getHashtags()));
    }

    private Video mapResultSetToVideo(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String filename = rs.getString("filename");
        String path = rs.getString("path");
        VideoStatus status = VideoStatus.valueOf(rs.getString("status"));
        Instant createdAt = Instant.parse(rs.getString("created_at"));

        String scheduledStr = rs.getString("scheduled_at");
        Instant scheduledAt = scheduledStr != null ? Instant.parse(scheduledStr) : null;

        String publishedStr = rs.getString("published_at");
        Instant publishedAt = publishedStr != null ? Instant.parse(publishedStr) : null;

        String caption = rs.getString("caption");
        List<String> hashtags = deserializeHashtags(rs.getString("hashtags"));

        return new Video(id, filename, path, status, createdAt, scheduledAt, publishedAt, caption, hashtags);
    }

    private String serializeHashtags(List<String> hashtags) {
        if (hashtags == null || hashtags.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(hashtags);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> deserializeHashtags(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
