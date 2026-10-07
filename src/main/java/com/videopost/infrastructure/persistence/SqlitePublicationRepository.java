package com.videopost.infrastructure.persistence;

import com.videopost.domain.model.Platform;
import com.videopost.domain.model.Publication;
import com.videopost.domain.model.PublicationStatus;
import com.videopost.domain.repository.PublicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementação SQLite do PublicationRepository via JDBC.
 */
public class SqlitePublicationRepository implements PublicationRepository {

    private static final Logger log = LoggerFactory.getLogger(SqlitePublicationRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public SqlitePublicationRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void save(Publication publication) {
        String sql = """
            INSERT INTO publications (id, video_id, platform, status, external_id, attempts, error_message, published_at, last_attempt_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindPublicationParameters(ps, publication);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Erro ao salvar publicação id={}: {}", publication.getId(), e.getMessage());
            throw new RuntimeException("Erro ao persistir publicação no SQLite", e);
        }
    }

    @Override
    public void update(Publication publication) {
        String sql = """
            UPDATE publications
            SET status = ?, external_id = ?, attempts = ?, error_message = ?, published_at = ?, last_attempt_at = ?
            WHERE id = ?
        """;

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, publication.getStatus().name());
            ps.setString(2, publication.getExternalId());
            ps.setInt(3, publication.getAttempts());
            ps.setString(4, publication.getErrorMessage());
            ps.setString(5, publication.getPublishedAt() != null ? publication.getPublishedAt().toString() : null);
            ps.setString(6, publication.getLastAttemptAt() != null ? publication.getLastAttemptAt().toString() : null);
            ps.setString(7, publication.getId());

            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Erro ao atualizar publicação id={}: {}", publication.getId(), e.getMessage());
            throw new RuntimeException("Erro ao atualizar publicação no SQLite", e);
        }
    }

    @Override
    public Optional<Publication> findById(String id) {
        String sql = "SELECT * FROM publications WHERE id = ?";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToPublication(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Erro ao buscar publicação por id {}: {}", id, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<Publication> findByVideoId(String videoId) {
        String sql = "SELECT * FROM publications WHERE video_id = ? ORDER BY platform ASC";
        List<Publication> list = new ArrayList<>();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, videoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToPublication(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Erro ao listar publicações por video_id {}: {}", videoId, e.getMessage());
        }
        return list;
    }

    @Override
    public Optional<Publication> findByVideoIdAndPlatform(String videoId, Platform platform) {
        String sql = "SELECT * FROM publications WHERE video_id = ? AND platform = ?";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, videoId);
            ps.setString(2, platform.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToPublication(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Erro ao buscar publicação por video_id {} e platform {}: {}", videoId, platform, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<Publication> findFailed() {
        String sql = "SELECT * FROM publications WHERE status = 'FAILED' ORDER BY last_attempt_at DESC";
        List<Publication> list = new ArrayList<>();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToPublication(rs));
            }
        } catch (SQLException e) {
            log.error("Erro ao listar publicações falhas: {}", e.getMessage());
        }
        return list;
    }

    @Override
    public long countByStatus(PublicationStatus status) {
        String sql = "SELECT COUNT(*) FROM publications WHERE status = ?";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("Erro ao contar publicações por status {}: {}", status, e.getMessage());
        }
        return 0;
    }

    @Override
    public void deleteByVideoId(String videoId) {
        String sql = "DELETE FROM publications WHERE video_id = ?";
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, videoId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Erro ao deletar publicações do video_id {}: {}", videoId, e.getMessage());
        }
    }

    private void bindPublicationParameters(PreparedStatement ps, Publication pub) throws SQLException {
        ps.setString(1, pub.getId());
        ps.setString(2, pub.getVideoId());
        ps.setString(3, pub.getPlatform().name());
        ps.setString(4, pub.getStatus().name());
        ps.setString(5, pub.getExternalId());
        ps.setInt(6, pub.getAttempts());
        ps.setString(7, pub.getErrorMessage());
        ps.setString(8, pub.getPublishedAt() != null ? pub.getPublishedAt().toString() : null);
        ps.setString(9, pub.getLastAttemptAt() != null ? pub.getLastAttemptAt().toString() : null);
    }

    private Publication mapResultSetToPublication(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String videoId = rs.getString("video_id");
        Platform platform = Platform.valueOf(rs.getString("platform"));
        PublicationStatus status = PublicationStatus.valueOf(rs.getString("status"));
        String externalId = rs.getString("external_id");
        int attempts = rs.getInt("attempts");
        String errorMessage = rs.getString("error_message");

        String publishedStr = rs.getString("published_at");
        Instant publishedAt = publishedStr != null ? Instant.parse(publishedStr) : null;

        String lastAttemptStr = rs.getString("last_attempt_at");
        Instant lastAttemptAt = lastAttemptStr != null ? Instant.parse(lastAttemptStr) : null;

        return new Publication(id, videoId, platform, status, externalId, attempts, errorMessage, publishedAt, lastAttemptAt);
    }
}
