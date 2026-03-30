# OpenTelemetry Servlet Weaver

A fragment bundle attaching to the [weaving host](../hook/README.md) that instruments `javax.servlet.http.HttpServlet` subclasses at class-load time.
Skips `javax.servlet.*` classes themselves.

## Instrumented Methods

`service`, `doGet`, `doPost`, `doPut`, `doDelete`, `doHead`, `doOptions`, `doTrace` — any method with the signature `(HttpServletRequest, HttpServletResponse)`.

## Generated Telemetry

### Traces (span kind: `SERVER`)

| Attribute | Description |
|---|---|
| `http.method` | HTTP method (GET, POST, …) |
| `http.url` | Full request URL |
| `http.query_string` | Query string (if present) |
| `http.servlet.class` | Fully qualified servlet class name |
| `http.status_code` | Response status code |

Span status is set to `ERROR` for status codes ≥ 400.
Exceptions are recorded on the span.

### Metrics

| Metric | Type | Description |
|---|---|---|
| `http.server.requests` | Counter | Total HTTP requests by status code |
| `http.server.duration` | Histogram | Request duration in milliseconds |

## Components

| Class | Description |
|---|---|
| `ServletWeaver` | `Weaver` targeting `HttpServlet` subclasses |
| `ServletClassVisitor` | ASM `ClassVisitor` identifying servlet handler methods |
| `ServletServiceMethodVisitor` | ASM `AdviceAdapter` injecting instrumentation |
| `ServletInstrumentationHelper` | Static helpers: `onServiceEnter`, `onServiceExit`, `onServiceError` |
