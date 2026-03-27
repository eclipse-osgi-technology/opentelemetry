package org.eclipse.osgi.technology.opentelemetry.example.app.trigger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

/**
 * Periodically sends HTTP requests to all servlet and JAX-RS endpoints
 * to generate a continuous stream of HTTP telemetry data.
 *
 * <p>Runs every 3 seconds after a 15-second initial delay (giving
 * HTTP and JAX-RS whiteboard time to register endpoints).
 */
@Component(immediate = true)
public class EndpointPollerService {

    private static final Logger LOG = Logger.getLogger(EndpointPollerService.class.getName());

    private static final String[][] SERVLET_ENDPOINTS = {
        { "GET",  "http://localhost:8181/foo" },
        { "POST", "http://localhost:8181/foo" },
        { "GET",  "http://localhost:8182/bar" },
        { "GET",  "http://localhost:8182/bar/slow" },
        { "GET",  "http://localhost:8183/buzz" },
        { "POST", "http://localhost:8183/buzz" },
    };

    private static final String[][] JAXRS_ENDPOINTS = {
        { "GET",    "http://localhost:8181/rest1/alpha" },
        { "GET",    "http://localhost:8181/rest1/alpha/42" },
        { "POST",   "http://localhost:8181/rest1/alpha" },
        { "GET",    "http://localhost:8181/rest1/beta" },
        { "PUT",    "http://localhost:8181/rest1/beta/1" },
        { "DELETE", "http://localhost:8181/rest1/beta/1" },
        { "GET",    "http://localhost:8182/rest2/gamma" },
        { "POST",   "http://localhost:8182/rest2/gamma" },
        { "GET",    "http://localhost:8182/rest2/delta" },
        { "GET",    "http://localhost:8182/rest2/delta/ping" },
        { "GET",    "http://localhost:8181/rest1/chaos" },
    };

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private ScheduledExecutorService scheduler;

    @Activate
    void activate() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "endpoint-poller");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::pollEndpoints, 15, 3, TimeUnit.SECONDS);
        LOG.info("EndpointPollerService started — polling every 3s");
    }

    @Deactivate
    void deactivate() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        LOG.info("EndpointPollerService stopped");
    }

    private void pollEndpoints() {
        try {
            callRandomEndpoints(SERVLET_ENDPOINTS, 2);
            callRandomEndpoints(JAXRS_ENDPOINTS, 3);
        } catch (Exception e) {
            LOG.warning("Poll cycle failed: " + e.getMessage());
        }
    }

    private void callRandomEndpoints(String[][] endpoints, int count) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            String[] endpoint = endpoints[rng.nextInt(endpoints.length)];
            callEndpoint(endpoint[0], endpoint[1]);
        }
    }

    private void callEndpoint(String method, String url) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10));

            switch (method) {
                case "POST":
                    builder.POST(BodyPublishers.ofString("{\"source\":\"endpoint-poller\"}"));
                    builder.header("Content-Type", "application/json");
                    break;
                case "PUT":
                    builder.PUT(BodyPublishers.ofString("{\"source\":\"endpoint-poller\"}"));
                    builder.header("Content-Type", "application/json");
                    break;
                case "DELETE":
                    builder.DELETE();
                    break;
                default:
                    builder.GET();
                    break;
            }

            HttpResponse<String> resp = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            LOG.fine(() -> method + " " + url + " -> " + resp.statusCode());
        } catch (Exception e) {
            LOG.fine(() -> method + " " + url + " -> FAILED: " + e.getMessage());
        }
    }
}
