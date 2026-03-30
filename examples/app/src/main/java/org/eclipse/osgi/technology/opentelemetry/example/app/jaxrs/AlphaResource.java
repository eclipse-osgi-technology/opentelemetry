/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 * 
 */

package org.eclipse.osgi.technology.opentelemetry.example.app.jaxrs;

import java.util.concurrent.atomic.AtomicLong;

import org.osgi.service.component.annotations.Component;
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
 * Simple demo JAX-RS resource on REST Whiteboard 1 (rest1, port 8181).
 *
 * <ul>
 * <li>GET /alpha → greeting</li>
 * <li>GET /alpha/{id} → lookup by id</li>
 * <li>POST /alpha → create item</li>
 * </ul>
 */
@Component(service = AlphaResource.class, property = { JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE + "=true", })
@Path("/alpha")
public class AlphaResource {

	private final AtomicLong counter = new AtomicLong(0);

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response list() {
		return Response.ok("{\"resource\":\"alpha\",\"value\":\"hello-alpha\",\"count\":" + counter.get() + "}")
				.build();
	}

	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getById(@PathParam("id") String id) {
		return Response.ok("{\"resource\":\"alpha\",\"id\":\"" + id + "\",\"found\":true}").build();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response create(String body) {
		long id = counter.incrementAndGet();
		return Response.status(Response.Status.CREATED)
				.entity("{\"resource\":\"alpha\",\"created\":\"" + id + "\",\"status\":\"ok\"}").build();
	}
}
