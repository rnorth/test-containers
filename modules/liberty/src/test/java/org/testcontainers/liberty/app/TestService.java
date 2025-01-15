package org.testcontainers.liberty.app;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;

@Path("/service")
@Produces(MediaType.TEXT_PLAIN)
@Consumes(MediaType.TEXT_PLAIN)
public class TestService {

    private static final List<String> cache = new ArrayList<>();

    @GET
    public String getAll() {
        System.out.println("Calling getAll");
        return cache.toString();
    }

    @GET
    @Path("/{value}")
    public boolean isCached(@PathParam("value") String value) {
        System.out.println("Calling isCached with value " + value);
        return cache.contains(value);
    }

    @POST
    public String cacheIt(@QueryParam("value") String value) {
        System.out.println("Calling cacheIt with value " + value);
        cache.add(value);
        return value;
    }

    @POST
    @Path("/{value}")
    public boolean updateIt(@PathParam("value") String oldValue, @QueryParam("value") String newValue) {
        System.out.println("Calling updateIt with oldValue " + oldValue + " and new value " + newValue);
        boolean result = cache.remove(oldValue);
        cache.add(newValue);
        return result;
    }

    @DELETE
    @Path("/{value}")
    public boolean removeIt(@PathParam("value") String value) {
        System.out.println("Calling removeIt with value " + value);
        return cache.remove(value);
    }
}
