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

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.opencensus.common.Function;
import io.opencensus.common.Functions;
import io.opencensus.common.Timestamp;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.SpanId;
import io.opencensus.trace.Status;
import io.opencensus.trace.TraceId;
import io.opencensus.trace.export.SpanData;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 *
 * document: https://docs.aws.amazon.com/xray/latest/devguide/xray-api-segmentdocuments.html
 *
 *
 */
public class TraceSegment {
  private static final Logger logger = Logger.getLogger(TraceSegment.class.getName());
  private static final String ATTRIB_SQL_EXEC = "sql.query";
  private static final SecureRandom Random = new SecureRandom();

  private static final Random rnd = new Random();
  private static final Integer MaxAge = 60 * 60 * 24 * 28; // 28Day
  private static final Integer MaxSkew = 60 * 5; // 5m
  private static final String VersionNo = "1";
  private static final Pattern reInvalidSpanCharacters = Pattern.compile("");
  private static final Integer maxSegmentNameLength = 200;
  private static final String defaultSegmentName = "span";

  @JsonProperty("name")
  public String name;

  @JsonProperty("id")
  public String id;

  @JsonProperty("start_time")
  public double startTime;

  @JsonProperty("trace_id")
  public String traceId;

  @JsonProperty("parent_id")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String parentId;

  @JsonProperty("end_time")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public double endTime;

  @JsonProperty("in_progress")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Boolean inProgress;

  @JsonProperty("type")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String type;

  @JsonProperty("namespace")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String nameSpace;

  @JsonProperty("error")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Boolean error;

  @JsonProperty("fault")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Boolean fault;

  @JsonProperty("throttle")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Boolean throttle;

  @JsonProperty("user")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String user;

  @JsonProperty("annotations")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Map<String, Object> annotations;

  @JsonProperty("precursor_ids")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public List<String> precursorIds;

  @JsonProperty("cause")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Cause cause;

  @JsonProperty("http")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public HTTP http;

  @JsonProperty("sql")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public SQL sql;

  @JsonProperty("origin")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String origin;

  @JsonProperty("subsegments")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public List<TraceSegment> subsegments;

  public static class Cause {
    @JsonProperty("exceptions")
    public List<Exceptions> exceptions;

    public Cause(Exceptions exp) {
      this.exceptions = new ArrayList<Exceptions>();
      this.exceptions.add(exp);
    }

    public static class Exceptions {
      @JsonProperty("id")
      public String id;

      @JsonProperty("message")
      public String message;
    }
  }

  public static class HTTP {
    @JsonProperty("request")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Request request;

    public static class Request {
      @JsonProperty("method")
      @JsonInclude(JsonInclude.Include.NON_NULL)
      public String method;

      @JsonProperty("url")
      @JsonInclude(JsonInclude.Include.NON_NULL)
      public String url;

      @JsonProperty("traced")
      @JsonInclude(JsonInclude.Include.NON_NULL)
      public Boolean traced;
    }

    @JsonProperty("response")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Response response;

    public static class Response {
      @JsonProperty("status")
      @JsonInclude(JsonInclude.Include.NON_NULL)
      public String status;
    }
  }

  public static class SQL {
    @JsonProperty("sanitized_query")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String sanitizedQuery;
  }

  public TraceSegment(String name, String parentId) {
    this.name = name;
    this.type = "subsegment";
    this.parentId = parentId;
  }

