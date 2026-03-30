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
 * Measures and logs request duration on http3 (port 8183).
 */
@Component(service = Filter.class, immediate = true, property = { "osgi.http.whiteboard.filter.pattern=/*",
		"osgi.http.whiteboard.filter.name=TimingFilter", "osgi.http.whiteboard.target=(id=http3)",
		"service.ranking:Integer=100" })
public class TimingFilter implements Filter {

	private static final Logger LOG = Logger.getLogger(TimingFilter.class.getName());

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		long start = System.currentTimeMillis();
		try {
			chain.doFilter(request, response);
		} finally {
			long duration = System.currentTimeMillis() - start;
			if (request instanceof HttpServletRequest httpReq) {
				LOG.fine(
						() -> httpReq.getMethod() + " " + httpReq.getRequestURI() + " completed in " + duration + "ms");
			}
		}
	}
}
