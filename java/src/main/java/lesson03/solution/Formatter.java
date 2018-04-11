package lesson03.solution;

import com.google.common.collect.ImmutableMap;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.opentracing.Scope;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.tag.Tags;
import lib.Tracing;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.util.HashMap;

public class Formatter extends Application<Configuration> {
    private final Tracer tracer;

    private Formatter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Path("/format")
    @Produces(MediaType.TEXT_PLAIN)
    public class FormatterResource {

        @GET
        public String format(@QueryParam("helloTo") String helloTo, @Context HttpHeaders httpHeaders) {
            try (Scope scope = startServerSpan(tracer, httpHeaders, "format")) {
                String helloStr = String.format("Hello, %s!", helloTo);
                scope.span().log(ImmutableMap.of("event", "string-format", "value", helloStr));
                return helloStr;
            }
        }

    }

    @Override
    public void run(Configuration configuration, Environment environment) throws Exception {
        environment.jersey().register(new FormatterResource());
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("dw.server.applicationConnectors[0].port", "8081");
        System.setProperty("dw.server.adminConnectors[0].port", "9081");

        Tracer tracer = Tracing.init("formatter");
        new Formatter(tracer).run(args);
    }

    public static Scope startServerSpan(Tracer tracer, javax.ws.rs.core.HttpHeaders httpHeaders, String operationName) {
        // format the headers for extraction
        MultivaluedMap<String, String> rawHeaders = httpHeaders.getRequestHeaders();
        final HashMap<String, String> headers = new HashMap<String, String>();
        for (String key : rawHeaders.keySet()) {
            headers.put(key, rawHeaders.get(key).get(0));
        }

        Tracer.SpanBuilder spanBuilder;
        try {
            SpanContext parentSpan = tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapExtractAdapter(headers));
            if (parentSpan == null) {
                spanBuilder = tracer.buildSpan(operationName);
            } else {
                spanBuilder = tracer.buildSpan(operationName).asChildOf(parentSpan);
            }
        } catch (IllegalArgumentException e) {
            spanBuilder = tracer.buildSpan(operationName);
        }
        return spanBuilder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).startActive(true);
    }
}