  public TraceSegment(String name, SpanData sd) {
    SpanContext sc = sd.getContext();
    if (name == null || name.equals("")) {
      this.name = fixSegmentName(sd.getName());
    } else {
      this.name = name;
    }

    // IDs
    this.id = convertToAmazonSpanID(sc.getSpanId());
    this.traceId = convertToAmazonTraceID(sc.getTraceId());
    SpanId parentId = sd.getParentSpanId();

    if (sd.getHasRemoteParent() == null) { // top level
      // set nothing
    } else if (sd.getHasRemoteParent() == true) { // remote invocation
      this.nameSpace = "remote";
      this.precursorIds = new ArrayList<String>();
      this.precursorIds.add(convertToAmazonSpanID(parentId));
    } else if (parentId != null && parentId.isValid()) {
      { // local invocation
        this.type = "subsegment";
        this.parentId = convertToAmazonSpanID(parentId);
        // if subsegment, change name from a service name to a method name.
        this.name = fixSegmentName(sd.getName());
      }
    }

    // Time
    this.startTime = toEpochSeconds(sd.getStartTimestamp());
    Timestamp endTime = sd.getEndTimestamp();
    if (endTime == null) {
      this.inProgress = true;
    } else {
      this.endTime = toEpochSeconds(endTime);
    }

    makeCause(sd.getStatus());
    this.annotations = this.makeAnnotations(sd.getName(), sd.getAttributes());
  }

