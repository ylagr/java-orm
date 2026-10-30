package com.benchmark.common;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class BenchmarkDbHelper {

    public static final String DB_FILE = "target/benchmark_db.sqlite";

    public static SQLiteDataSource createDataSource() {
        new File("target").mkdirs();
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + DB_FILE);

        SQLiteConfig config = new SQLiteConfig();
        config.setDateClass("INTEGER");
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setBusyTimeout(5000);
        dataSource.setConfig(config);

        return dataSource;
    }

    public static void initializeDatabase(DataSource dataSource, int teacherCount, int studentCount) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS t_student;");
            stmt.execute("DROP TABLE IF EXISTS t_teacher;");

            stmt.execute("CREATE TABLE t_teacher (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "teacher_name TEXT, " +
                    "age INTEGER, " +
                    "title TEXT);");

            stmt.execute("CREATE TABLE t_student (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "student_name TEXT, " +
                    "age INTEGER, " +
                    "teacher_id INTEGER, " +
                    "is_deleted INTEGER DEFAULT 0, " +
                    "create_time INTEGER);");

            // Seed teachers
            conn.setAutoCommit(false);
            try (PreparedStatement tStmt = conn.prepareStatement(
                    "INSERT INTO t_teacher (teacher_name, age, title) VALUES (?, ?, ?)")) {
                for (int i = 1; i <= teacherCount; i++) {
                    tStmt.setString(1, "教师_" + i);
                    tStmt.setInt(2, 25 + (i % 40));
                    tStmt.setString(3, (i % 3 == 0) ? "教授" : ((i % 2 == 0) ? "副教授" : "讲师"));
                    tStmt.addBatch();
                }
                tStmt.executeBatch();
            }

            // Seed students
            long nowMillis = System.currentTimeMillis();
            try (PreparedStatement sStmt = conn.prepareStatement(
                    "INSERT INTO t_student (student_name, age, teacher_id, is_deleted, create_time) VALUES (?, ?, ?, ?, ?)")) {
                for (int i = 1; i <= studentCount; i++) {
                    sStmt.setString(1, (i % 2 == 0 ? "王" : "李") + "学生_" + i);
                    sStmt.setInt(2, 18 + (i % 10));
                    sStmt.setLong(3, (long) ((i % teacherCount) + 1));
                    sStmt.setInt(4, (i % 20 == 0) ? 1 : 0); // 每20个有1个逻辑删除
                    sStmt.setLong(5, nowMillis - i * 1000L);
                    sStmt.addBatch();
                }
                sStmt.executeBatch();
            }

            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize benchmark database", e);
        }
    }
}
