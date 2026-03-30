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

package org.eclipse.osgi.technology.opentelemetry.example.app.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.servlet.runtime.HttpServiceRuntime;
import org.osgi.service.servlet.runtime.dto.RuntimeDTO;
import org.osgi.service.servlet.runtime.dto.ServletContextDTO;
import org.osgi.service.servlet.runtime.dto.ServletDTO;

/**
 * Passive DTO query service for the HTTP Whiteboard runtime. Reads
 * {@link HttpServiceRuntime} DTOs to report servlet contexts, servlets,
 * filters, and listeners. Does NOT produce telemetry itself — the integration
 * bundle {@code opentelemetry-osgi-http-whiteboard} handles that automatically.
 */
@Component(service = HttpWhiteboardQueryService.class, immediate = true)
public class HttpWhiteboardQueryService {

	private final List<HttpServiceRuntime> runtimes = new CopyOnWriteArrayList<>();

	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
	void bindHttpServiceRuntime(HttpServiceRuntime runtime) {
		runtimes.add(runtime);
	}

	void unbindHttpServiceRuntime(HttpServiceRuntime runtime) {
		runtimes.remove(runtime);
	}

	public String getInventoryReport() {
		StringBuilder sb = new StringBuilder();
		for (HttpServiceRuntime runtime : runtimes) {
			RuntimeDTO dto = runtime.getRuntimeDTO();
			sb.append("HTTP Runtime (service.id=").append(dto.serviceDTO.id).append(")\n");
			for (ServletContextDTO ctx : dto.servletContextDTOs) {
				sb.append("  Context: ").append(ctx.name).append(" [").append(ctx.contextPath).append("]\n");
				for (ServletDTO servlet : ctx.servletDTOs) {
					sb.append("    Servlet: ").append(servlet.name);
					if (servlet.patterns != null && servlet.patterns.length > 0) {
						sb.append(" -> ").append(String.join(", ", servlet.patterns));
					}
					sb.append("\n");
				}
			}
		}
		return sb.length() == 0 ? "No HTTP runtimes found\n" : sb.toString();
	}

	public int getContextCount() {
		int count = 0;
		for (HttpServiceRuntime runtime : runtimes) {
			count += runtime.getRuntimeDTO().servletContextDTOs.length;
		}
		return count;
	}

	public int getServletCount() {
		int count = 0;
		for (HttpServiceRuntime runtime : runtimes) {
			for (ServletContextDTO ctx : runtime.getRuntimeDTO().servletContextDTOs) {
				count += ctx.servletDTOs.length;
			}
		}
		return count;
	}

	public List<String> getServletNames() {
		List<String> names = new ArrayList<>();
		for (HttpServiceRuntime runtime : runtimes) {
			for (ServletContextDTO ctx : runtime.getRuntimeDTO().servletContextDTOs) {
				for (ServletDTO servlet : ctx.servletDTOs) {
					names.add(servlet.name);
				}
			}
		}
		return Collections.unmodifiableList(names);
	}

	public List<String> getContextPaths() {
		List<String> paths = new ArrayList<>();
		for (HttpServiceRuntime runtime : runtimes) {
			for (ServletContextDTO ctx : runtime.getRuntimeDTO().servletContextDTOs) {
				paths.add(ctx.contextPath);
			}
		}
		return Collections.unmodifiableList(paths);
	}
}
