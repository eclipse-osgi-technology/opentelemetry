package org.eclipse.osgi.technology.opentelemetry.example.app.config;

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentConfigurationDTO;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.component.runtime.dto.UnsatisfiedReferenceDTO;

/**
 * Periodically prints SCR component status and bundle state for debugging.
 */
@Component(immediate = true)
public class DebugPrinter {

    private static final long INTERVAL_SECONDS = 15;

    @Reference
    private ServiceComponentRuntime scr;

    private ScheduledExecutorService scheduler;
    private BundleContext bundleContext;

    @Activate
    void activate(BundleContext ctx) {
        this.bundleContext = ctx;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "debug-printer");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::printStatus, 0, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Deactivate
    void deactivate() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void printStatus() {
        try {
            printScrStatus();
            printBundleStatus();
        } catch (Exception e) {
            System.out.println("DebugPrinter error: " + e.getMessage());
        }
    }

    private void printScrStatus() {
        System.out.println("========== SCR Component Status ==========");
        Collection<ComponentDescriptionDTO> descs = scr.getComponentDescriptionDTOs();
        int active = 0, problems = 0;
        for (ComponentDescriptionDTO desc : descs) {
            Collection<ComponentConfigurationDTO> configs = scr.getComponentConfigurationDTOs(desc);
            if (configs.isEmpty()) {
                System.out.printf("  %-15s %s (%s)%n", "NO-CONFIG", desc.name, desc.bundle.symbolicName);
                problems++;
                continue;
            }
            for (ComponentConfigurationDTO config : configs) {
                String state = switch (config.state) {
                    case ComponentConfigurationDTO.ACTIVE -> "ACTIVE";
                    case ComponentConfigurationDTO.SATISFIED -> "SATISFIED";
                    case ComponentConfigurationDTO.UNSATISFIED_REFERENCE -> "UNSAT_REF";
                    case ComponentConfigurationDTO.UNSATISFIED_CONFIGURATION -> "UNSAT_CFG";
                    case ComponentConfigurationDTO.FAILED_ACTIVATION -> "FAILED";
                    default -> "STATE_" + config.state;
                };
                if (config.state == ComponentConfigurationDTO.ACTIVE) {
                    active++;
                } else {
                    problems++;
                    System.out.printf("  %-15s %s (%s)%n", state, desc.name, desc.bundle.symbolicName);
                    if (config.unsatisfiedReferences != null) {
                        for (UnsatisfiedReferenceDTO ref : config.unsatisfiedReferences) {
                            System.out.printf("    UNSATISFIED: %s target=%s%n",
                                    ref.name,
                                    ref.target != null ? ref.target : "(none)");
                        }
                    }
                }
            }
        }
        System.out.printf("  --- %d ACTIVE, %d with problems ---%n", active, problems);
    }

    private void printBundleStatus() {
        System.out.println("========== Non-Active Bundles ==========");
        int total = 0, activeCount = 0, problemCount = 0;
        for (Bundle b : bundleContext.getBundles()) {
            total++;
            if (b.getState() == Bundle.ACTIVE) {
                activeCount++;
                continue;
            }
            // Fragment bundles are RESOLVED, that's normal
            if (b.getState() == Bundle.RESOLVED && b.getHeaders().get("Fragment-Host") != null) {
                continue;
            }
            problemCount++;
            String state = switch (b.getState()) {
                case Bundle.INSTALLED -> "INSTALLED";
                case Bundle.RESOLVED -> "RESOLVED";
                case Bundle.STARTING -> "STARTING";
                case Bundle.STOPPING -> "STOPPING";
                case Bundle.UNINSTALLED -> "UNINSTALLED";
                default -> "STATE_" + b.getState();
            };
            System.out.printf("  [%2d] %-12s %s%n", b.getBundleId(), state, b.getSymbolicName());
        }
        System.out.printf("  --- %d total, %d ACTIVE, %d problems ---%n", total, activeCount, problemCount);
        System.out.println("==========================================");
    }
}
