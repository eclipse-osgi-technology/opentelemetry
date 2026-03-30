package org.eclipse.osgi.technology.opentelemetry.example.app.servlet;

import java.io.IOException;

import org.osgi.service.component.annotations.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds CORS headers on http2 (port 8182).
 */
@Component(service = Filter.class, immediate = true, property = { "osgi.http.whiteboard.filter.pattern=/*",
		"osgi.http.whiteboard.filter.name=CorsFilter", "osgi.http.whiteboard.target=(id=http2)",
		"service.ranking:Integer=100" })
public class CorsFilter implements Filter {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if (response instanceof HttpServletResponse httpResp) {
			httpResp.setHeader("Access-Control-Allow-Origin", "*");
			httpResp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
			httpResp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
		}
		chain.doFilter(request, response);
	}
}
