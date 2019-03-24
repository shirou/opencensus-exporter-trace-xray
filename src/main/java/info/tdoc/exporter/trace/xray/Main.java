package info.tdoc.exporter.trace.xray;

import io.opencensus.common.Scope;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;

public class Main {
  private static Tracer tracer = Tracing.getTracer();

  public static void main(String... args) {
    XRayTraceExporter.createAndRegister("hogeghoe");

    // For demo purposes, we'll always sample
    TraceConfig traceConfig = Tracing.getTraceConfig();
    traceConfig.updateActiveTraceParams(
        traceConfig.getActiveTraceParams().toBuilder().setSampler(Samplers.alwaysSample()).build());

    // Do some work
    for (int i = 0; i < 5; i++) {
      String name = String.format("sample-%d", i);
      Scope ss = tracer.spanBuilder(name).startScopedSpan();
      tracer.getCurrentSpan().addAnnotation("This annotation is for " + name);
      sleep(200); // Sleep for 200 milliseconds
      ss.close();
    }

    sleep(8000); // Sleep for 8 seconds to give exporting time to complete
    System.exit(0);
  }

  private static void sleep(int ms) {
    // A helper to avoid try-catch when invoking Thread.sleep so that
    // sleeps can be succinct and not permeated by exception handling.
    try {
      Thread.sleep(ms);
    } catch (Exception e) {
      System.err.println(String.format("Failed to sleep for %dms. Exception: %s", ms, e));
    }
  }
}
