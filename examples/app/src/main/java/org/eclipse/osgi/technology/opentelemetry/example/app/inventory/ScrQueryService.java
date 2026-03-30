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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;

/**
 * Passive DTO query service for the Declarative Services runtime. Reads
 * {@link ServiceComponentRuntime} DTOs to report component states. Does NOT
 * produce telemetry itself — the integration bundle
 * {@code opentelemetry-osgi-scr} handles that automatically.
 */
@Component(service = ScrQueryService.class, immediate = true)
public class ScrQueryService {

	@Reference
	private ServiceComponentRuntime scr;

	public String getComponentReport() {
		StringBuilder sb = new StringBuilder();
		Collection<ComponentDescriptionDTO> descriptions = scr.getComponentDescriptionDTOs();
		for (ComponentDescriptionDTO desc : descriptions) {
			Collection<ComponentConfigurationDTO> configs = scr.getComponentConfigurationDTOs(desc);
			for (ComponentConfigurationDTO config : configs) {
				sb.append(stateToString(config.state)).append("  ").append(desc.name).append(" (")
						.append(desc.bundle.symbolicName).append(")\n");
			}
			if (configs.isEmpty()) {
				sb.append("UNCONFIGURED  ").append(desc.name).append(" (").append(desc.bundle.symbolicName)
						.append(")\n");
			}
		}
		return sb.length() == 0 ? "No components found\n" : sb.toString();
	}

	public long getActiveCount() {
		return countByState(ComponentConfigurationDTO.ACTIVE);
	}

	public long getFailedCount() {
		return countByState(ComponentConfigurationDTO.FAILED_ACTIVATION);
	}

	public List<String> getUnsatisfiedComponents() {
		List<String> unsatisfied = new ArrayList<>();
		for (ComponentDescriptionDTO desc : scr.getComponentDescriptionDTOs()) {
			for (ComponentConfigurationDTO config : scr.getComponentConfigurationDTOs(desc)) {
				if (config.state == ComponentConfigurationDTO.UNSATISFIED_REFERENCE
						|| config.state == ComponentConfigurationDTO.UNSATISFIED_CONFIGURATION) {
					unsatisfied.add(desc.name);
				}
			}
		}
		return Collections.unmodifiableList(unsatisfied);
	}

	private long countByState(int state) {
		long count = 0;
		for (ComponentDescriptionDTO desc : scr.getComponentDescriptionDTOs()) {
			for (ComponentConfigurationDTO config : scr.getComponentConfigurationDTOs(desc)) {
				if (config.state == state) {
					count++;
				}
			}
		}
		return count;
	}

	private String stateToString(int state) {
		return switch (state) {
		case ComponentConfigurationDTO.ACTIVE -> "ACTIVE       ";
		case ComponentConfigurationDTO.SATISFIED -> "SATISFIED    ";
		case ComponentConfigurationDTO.UNSATISFIED_CONFIGURATION -> "UNSAT_CONFIG ";
		case ComponentConfigurationDTO.UNSATISFIED_REFERENCE -> "UNSAT_REF    ";
		case ComponentConfigurationDTO.FAILED_ACTIVATION -> "FAILED       ";
		default -> "UNKNOWN(" + state + ")";
		};
	}
}
