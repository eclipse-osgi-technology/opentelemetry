package org.eclipse.osgi.technology.opentelemetry.example.app.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.jakartars.runtime.JakartarsServiceRuntime;
import org.osgi.service.jakartars.runtime.dto.ApplicationDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceDTO;
import org.osgi.service.jakartars.runtime.dto.ResourceMethodInfoDTO;
import org.osgi.service.jakartars.runtime.dto.RuntimeDTO;

/**
 * Passive DTO query service for the Jakarta RS (JAX-RS) Whiteboard runtime.
 * Reads {@link JakartarsServiceRuntime} DTOs to report applications, resources,
 * and extensions. Does NOT produce telemetry itself — the integration bundle
 * {@code opentelemetry-osgi-jaxrs-whiteboard} handles that automatically.
 */
@Component(service = JaxRsWhiteboardQueryService.class, immediate = true)
public class JaxRsWhiteboardQueryService {

	private final List<JakartarsServiceRuntime> runtimes = new CopyOnWriteArrayList<>();

	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
	void bindJakartarsServiceRuntime(JakartarsServiceRuntime runtime) {
		runtimes.add(runtime);
	}

	void unbindJakartarsServiceRuntime(JakartarsServiceRuntime runtime) {
		runtimes.remove(runtime);
	}

	public String getInventoryReport() {
		StringBuilder sb = new StringBuilder();
		for (JakartarsServiceRuntime runtime : runtimes) {
			RuntimeDTO dto = runtime.getRuntimeDTO();
			sb.append("JAX-RS Runtime (service.id=").append(dto.serviceDTO.id).append(")\n");
			if (dto.defaultApplication != null) {
				appendApplication(sb, dto.defaultApplication, "  [default] ");
			}
			for (ApplicationDTO app : dto.applicationDTOs) {
				appendApplication(sb, app, "  ");
			}
		}
		return sb.length() == 0 ? "No JAX-RS runtimes found\n" : sb.toString();
	}

	private void appendApplication(StringBuilder sb, ApplicationDTO app, String prefix) {
		sb.append(prefix).append("Application: ").append(app.name).append(" [").append(app.base).append("]\n");
		for (ResourceDTO resource : app.resourceDTOs) {
			sb.append(prefix).append("  Resource: ").append(resource.name).append("\n");
			for (ResourceMethodInfoDTO method : resource.resourceMethods) {
				sb.append(prefix).append("    ").append(method.method).append(" ").append(method.path).append("\n");
			}
		}
	}

	public int getApplicationCount() {
		int count = 0;
		for (JakartarsServiceRuntime runtime : runtimes) {
			RuntimeDTO dto = runtime.getRuntimeDTO();
			count += dto.applicationDTOs.length;
			if (dto.defaultApplication != null) {
				count++;
			}
		}
		return count;
	}

	public int getResourceCount() {
		int count = 0;
		for (JakartarsServiceRuntime runtime : runtimes) {
			RuntimeDTO dto = runtime.getRuntimeDTO();
			if (dto.defaultApplication != null) {
				count += dto.defaultApplication.resourceDTOs.length;
			}
			for (ApplicationDTO app : dto.applicationDTOs) {
				count += app.resourceDTOs.length;
			}
		}
		return count;
	}

	public List<String> getResourcePaths() {
		List<String> paths = new ArrayList<>();
		for (JakartarsServiceRuntime runtime : runtimes) {
			RuntimeDTO dto = runtime.getRuntimeDTO();
			collectPaths(dto.defaultApplication, paths);
			for (ApplicationDTO app : dto.applicationDTOs) {
				collectPaths(app, paths);
			}
		}
		return Collections.unmodifiableList(paths);
	}

	private void collectPaths(ApplicationDTO app, List<String> paths) {
		if (app == null)
			return;
		for (ResourceDTO resource : app.resourceDTOs) {
			for (ResourceMethodInfoDTO method : resource.resourceMethods) {
				paths.add(method.method + " " + app.base + method.path);
			}
		}
	}
}
