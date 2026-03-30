package org.eclipse.osgi.technology.opentelemetry.example.app.trigger;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * JAX-RS resource on REST Whiteboard 1 (rest1, port 8181) that exposes the
 * {@link TriggerService} functionality via REST endpoints.
 *
 * <ul>
 * <li>GET /trigger/all — triggers everything</li>
 * <li>GET /trigger/servlets — only servlet endpoints</li>
 * <li>GET /trigger/jaxrs — only JAX-RS endpoints</li>
 * <li>GET /trigger/telemetry — only internal telemetry</li>
 * <li>GET /trigger/jdbc — only JDBC operations</li>
 * <li>GET /trigger/inventory — DTO inventory reports</li>
 * </ul>
 */
@Component(service = TriggerResource.class, property = { JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE + "=true", })
@Path("/trigger")
public class TriggerResource {

	@Reference
	private TriggerService triggerService;

	@GET
	@Path("/all")
	@Produces(MediaType.TEXT_PLAIN)
	public Response triggerAll() {
		return Response.ok(triggerService.triggerAll()).build();
	}

	@GET
	@Path("/servlets")
	@Produces(MediaType.TEXT_PLAIN)
	public Response triggerServlets() {
		return Response.ok(triggerService.triggerServlets()).build();
	}

	@GET
	@Path("/jaxrs")
	@Produces(MediaType.TEXT_PLAIN)
	public Response triggerJaxRs() {
		return Response.ok(triggerService.triggerJaxRs()).build();
	}

	@GET
	@Path("/telemetry")
	@Produces(MediaType.TEXT_PLAIN)
	public Response triggerTelemetry() {
		return Response.ok(triggerService.triggerTelemetry()).build();
	}

	@GET
	@Path("/jdbc")
	@Produces(MediaType.TEXT_PLAIN)
	public Response triggerJdbc() {
		return Response.ok(triggerService.triggerJdbc()).build();
	}

	@GET
	@Path("/inventory")
	@Produces(MediaType.TEXT_PLAIN)
	public Response inventory() {
		return Response.ok(triggerService.getInventoryReport()).build();
	}
}
