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

import static com.google.common.base.Preconditions.checkState;

import com.amazonaws.services.xray.AWSXRay;
import com.amazonaws.services.xray.AWSXRayAsyncClientBuilder;
import com.google.common.annotations.VisibleForTesting;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.export.SpanExporter;
import io.opencensus.trace.export.SpanExporter.Handler;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * An AWS X-Ray span exporter implementation which exports data to AWS X-Ray.
 *
 * <p>Example of usage:
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *   XRayTraceExporter.createAndRegister("myservicename");
 *   ... // Do work.
 * }
 * }</pre>
 */
public final class XRayTraceExporter {
  private static final String REGISTER_NAME = XRayTraceExporter.class.getName();
  private static final Object monitor = new Object();
  private static final Logger logger = Logger.getLogger(XRayTraceExporter.class.getName());

  @GuardedBy("monitor")
  @Nullable
  private static Handler handler = null;

  private XRayTraceExporter() {}

  /**
   * Creates and registers the XRay Trace exporter to the OpenCensus library. Only one XRay exporter
   * can be registered at any point.
   *
   * @param serviceName the {@link Span#localServiceName() local service name} of the process.
   * @throws IllegalStateException if a XRay exporter is already registered.
   */
  public static void createAndRegister(String serviceName) {
    AWSXRay client = AWSXRayAsyncClientBuilder.defaultClient();

    createAndRegister(client, serviceName);
  }

  /**
   * Creates and registers the XRay Trace exporter to the OpenCensus library. Only one XRay exporter
   * can be registered at any point.
   *
   * @param serviceName the {@link Span#localServiceName() local service name} of the process.
   * @throws IllegalStateException if a XRay exporter is already registered.
   */
  public static void createAndRegister(AWSXRay client, String serviceName) {
    synchronized (monitor) {
      checkState(handler == null, "XRay exporter is already registered.");
      Handler newHandler = new XRayExporterHandler(client, serviceName);
      handler = newHandler;

      register(Tracing.getExportComponent().getSpanExporter(), newHandler);
    }
  }

  /**
   * Registers the {@code XRayTraceExporter}.
   *
   * @param spanExporter the instance of the {@code SpanExporter} where this service is registered.
   */
  @VisibleForTesting
  static void register(SpanExporter spanExporter, Handler handler) {
    spanExporter.registerHandler(REGISTER_NAME, handler);
  }

  /**
   * Unregisters the XRay Trace exporter from the OpenCensus library.
   *
   * @throws IllegalStateException if a XRay exporter is not registered.
   */
  public static void unregister() {
    synchronized (monitor) {
      checkState(handler != null, "XRay exporter is not registered.");
      unregister(Tracing.getExportComponent().getSpanExporter());
      handler = null;
    }
  }

  /**
   * Unregisters the {@code XRayTraceExporter}.
   *
   * @param spanExporter the instance of the {@code SpanExporter} from where this service is
   *     unregistered.
   */
  @VisibleForTesting
  static void unregister(SpanExporter spanExporter) {
    spanExporter.unregisterHandler(REGISTER_NAME);
  }
}
