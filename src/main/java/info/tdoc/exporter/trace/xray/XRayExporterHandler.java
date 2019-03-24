/*
 * Copyright 2019, Shirou WAKAYAMA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.tdoc.exporter.trace.xray;

import com.amazonaws.services.xray.AWSXRay;
import com.amazonaws.services.xray.model.PutTraceSegmentsRequest;
import com.amazonaws.services.xray.model.PutTraceSegmentsResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencensus.common.Scope;
import io.opencensus.trace.Sampler;
import io.opencensus.trace.Status;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.export.SpanData;
import io.opencensus.trace.export.SpanExporter;
import io.opencensus.trace.samplers.Samplers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/*>>>
import org.checkerframework.checker.nullness.qual.Nullable;
*/

final class XRayExporterHandler extends SpanExporter.Handler {
  private static final Tracer tracer = Tracing.getTracer();
  private static final Sampler probabilitySampler = Samplers.probabilitySampler(0.0001);
  private static final Logger logger = Logger.getLogger(XRayExporterHandler.class.getName());

  private static final String STATUS_CODE = "census.status_code";
  private static final String STATUS_DESCRIPTION = "census.status_description";

  private final AWSXRay client;
  private final String serviceName;
  private final Boolean useDaemon;
  private final ObjectMapper mapper = new ObjectMapper();

  XRayExporterHandler(AWSXRay client, String serviceName) {
    this.client = client;
    this.serviceName = serviceName;
    this.useDaemon = false;
  }

  /*
  @SuppressWarnings("deprecation")
  static Span generateSpan(SpanData spanData, Endpoint localEndpoint) {
    SpanContext context = spanData.getContext();
    long startTimestamp = toEpochMicros(spanData.getStartTimestamp());

    // TODO(sebright): Fix the Checker Framework warning.
    @SuppressWarnings("nullness")
    long endTimestamp = toEpochMicros(spanData.getEndTimestamp());

    // TODO(bdrutu): Fix the Checker Framework warning.
    @SuppressWarnings("nullness")
    Span.Builder spanBuilder =
        Span.newBuilder()
            .traceId(context.getTraceId().toLowerBase16())
            .id(context.getSpanId().toLowerBase16())
            .kind(toSpanKind(spanData))
            .name(spanData.getName())
            .timestamp(toEpochMicros(spanData.getStartTimestamp()))
            .duration(endTimestamp - startTimestamp)
            .localEndpoint(localEndpoint);

    if (spanData.getParentSpanId() != null && spanData.getParentSpanId().isValid()) {
      spanBuilder.parentId(spanData.getParentSpanId().toLowerBase16());
    }

    for (Map.Entry<String, AttributeValue> label :
        spanData.getAttributes().getAttributeMap().entrySet()) {
      spanBuilder.putTag(label.getKey(), attributeValueToString(label.getValue()));
    }
    Status status = spanData.getStatus();
    if (status != null) {
      spanBuilder.putTag(STATUS_CODE, status.getCanonicalCode().toString());
      if (status.getDescription() != null) {
        spanBuilder.putTag(STATUS_DESCRIPTION, status.getDescription());
      }
    }

    for (TimedEvent<Annotation> annotation : spanData.getAnnotations().getEvents()) {
      spanBuilder.addAnnotation(
          toEpochMicros(annotation.getTimestamp()), annotation.getEvent().getDescription());
    }

    for (TimedEvent<io.opencensus.trace.MessageEvent> messageEvent :
        spanData.getMessageEvents().getEvents()) {
      spanBuilder.addAnnotation(
          toEpochMicros(messageEvent.getTimestamp()), messageEvent.getEvent().getType().name());
    }

    return spanBuilder.build();
  }

  @javax.annotation.Nullable
  private static Span.Kind toSpanKind(SpanData spanData) {
    // This is a hack because the Span API did not have SpanKind.
    if (spanData.getKind() == Kind.SERVER
        || (spanData.getKind() == null && Boolean.TRUE.equals(spanData.getHasRemoteParent()))) {
      return Span.Kind.SERVER;
    }

    // This is a hack because the Span API did not have SpanKind.
    if (spanData.getKind() == Kind.CLIENT || spanData.getName().startsWith("Sent.")) {
      return Span.Kind.CLIENT;
    }

    return null;
  }

  // The return type needs to be nullable when this function is used as an argument to 'match' in
  // attributeValueToString, because 'match' doesn't allow covariant return types.
  private static final Function<Object, @Nullable String> returnToString =
      Functions.returnToString();

  // TODO: Fix the Checker Framework warning.
  @SuppressWarnings("nullness")
  private static String attributeValueToString(AttributeValue attributeValue) {
    return attributeValue.match(
        returnToString,
        returnToString,
        returnToString,
        returnToString,
        Functions.<String>returnConstant(""));
  }
  */

  private TraceSegment generateSegment(SpanData spanData) {
    // TODO: AWS X-Ray does not have service value?
    return new TraceSegment(spanData);
  }

  @Override
  public void export(Collection<SpanData> spanDataList) {
    Scope scope =
        tracer.spanBuilder("SendXRaySpans").setSampler(probabilitySampler).startScopedSpan();
    try {
      List<String> encodedSpans = new ArrayList<String>(spanDataList.size());
      for (SpanData spanData : spanDataList) {
        TraceSegment tr = generateSegment(spanData);
        String s = mapper.writeValueAsString(tr);
        if (useDaemon == true) {
          s = "{\"format\": \"json\", \"version\": 1}\n" + s;
        }
        encodedSpans.add(s);
      }
      try {
        PutTraceSegmentsRequest req =
            new PutTraceSegmentsRequest().withTraceSegmentDocuments(encodedSpans);
        PutTraceSegmentsResult res = client.putTraceSegments(req);
        if (res.getUnprocessedTraceSegments().size() != 0) {
          tracer.getCurrentSpan().setStatus(Status.DATA_LOSS);
          logger.log(
              Level.WARNING,
              "UnprocessedTraceSegments exist: count={}",
              res.getUnprocessedTraceSegments().size());
        }
      } catch (RuntimeException e) {
        tracer
            .getCurrentSpan()
            .setStatus(
                Status.UNKNOWN.withDescription(
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        throw new RuntimeException(e); // TODO: should we instead do drop metrics?
      }
    } catch (JsonProcessingException e) {
      tracer.getCurrentSpan().setStatus(Status.UNKNOWN.withDescription(e.getMessage()));
      throw new RuntimeException(e); // TODO: should we instead do drop metrics?
    } finally {
      scope.close();
    }
  }
}
