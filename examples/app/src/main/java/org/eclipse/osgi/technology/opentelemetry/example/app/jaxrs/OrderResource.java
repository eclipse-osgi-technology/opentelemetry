package org.eclipse.osgi.technology.opentelemetry.example.app.jaxrs;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.osgi.technology.opentelemetry.example.app.telemetry.OrderService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * JAX-RS resource demonstrating the end-to-end trace flow.
 *
 * <p>
 * A single POST request produces one trace with multiple spans:
 * 
 * <pre>
 * [SERVER]   servlet weaver         (HTTP request)
 *  └─[INTERNAL] JAX-RS weaver       (POST /orders)
 *     └─[INTERNAL] order.process    (OrderService — manual span)
 *        ├─ Events: received, validated, persisting, persisted, metrics.recorded, completed
 *        ├─ LOG  "Processing order"  (OTel log, correlated via traceId)
 *        ├─ METRIC  order.created    (counter)
 *        └─[CLIENT] JDBC weaver     (INSERT INTO example_events …)
 * </pre>
 */
@Component(service = OrderResource.class, property = { JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE + "=true", })
@Path("/orders")
public class OrderResource {

	@Reference
	private OrderService orderService;

	@POST
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createOrder(String body) {
		String name = extractJsonValue(body, "name", "unknown");
		String data = extractJsonValue(body, "data", "");
		try {
			Map<String, Object> order = orderService.createOrder(name, data);
			return Response.status(Response.Status.CREATED).entity(toJson(order)).build();
		} catch (SQLException e) {
			return Response.serverError().entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}").build();
		}
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response listOrders() {
		try {
			List<Map<String, Object>> orders = orderService.listOrders();
			String json = orders.stream().map(this::toJson).collect(Collectors.joining(",", "[", "]"));
			return Response.ok(json).build();
		} catch (SQLException e) {
			return Response.serverError().entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}").build();
		}
	}

	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getOrder(@PathParam("id") long id) {
		try {
			Map<String, Object> order = orderService.getOrder(id);
			if (order.isEmpty()) {
				return Response.status(Response.Status.NOT_FOUND).entity("{\"error\":\"Order not found\"}").build();
			}
			return Response.ok(toJson(order)).build();
		} catch (SQLException e) {
			return Response.serverError().entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}").build();
		}
	}

	private String toJson(Map<String, Object> map) {
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for (var entry : map.entrySet()) {
			if (!first)
				sb.append(",");
			first = false;
			sb.append("\"").append(entry.getKey()).append("\":");
			Object v = entry.getValue();
			if (v instanceof Number) {
				sb.append(v);
			} else {
				sb.append("\"").append(escapeJson(String.valueOf(v))).append("\"");
			}
		}
		return sb.append("}").toString();
	}

	private static String extractJsonValue(String json, String key, String defaultValue) {
		if (json == null)
			return defaultValue;
		String search = "\"" + key + "\"";
		int idx = json.indexOf(search);
		if (idx < 0)
			return defaultValue;
		idx = json.indexOf(':', idx + search.length());
		if (idx < 0)
			return defaultValue;
		idx = json.indexOf('"', idx + 1);
		if (idx < 0)
			return defaultValue;
		int end = json.indexOf('"', idx + 1);
		if (end < 0)
			return defaultValue;
		return json.substring(idx + 1, end);
	}

	private static String escapeJson(String s) {
		if (s == null)
			return "";
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}
}
