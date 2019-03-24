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

import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.verify;

import io.opencensus.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class XRayTraceExporterTest {
  @Mock private SpanExporter spanExporter;
  @Mock private SpanExporter.Handler handler;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void registerUnregisterXRayExporter() {
    XRayTraceExporter.register(spanExporter, handler);
    verify(spanExporter)
        .registerHandler(
            eq("info.tdoc.exporter.trace.xray.XRayTraceExporter"), same(handler));
    XRayTraceExporter.unregister(spanExporter);
    verify(spanExporter)
        .unregisterHandler(eq("info.tdoc.exporter.trace.xray.XRayTraceExporter"));
  }
}
