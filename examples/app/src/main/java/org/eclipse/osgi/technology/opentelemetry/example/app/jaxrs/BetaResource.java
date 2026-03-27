package org.eclipse.osgi.technology.opentelemetry.example.app.jaxrs;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Simple demo JAX-RS resource on REST Whiteboard 1 (rest1, port 8181).
 *
 * <ul>
 *   <li>GET /beta → item list</li>
 *   <li>PUT /beta/{id} → update</li>
 *   <li>DELETE /beta/{id} → delete</li>
 * </ul>
 */
@Component(
    service = BetaResource.class,
    property = {
        JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE + "=true",
    }
)
@Path("/beta")
public class BetaResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list() {
        return Response.ok(
                "{\"resource\":\"beta\",\"items\":[\"b1\",\"b2\",\"b3\"]}").build();
    }

    @PUT
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") String id) {
        return Response.ok(
                "{\"resource\":\"beta\",\"id\":\"" + id + "\",\"updated\":true}").build();
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") String id) {
        return Response.ok(
                "{\"resource\":\"beta\",\"id\":\"" + id + "\",\"deleted\":true}").build();
    }
}
