package org.eclipse.osgi.technology.opentelemetry.example.app.trigger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.eclipse.osgi.technology.opentelemetry.example.app.inventory.HttpWhiteboardQueryService;
import org.eclipse.osgi.technology.opentelemetry.example.app.inventory.JaxRsWhiteboardQueryService;
import org.eclipse.osgi.technology.opentelemetry.example.app.inventory.ScrQueryService;
import org.eclipse.osgi.technology.opentelemetry.example.app.telemetry.ContextPropagationService;
import org.eclipse.osgi.technology.opentelemetry.example.app.telemetry.JdbcTelemetryService;
import org.eclipse.osgi.technology.opentelemetry.example.app.telemetry.LogProducerService;
import org.eclipse.osgi.technology.opentelemetry.example.app.telemetry.MetricsProducerService;
import org.eclipse.osgi.technology.opentelemetry.example.app.telemetry.TracingProducerService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Orchestration service that triggers all demo endpoints and telemetry
 * producers. Returns status-code summaries only — no internal data.
 */
@Component(service = TriggerService.class, immediate = true)
public class TriggerService {

    @Reference(cardinality = org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL,
               policy = org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC)
    private volatile MetricsProducerService metricsProducer;
    @Reference(cardinality = org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL,
               policy = org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC)
    private volatile TracingProducerService tracingProducer;
    @Reference(cardinality = org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL,
               policy = org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC)
    private volatile LogProducerService logProducer;
    @Reference(cardinality = org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL,
               policy = org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC)
    private volatile JdbcTelemetryService jdbcService;
    @Reference(cardinality = org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL,
               policy = org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC)
    private volatile ContextPropagationService contextPropagation;
    @Reference(cardinality = org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL,
               policy = org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC)
    private volatile HttpWhiteboardQueryService httpQuery;
    @Reference(cardinality = org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL,
               policy = org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC)
    private volatile JaxRsWhiteboardQueryService jaxrsQuery;
    @Reference(cardinality = org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL,
               policy = org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC)
    private volatile ScrQueryService scrQuery;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public String triggerAll() {
        StringBuilder report = new StringBuilder();
        report.append("=== Trigger All @ ").append(Instant.now()).append(" ===\n\n");
        report.append(triggerServlets());
        report.append("\n");
        report.append(triggerJaxRs());
        report.append("\n");
        report.append(triggerTelemetry());
        report.append("\n");
        report.append(triggerJdbc());
        return report.toString();
    }

    public String triggerServlets() {
        StringBuilder report = new StringBuilder("--- Servlets ---\n");
        callEndpoint("http://localhost:8181/foo", report);
        callEndpoint("http://localhost:8182/bar", report);
        callEndpoint("http://localhost:8182/bar/slow", report);
        callEndpoint("http://localhost:8183/buzz", report);
        return report.toString();
    }

    public String triggerJaxRs() {
        StringBuilder report = new StringBuilder("--- JAX-RS ---\n");
        callEndpoint("http://localhost:8181/rest1/alpha", report);
        callEndpoint("http://localhost:8181/rest1/alpha/42", report);
        callEndpoint("http://localhost:8181/rest1/beta", report);
        callEndpoint("http://localhost:8182/rest2/gamma", report);
        callEndpoint("http://localhost:8182/rest2/delta", report);
        callEndpoint("http://localhost:8182/rest2/delta/ping", report);
        return report.toString();
    }

    public String triggerTelemetry() {
        StringBuilder report = new StringBuilder("--- Telemetry ---\n");

        MetricsProducerService mp = this.metricsProducer;
        if (mp != null) {
            mp.recordRequest("trigger", "/all");
            mp.recordDuration(42.5);
            mp.incrementActive();
            mp.decrementActive();
            report.append("Metrics: 4 operations recorded\n");
        } else {
            report.append("Metrics: not available\n");
        }

        TracingProducerService tp = this.tracingProducer;
        if (tp != null) {
            tp.executeWithTracing("trigger.operation", tp::executeNestedOperation);
            tp.executeWithError("trigger.simulated-error");
            report.append("Tracing: 2 operations (1 with error)\n");
        } else {
            report.append("Tracing: not available\n");
        }

        LogProducerService lp = this.logProducer;
        if (lp != null) {
            lp.logBusinessEvent("trigger.executed", Map.of(
                    "source", "TriggerService",
                    "timestamp", Instant.now().toString()));
            lp.logWarning("Trigger warning demo");
            report.append("Logs: 2 records emitted\n");
        } else {
            report.append("Logs: not available\n");
        }

        ContextPropagationService cp = this.contextPropagation;
        if (cp != null) {
            cp.demonstratePropagation();
            report.append("Context propagation: demonstrated\n");
        } else {
            report.append("Context propagation: not available\n");
        }

        return report.toString();
    }

    public String triggerJdbc() {
        StringBuilder report = new StringBuilder("--- JDBC ---\n");
        JdbcTelemetryService jdbc = this.jdbcService;
        if (jdbc == null) {
            report.append("JDBC service not available\n");
            return report.toString();
        }
        try {
            long id = jdbc.insertEvent("trigger",
                    "Triggered at " + Instant.now());
            report.append("INSERT: id=").append(id).append("\n");

            int count = jdbc.queryEvents().size();
            report.append("SELECT: ").append(count).append(" events\n");

            int deleted = jdbc.deleteOldEvents(20);
            report.append("DELETE: ").append(deleted).append(" old events removed\n");
        } catch (Exception e) {
            report.append("ERROR: ").append(e.getMessage()).append("\n");
        }
        return report.toString();
    }

    public String getInventoryReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== HTTP Whiteboard Inventory ===\n");
        HttpWhiteboardQueryService http = this.httpQuery;
        report.append(http != null ? http.getInventoryReport() : "Not available\n");
        report.append("\n=== JAX-RS Whiteboard Inventory ===\n");
        JaxRsWhiteboardQueryService jaxrs = this.jaxrsQuery;
        report.append(jaxrs != null ? jaxrs.getInventoryReport() : "Not available\n");
        report.append("\n=== SCR Components ===\n");
        ScrQueryService scr = this.scrQuery;
        report.append(scr != null ? scr.getComponentReport() : "Not available\n");
        return report.toString();
    }

    private void callEndpoint(String url, StringBuilder report) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString());
            report.append("GET ").append(url).append(" -> ")
                  .append(resp.statusCode()).append("\n");
        } catch (Exception e) {
            report.append("GET ").append(url).append(" -> FAILED: ")
                  .append(e.getMessage()).append("\n");
        }
    }
}
