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

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.jakartars.whiteboard.JakartarsWhiteboardConstants;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Simple demo JAX-RS resource on REST Whiteboard 2 (rest2, port 8182).
 *
 * <ul>
 * <li>GET /delta → timestamp and random value</li>
 * <li>GET /delta/ping → pong response</li>
 * </ul>
 */
@Component(service = DeltaResource.class, property = { JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE + "=true", })
@Path("/delta")
public class DeltaResource {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response get() {
		double random = ThreadLocalRandom.current().nextDouble();
		return Response.ok("{\"resource\":\"delta\",\"timestamp\":\"" + Instant.now() + "\",\"random\":" + random + "}")
				.build();
	}

	@GET
	@Path("/ping")
	@Produces(MediaType.APPLICATION_JSON)
	public Response ping() {
		return Response.ok("{\"resource\":\"delta\",\"pong\":true}").build();
	}
}
