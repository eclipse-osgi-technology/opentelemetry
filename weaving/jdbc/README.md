# OpenTelemetry JDBC Weaver

A fragment bundle attaching to the [weaving host](../opentelemetry-osgi-weaving/README.md) that instruments JDBC `Statement`, `PreparedStatement`, and `CallableStatement` implementations at class-load time.
Skips JDBC API classes themselves (packages starting with `java.sql` or `javax.sql`).

## Instrumented Methods

`execute`, `executeQuery`, `executeUpdate`, `executeBatch`, `executeLargeUpdate`, `executeLargeBatch` — standard JDBC execute methods with and without SQL string parameters.

## Generated Telemetry

### Traces (span kind: `CLIENT`)

| Attribute | Description |
|---|---|
| `db.system` | Always `jdbc` |
| `db.operation` | Method name (executeQuery, executeUpdate, …) |
| `db.statement` | SQL statement text (truncated to 1000 chars) |
| `db.jdbc.driver_class` | JDBC driver implementation class name |

Span names are derived from the SQL statement (SELECT, INSERT, UPDATE, DELETE) or fall back to `JDBC <operation>` for prepared statements without visible SQL.

### Metrics

| Metric | Type | Description |
|---|---|---|
| `db.client.operations` | Counter | Total JDBC operations |
| `db.client.duration` | Histogram | Operation duration in milliseconds |

## Components

| Class | Description |
|---|---|
| `JdbcWeaver` | `Weaver` targeting `Statement`/`PreparedStatement`/`CallableStatement` implementations |
| `JdbcClassVisitor` | ASM `ClassVisitor` identifying execute methods |
| `JdbcMethodVisitor` | ASM `AdviceAdapter` injecting instrumentation |
| `JdbcInstrumentationHelper` | Static helpers: `onExecuteEnter`, `onExecuteExit`, `onExecuteError` |
