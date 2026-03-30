package org.eclipse.osgi.technology.opentelemetry.example.app.servlet;

import java.io.IOException;
import java.time.Instant;

import org.osgi.service.component.annotations.Component;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Simple demo servlet on http1 (port 8181).
 *
 * <ul>
 * <li>GET /foo → JSON greeting</li>
 * <li>POST /foo → echoes received body</li>
 * </ul>
 */
@Component(service = jakarta.servlet.Servlet.class, immediate = true, property = {
		"osgi.http.whiteboard.servlet.pattern=/foo/*", "osgi.http.whiteboard.servlet.name=FooServlet",
		"osgi.http.whiteboard.target=(id=http1)" })
public class FooServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		resp.getWriter()
				.write("{\"service\":\"foo\",\"message\":\"Hello from Foo\",\"timestamp\":\"" + Instant.now() + "\"}");
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String body = new String(req.getInputStream().readAllBytes(), "UTF-8");
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		resp.getWriter()
				.write("{\"service\":\"foo\",\"received\":\"" + escapeJson(body) + "\",\"status\":\"accepted\"}");
	}

	private String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}
}
