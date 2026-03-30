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
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Simple demo JAX-RS resource on REST Whiteboard 2 (rest2, port 8182).
 *
 * <ul>
 * <li>GET /gamma → counter value</li>
 * <li>POST /gamma → echo body</li>
 * </ul>
 */
@Component(service = GammaResource.class, property = { JakartarsWhiteboardConstants.JAKARTA_RS_RESOURCE + "=true", })
@Path("/gamma")
public class GammaResource {

	private final AtomicLong counter = new AtomicLong(0);

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response get() {
		return Response.ok("{\"resource\":\"gamma\",\"counter\":" + counter.incrementAndGet() + "}").build();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response post(String body) {
		return Response.ok("{\"resource\":\"gamma\",\"echo\":\"" + escapeJson(body) + "\"}").build();
	}

	private String escapeJson(String s) {
		if (s == null)
			return "";
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}
}
