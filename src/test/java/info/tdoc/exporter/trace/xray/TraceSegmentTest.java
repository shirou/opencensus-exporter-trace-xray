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

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.opencensus.common.Timestamp;
import io.opencensus.trace.Annotation;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Link;
import io.opencensus.trace.MessageEvent;
import io.opencensus.trace.Span.Kind;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.SpanId;
import io.opencensus.trace.Status;
import io.opencensus.trace.TraceId;
import io.opencensus.trace.TraceOptions;
import io.opencensus.trace.Tracestate;
import io.opencensus.trace.export.SpanData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import com.amazonaws.services.xray.AWSXRay;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TraceSegmentTest {
  private static final byte FF = (byte) 0xFF;
  private static final String serviceName = "testService";

  @Test
  public void exportShouldConvertFromSpanDataToXRaySegment() {
    final long startTime = 1519629870001L;
    final long endTime = 1519630148002L;
    final SpanData sd =
        SpanData.create(
            sampleSpanContext(),
            SpanId.fromBytes(new byte[] {(byte) 0x7F, FF, FF, FF, FF, FF, FF, FF}),
            true,
            "test",
            Kind.SERVER,
            Timestamp.fromMillis(startTime),
            SpanData.Attributes.create(sampleAttributes(), 0),
            SpanData.TimedEvents.create(singletonList(sampleAnnotation()), 0),
            SpanData.TimedEvents.create(singletonList(sampleMessageEvent()), 0),
            SpanData.Links.create(sampleLinks(), 0),
            0,
            Status.OK,
            Timestamp.fromMillis(endTime));

    TraceSegment tr = new TraceSegment(sd);
    assertEquals("0102030405060708", tr.id);

    ObjectMapper mapper = new ObjectMapper();

    try{
      String s = mapper.writeValueAsString(tr);
      assertTrue(s.contains("05060708090a0b0c0d0e0f10")); // trace_id
      assertTrue(s.contains("0102030405060708")); // id
      assertTrue(s.contains(":1.519629870001E9,")); // time
    }catch (Exception e){
      fail(e);
    }
  }

  private static SpanContext sampleSpanContext() {
    return SpanContext.create(
        TraceId.fromBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}),
        SpanId.fromBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}),
        TraceOptions.builder().setIsSampled(true).build(),
        Tracestate.builder().build());
  }

  private static ImmutableMap<String, AttributeValue> sampleAttributes() {
    return ImmutableMap.of(
        "BOOL", AttributeValue.booleanAttributeValue(false),
        "LONG", AttributeValue.longAttributeValue(Long.MAX_VALUE),
        "STRING",
            AttributeValue.stringAttributeValue(
                "Judge of a man by his questions rather than by his answers. -- Voltaire"));
  }

  private static SpanData.TimedEvent<Annotation> sampleAnnotation() {
    return SpanData.TimedEvent.create(
        Timestamp.create(1519629872L, 987654321),
        Annotation.fromDescriptionAndAttributes(
            "annotation #1",
            ImmutableMap.of(
                "bool", AttributeValue.booleanAttributeValue(true),
                "long", AttributeValue.longAttributeValue(1337L),
                "string",
                    AttributeValue.stringAttributeValue(
                        "Kind words do not cost much. Yet they accomplish much. -- Pascal"))));
  }

  private static SpanData.TimedEvent<MessageEvent> sampleMessageEvent() {
    return SpanData.TimedEvent.create(
        Timestamp.create(1519629871L, 123456789),
        MessageEvent.builder(MessageEvent.Type.SENT, 42L)
            .setCompressedMessageSize(69)
            .setUncompressedMessageSize(96)
            .build());
  }

  private static List<Link> sampleLinks() {
    return Lists.newArrayList(
        Link.fromSpanContext(
            SpanContext.create(
                TraceId.fromBytes(
                    new byte[] {FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, FF, 0}),
                SpanId.fromBytes(new byte[] {0, 0, 0, 0, 0, 0, 2, 0}),
                TraceOptions.builder().setIsSampled(false).build(),
                Tracestate.builder().build()),
            Link.Type.CHILD_LINKED_SPAN,
            ImmutableMap.of(
                "Bool", AttributeValue.booleanAttributeValue(true),
                "Long", AttributeValue.longAttributeValue(299792458L),
                "String",
                    AttributeValue.stringAttributeValue(
                        "Man is condemned to be free; because once thrown into the world, "
                            + "he is responsible for everything he does. -- Sartre"))));
  }
}
