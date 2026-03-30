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

package org.eclipse.osgi.technology.opentelemetry.example.app.servlet;

import java.io.IOException;

import org.osgi.service.component.annotations.Component;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Simple demo servlet on http2 (port 8182).
 *
 * <ul>
 * <li>GET /bar → JSON item list</li>
 * <li>GET /bar/slow → 500ms delayed response (latency demo)</li>
 * </ul>
 */
@Component(service = jakarta.servlet.Servlet.class, immediate = true, property = {
		"osgi.http.whiteboard.servlet.pattern=/bar/*", "osgi.http.whiteboard.servlet.name=BarServlet",
		"osgi.http.whiteboard.target=(id=http2)" })
public class BarServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");

		String pathInfo = req.getPathInfo();
		if (pathInfo != null && pathInfo.startsWith("/slow")) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			resp.getWriter().write("{\"service\":\"bar\",\"mode\":\"slow\",\"duration\":500}");
		} else {
			resp.getWriter().write("{\"service\":\"bar\",\"items\":[\"bar-1\",\"bar-2\",\"bar-3\"]}");
		}
	}
}