  /*
   * convertToAmazonSpanID generates an Amazon spanID from a SpanID - a 64-bit identifier
   * for the segment, unique among segments in the same trace, in 16 hexadecimal digits.
   */
  private static String convertToAmazonSpanID(SpanId spanId) {
    byte[] v = spanId.getBytes();
    if (v.equals("")) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (byte b : Arrays.copyOfRange(v, 0, 8)) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  /*
   * converts a trace ID to the Amazon format.
   *
   */
  private static String convertToAmazonTraceID(TraceId traceId) {
    long epochNow = Instant.now(Clock.systemUTC()).getEpochSecond();
    long epoch = ByteBuffer.wrap(Arrays.copyOfRange(traceId.getBytes(), 0, 4)).getInt();

    long delta = epochNow - epoch;
    if (delta > MaxAge || delta < -MaxSkew) {
      epoch = epochNow;
    }

    StringBuilder sb = new StringBuilder();

    sb.append(VersionNo);
    sb.append("-");

    byte[] epochByte = ByteBuffer.allocate(8).putLong(epoch).array();
    for (byte b : Arrays.copyOfRange(epochByte, 4, 8)) {
      sb.append(String.format("%02x", b));
    }

    sb.append("-");
    // overwrite with identifier
    for (byte b : Arrays.copyOfRange(traceId.getBytes(), 4, 16)) {
      sb.append(String.format("%02x", b));
    }

    return sb.toString();
  }

  private void makeCause(Status status) {
    if (status == null || status.isOk()) {
      return;
    }

    String desc = status.getDescription();
    if (desc != null && desc.equals("") != true) {
      Cause.Exceptions exp = new Cause.Exceptions();
      exp.id =
          String.format("%04x", rnd.nextInt(0x10000)) + String.format("%04x", rnd.nextInt(0x10000));
      exp.message = desc;
      this.cause = new Cause(exp);
    }
    if (status.equals(Status.RESOURCE_EXHAUSTED)) {
      this.throttle = true;
    } else if (isError(status) == true) {
      this.error = true;
    } else {
      this.fault = true;
    }
  }

  private Map<String, Object> makeAnnotations(String name, SpanData.Attributes attrib) {
    Map<String, Object> ret = new HashMap<String, Object>();
    ret.put("name", name); // allways put span's name to attribute.

    if (attrib.getAttributeMap().entrySet().size() == 0) {
      return ret;
    }

    SQL sqlinfo = null;
    for (Map.Entry<String, AttributeValue> label : attrib.getAttributeMap().entrySet()) {
      String key = label.getKey();
      logger.log(Level.WARNING, key);

      if (key.equals(ATTRIB_SQL_EXEC)) {
        sqlinfo = new SQL();
        sqlinfo.sanitizedQuery = attributeValueToString(label.getValue());
        continue;
      }

      ret.put(label.getKey(), attributeValueToObject(label.getValue()));
    }

    if (sqlinfo != null) {
      this.subsegments = new ArrayList<TraceSegment>();
      TraceSegment s = new TraceSegment("sql.query", this.id);
      s.id = generateId();
      s.nameSpace = "remote";
      s.startTime = this.startTime;
      s.endTime = this.endTime;
      s.traceId = this.traceId;
      s.sql = sqlinfo;
      this.subsegments.add(s);
    }

    return ret;
  }

  /*
   * fixSegmentName removes any invalid characters from the span name.  AWS X-Ray defines
   * the list of valid characters here:
   * https://docs.aws.amazon.com/xray/latest/devguide/xray-api-segmentdocuments.html
   */
  private static String fixSegmentName(String name) {
    Matcher m = reInvalidSpanCharacters.matcher(name);
    if (m.matches()) {
      // only allocate for ReplaceAllString if we need to
      name = m.replaceAll("");
    }
    if (name.length() > maxSegmentNameLength) {
      name = name.substring(0, maxSegmentNameLength);
    } else if (name.length() == 0) {
      name = defaultSegmentName;
    }

    return name;
  }

  private static double toEpochSeconds(Timestamp timestamp) {
    return timestamp.getSeconds() + NANOSECONDS.toMillis(timestamp.getNanos()) / 1000.0;
  }

  public static String generateId() {
    String id = Long.toString(Random.nextLong() >>> 1, 16);
    while (id.length() < 16) {
      id = '0' + id;
    }
    return id;
  }

  private static final Function<Object, String> returnToString = Functions.returnToString();

  private static String attributeValueToString(AttributeValue attributeValue) {
    return attributeValue.match(
        returnToString,
        returnToString,
        returnToString,
        returnToString,
        Functions.<String>returnConstant(""));
  }

  private static Object attributeValueToObject(AttributeValue attributeValue) {
    return attributeValue.match(
        stringAttributeValueFunction,
        booleanAttributeValueFunction,
        longAttributeValueFunction,
        doubleAttributeValueFunction,
        Functions.returnNull());
  }

  // Constant functions for AttributeValue.
  private static final Function<? super String, Object> stringAttributeValueFunction =
      new Function<String, Object>() {
        @Override
        public Object apply(String value) {
          return value;
        }
      };
  private static final Function<? super Boolean, Object> booleanAttributeValueFunction =
      new Function<Boolean, Object>() {
        @Override
        public Object apply(Boolean value) {
          return value;
        }
      };
  private static final Function<? super Long, Object> longAttributeValueFunction =
      new Function<Long, Object>() {
        @Override
        public Object apply(Long value) {
          return value;
        }
      };
  private static final Function<? super Double, Object> doubleAttributeValueFunction =
      new Function<Double, Object>() {
        @Override
        public Object apply(Double value) {
          return value;
        }
      };

  /*
   * if 400 <= code < 500, return true
   */
  private static final Boolean isError(Status status) {
    switch (status.getCanonicalCode()) {
      case ABORTED: // 409 Conflict
      case ALREADY_EXISTS: // 409 Conflict
      case CANCELLED: // 499 Client Closed Request
      case FAILED_PRECONDITION: // 400 Bad Request
      case INVALID_ARGUMENT: // 400 Bad Request
      case NOT_FOUND: // 404 Not Found
      case OUT_OF_RANGE: // 400 Bad Request
      case PERMISSION_DENIED: // 403 Forbidden
      case RESOURCE_EXHAUSTED: // 429 Too Many Requests
      case UNAUTHENTICATED: // 401 Unauthorized
        return true;
      case OK:
        return false;
      case DATA_LOSS: // 500 Internal Server Error
      case DEADLINE_EXCEEDED: // 504 Gateway Timeout
      case INTERNAL: // 500 Internal Server Error
      case UNAVAILABLE: // 503 Service Unavailable
      case UNIMPLEMENTED: // 501 Not Implemented
      case UNKNOWN: //  500 Internal Server Error
        return false;
      default:
        return false;
    }
  }
}
