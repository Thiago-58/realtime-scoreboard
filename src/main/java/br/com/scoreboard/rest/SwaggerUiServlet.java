package br.com.scoreboard.rest;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet(urlPatterns = {"/openapi-ui", "/openapi-ui/*", "/swagger", "/swagger/*", "/swagger-ui", "/swagger-ui/*", "/swagger-ui.html"})
public class SwaggerUiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"pt-BR\">");
        out.println("<head>");
        out.println("  <meta charset=\"UTF-8\">");
        out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        out.println("  <title>Swagger UI - Realtime Scoreboard API</title>");
        out.println("  <link rel=\"stylesheet\" type=\"text/css\" href=\"https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui.css\" />");
        out.println("  <link rel=\"icon\" type=\"image/png\" href=\"https://unpkg.com/swagger-ui-dist@5.11.0/favicon-32x32.png\" sizes=\"32x32\" />");
        out.println("  <style>");
        out.println("    html { box-sizing: border-box; overflow-y: scroll; }");
        out.println("    *, *:before, *:after { box-sizing: inherit; }");
        out.println("    body { margin: 0; background: #fafafa; font-family: sans-serif; }");
        out.println("    .topbar { background-color: #3b5323 !important; }");
        out.println("    .topbar .download-url-wrapper { display: flex !important; }");
        out.println("  </style>");
        out.println("</head>");
        out.println("<body>");
        out.println("<div id=\"swagger-ui\"></div>");
        out.println("<script src=\"https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-bundle.js\" charset=\"UTF-8\"> </script>");
        out.println("<script src=\"https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-standalone-preset.js\" charset=\"UTF-8\"> </script>");
        out.println("<script>");
        out.println("window.onload = function() {");
        out.println("  const ui = SwaggerUIBundle({");
        out.println("    url: window.location.origin + '/openapi',");
        out.println("    dom_id: '#swagger-ui',");
        out.println("    deepLinking: true,");
        out.println("    presets: [");
        out.println("      SwaggerUIBundle.presets.apis,");
        out.println("      SwaggerUIStandalonePreset");
        out.println("    ],");
        out.println("    layout: 'StandaloneLayout'");
        out.println("  });");
        out.println("  window.ui = ui;");
        out.println("};");
        out.println("</script>");
        out.println("</body>");
        out.println("</html>");
    }
}