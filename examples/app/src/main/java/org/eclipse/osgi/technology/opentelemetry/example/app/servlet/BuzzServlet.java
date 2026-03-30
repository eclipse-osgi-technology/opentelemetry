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
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.osgi.service.component.annotations.Component;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Simple demo servlet on http3 (port 8183).
 *
 * <ul>
 * <li>GET /buzz → JSON with incrementing counter</li>
 * <li>POST /buzz → echoes posted body</li>
 * </ul>
 */
@Component(service = jakarta.servlet.Servlet.class, immediate = true, property = {
		"osgi.http.whiteboard.servlet.pattern=/buzz/*", "osgi.http.whiteboard.servlet.name=BuzzServlet",
		"osgi.http.whiteboard.target=(id=http3)" })
public class BuzzServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private final AtomicLong counter = new AtomicLong(0);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		resp.getWriter().write("{\"service\":\"buzz\",\"counter\":" + counter.incrementAndGet() + ",\"timestamp\":\""
				+ Instant.now() + "\"}");
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String body = new String(req.getInputStream().readAllBytes(), "UTF-8");
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		resp.getWriter().write("{\"service\":\"buzz\",\"echo\":\"" + escapeJson(body) + "\"}");
	}

	private String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}
}
