package org.testcontainers.liberty.app;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Path("/resource")
@Produces(MediaType.TEXT_PLAIN)
@Consumes(MediaType.TEXT_PLAIN)
public class TestResource {

    private static final String dbURL = System.getenv("DB_URL");

    @GET
    public String getConnection() {
        try {
            URL url = new URL(dbURL + "/hello");
            System.out.println("KJA1017 url is: " + url.toString());
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String response = in.readLine();
                System.out.println("KJA1017 response: " + response);
                return response;
            }
        } catch (Exception e) {
            System.out.println("KJA1017 error: " + e.toString());
        }
        return "FAILURE";
    }
}
