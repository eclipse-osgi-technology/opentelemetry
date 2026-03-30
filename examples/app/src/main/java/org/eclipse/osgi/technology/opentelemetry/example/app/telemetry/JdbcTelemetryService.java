package org.eclipse.osgi.technology.opentelemetry.example.app.telemetry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.h2.jdbcx.JdbcDataSource;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

/**
 * JDBC telemetry demo using an in-memory H2 database. All JDBC calls are
 * auto-instrumented by opentelemetry-osgi-weaver-jdbc which creates trace spans
 * for every Statement execution.
 *
 * <p>
 * Demonstrates:
 * <ul>
 * <li>INSERT, SELECT, UPDATE, DELETE operations</li>
 * <li>Automatic JDBC weaver instrumentation</li>
 * </ul>
 */
@Component(service = JdbcTelemetryService.class, immediate = true)
public class JdbcTelemetryService {

	private JdbcDataSource dataSource;

	@Activate
	void activate() throws SQLException {
		dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:otel-example;DB_CLOSE_DELAY=-1");
		try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
			stmt.execute("CREATE TABLE IF NOT EXISTS example_events (" + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
					+ "name VARCHAR(255), " + "data VARCHAR(1024), "
					+ "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
		}
	}

	@Deactivate
	void deactivate() {
		try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
			stmt.execute("DROP TABLE IF EXISTS example_events");
			stmt.execute("SHUTDOWN");
		} catch (SQLException e) {
			// ignore
		}
	}

	public long insertEvent(String name, String data) throws SQLException {
		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement("INSERT INTO example_events (name, data) VALUES (?, ?)",
						Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, name);
			ps.setString(2, data);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					return keys.getLong(1);
				}
			}
		}
		return -1;
	}

	public List<Map<String, Object>> queryEvents() throws SQLException {
		List<Map<String, Object>> results = new ArrayList<>();
		try (Connection conn = dataSource.getConnection();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT id, name, data, created_at FROM example_events ORDER BY id")) {
			while (rs.next()) {
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("id", rs.getLong("id"));
				row.put("name", rs.getString("name"));
				row.put("data", rs.getString("data"));
				row.put("created_at", rs.getTimestamp("created_at").toString());
				results.add(row);
			}
		}
		return results;
	}

	public Map<String, Object> queryById(long id) throws SQLException {
		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn
						.prepareStatement("SELECT id, name, data, created_at FROM example_events WHERE id = ?")) {
			ps.setLong(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					Map<String, Object> row = new LinkedHashMap<>();
					row.put("id", rs.getLong("id"));
					row.put("name", rs.getString("name"));
					row.put("data", rs.getString("data"));
					row.put("created_at", rs.getTimestamp("created_at").toString());
					return row;
				}
			}
		}
		return Map.of();
	}

	public int deleteOldEvents(int keepLast) throws SQLException {
		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement("DELETE FROM example_events WHERE id NOT IN "
						+ "(SELECT id FROM example_events ORDER BY id DESC LIMIT ?)")) {
			ps.setInt(1, keepLast);
			return ps.executeUpdate();
		}
	}
}
