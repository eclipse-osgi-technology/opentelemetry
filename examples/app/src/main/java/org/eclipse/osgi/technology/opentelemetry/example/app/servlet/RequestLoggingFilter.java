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
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Logs incoming HTTP requests on http1 (port 8181).
 */
@Component(service = Filter.class, immediate = true, property = { "osgi.http.whiteboard.filter.pattern=/*",
		"osgi.http.whiteboard.filter.name=RequestLoggingFilter", "osgi.http.whiteboard.target=(id=http1)",
		"service.ranking:Integer=100" })
public class RequestLoggingFilter implements Filter {

	private static final Logger LOG = Logger.getLogger(RequestLoggingFilter.class.getName());

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if (request instanceof HttpServletRequest httpReq) {
			LOG.fine(() -> httpReq.getMethod() + " " + httpReq.getRequestURI());
		}
		chain.doFilter(request, response);
	}
}
