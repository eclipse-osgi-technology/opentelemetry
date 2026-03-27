package org.eclipse.osgi.technology.opentelemetry.weaving.jakarta.servlet.test;

import java.io.IOException;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component(
    service = Servlet.class,
    property = {
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/test",
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME + "=TestServlet"
    }
)
public class TestServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(200);
        resp.getWriter().write("OK");
    }
}
