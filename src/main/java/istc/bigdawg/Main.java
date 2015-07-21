package istc.bigdawg;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import istc.bigdawg.stream.MemStreamDAO;
import istc.bigdawg.stream.StreamDAO;

import java.io.IOException;
import java.net.URI;

/**
 * Main class.
 *
 */
public class Main {
    // Base URI the Grizzly HTTP server will listen on
    public static final String BASE_URI = "http://0.0.0.0:8080/bigdawg/";
    public static StreamDAO streamDAO = null;

    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     */
    public static HttpServer startServer() {
        // create a resource config that scans for JAX-RS resources and providers
        // in istc.bigdawg package
        final ResourceConfig rc = new ResourceConfig().packages("istc.bigdawg");

        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        streamDAO = getStreamDAO();
        
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
    }

    public static StreamDAO getStreamDAO(){
    	if (streamDAO == null){
    		streamDAO = new MemStreamDAO();
    	}
    	return streamDAO;
    }
    
    /**
     * Main method.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        final HttpServer server = startServer();
        System.out.println(String.format("Jersey app started with WADL available at "
                + "%sapplication.wadl\nHit enter to stop it...", BASE_URI));
        System.in.read();
        server.stop();
    }
}
