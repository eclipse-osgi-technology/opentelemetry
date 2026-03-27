package org.eclipse.osgi.technology.opentelemetry.weaving.jakarta.rest.test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/test")
public class TestResource {
    @GET
    public String hello() {
        return "Hello from test resource";
    }
}
