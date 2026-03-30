package org.eclipse.osgi.technology.opentelemetry.example.app.jaxrs;

import java.util.concurrent.ThreadLocalRandom;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * JAX-RS resource that randomly returns different HTTP error status codes and
 * messages. Useful for testing error handling, alerting, and telemetry error
 * recording.
 */
@Component(service = ChaosResource.class, property = { JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE + "=true", })
@Path("/chaos")
public class ChaosResource {

	private static final int[] ERROR_CODES = { 400, 401, 403, 404, 405, 408, 409, 410, 413, 415, 418, 422, 429, 500,
			501, 502, 503, 504, 507 };

	private static final String[] MESSAGES = { "something went terribly wrong", "gremlins in the system",
			"cosmic ray bit flip detected", "the server is having a bad day", "quota exceeded for imaginary resource",
			"request rejected by the mood algorithm", "upstream hamster wheel stopped spinning",
			"the cloud is actually just someone else's broken computer", "this endpoint is on strike",
			"unexpected llama in the request pipeline", "your request was too polite, try being more assertive",
			"error 418: I'm a teapot and I refuse to brew coffee", "the database said no and walked away",
			"timeout waiting for consensus among microservices", "permission denied: you need mass_hysteria scope",
			"rate limit exceeded: slow down cowboy", "conflict: another request got here first and changed the locks",
			"payload too large: we only accept haikus", "service unavailable: currently migrating to the moon" };

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response get() {
		return randomErrorOrSuccess();
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response post() {
		return randomErrorOrSuccess();
	}

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public Response put() {
		return randomErrorOrSuccess();
	}

	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public Response delete() {
		return randomErrorOrSuccess();
	}

	private Response randomErrorOrSuccess() {
		ThreadLocalRandom rng = ThreadLocalRandom.current();

		// 30% chance of success, 70% error
		if (rng.nextInt(10) < 3) {
			return Response
					.ok("{\"resource\":\"chaos\",\"status\":\"ok\"," + "\"message\":\"you got lucky this time\"}")
					.build();
		}

		int code = ERROR_CODES[rng.nextInt(ERROR_CODES.length)];
		String message = MESSAGES[rng.nextInt(MESSAGES.length)];

		return Response.status(code).entity("{\"resource\":\"chaos\",\"status\":\"error\"," + "\"code\":" + code + ","
				+ "\"message\":\"" + message + "\"}").build();
	}
}
