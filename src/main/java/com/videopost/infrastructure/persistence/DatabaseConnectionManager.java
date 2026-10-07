package com.videopost.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gerenciador de conexão e migrações DDL para o banco de dados SQLite local.
 */
public class DatabaseConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionManager.class);
    private final String jdbcUrl;

    public DatabaseConnectionManager(Path dbPath) {
        try {
            if (dbPath.getParent() != null) {
                Files.createDirectories(dbPath.getParent());
            }
        } catch (IOException e) {
            log.error("Erro ao criar diretório para banco SQLite: {}", e.getMessage());
        }
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        initializeSchema();
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA journal_mode = WAL;");
        }
        return conn;
    }

    private void initializeSchema() {
        String ddlVideos = """
            CREATE TABLE IF NOT EXISTS videos (
                id TEXT PRIMARY KEY,
                filename TEXT NOT NULL UNIQUE,
                path TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                scheduled_at TEXT,
                published_at TEXT,
                caption TEXT,
                hashtags TEXT
            );
        """;

        String ddlPublications = """
            CREATE TABLE IF NOT EXISTS publications (
                id TEXT PRIMARY KEY,
                video_id TEXT NOT NULL,
                platform TEXT NOT NULL,
                status TEXT NOT NULL,
                external_id TEXT,
                attempts INTEGER NOT NULL DEFAULT 0,
                error_message TEXT,
                published_at TEXT,
                last_attempt_at TEXT,
                FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE,
                UNIQUE(video_id, platform)
            );
        """;

        String indexes = """
            CREATE INDEX IF NOT EXISTS idx_videos_status ON videos(status);
            CREATE INDEX IF NOT EXISTS idx_videos_scheduled_at ON videos(scheduled_at);
            CREATE INDEX IF NOT EXISTS idx_publications_video ON publications(video_id);
            CREATE INDEX IF NOT EXISTS idx_publications_status ON publications(status);
        """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(ddlVideos);
            stmt.execute(ddlPublications);
            for (String indexSql : indexes.split(";")) {
                if (!indexSql.trim().isEmpty()) {
                    stmt.execute(indexSql.trim());
                }
            }
            log.info("Esquema SQLite inicializado com sucesso em {}", jdbcUrl);
        } catch (SQLException e) {
            log.error("Erro fatal ao inicializar esquema do banco SQLite: {}", e.getMessage(), e);
            throw new RuntimeException("Falha na inicialização do banco SQLite", e);
        }
    }
}
