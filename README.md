# OpenCensus AWS X-Ray Trace Exporter

The *OpenCensus X-Ray Trace Exporter* is a trace exporter that exports data to AWS X-Ray.


![Screenshot](x-ray-screenshot.png?raw=true "Screenshot")


### Hello X-Ray

#### Add the dependencies to your project

For Gradle add to your dependencies:
```groovy

buildscript {
    repositories {
        maven { url 'https://raw.githubusercontent.com/shirou/opencensus-exporter-trace-xray/master/public' }
    }
    dependencies {
        classpath("info.tdoc:opencensus-exporter-trace-xray:0.0.2")
    }
}

repositories {
    maven { url 'https://raw.githubusercontent.com/shirou/opencensus-exporter-trace-xray/master/public' }
}


dependencies {
    compile 'io.opencensus:opencensus-api:0.19.2'
    compile 'io.opencensus:opencensus-impl:0.19.2'

    compile 'io.opencensus:opencensus-exporter-trace-xray:0.0.2'
}
```

#### Register the exporter

This will export traces to the X-Ray thrift format to the X-Ray instance started previously:

```java
import info.tdoc.exporter.trace.xray.XRayTraceExporter;

public class MyMainClass {
  public static void main(String[] args) throws Exception {
    XRayTraceExporter.createAndRegister("my-service");
    // ...
  }
}
```

#### HTTP Attribute key

If span has these attribute key and value, this library add AWS X-Ray HTTP Request/Response to generated segment.

- http.method
- http.user_agent
- http.url
- http.status_code

These are same as this. But some of them can not be converted to AWS X-Ray HTTP Specification. so just dropped.

https://github.com/census-instrumentation/opencensus-java/blob/master/contrib/http_util/src/main/java/io/opencensus/contrib/http/util/HttpTraceAttributeConstants.java


#### SQL Attribute key

If span has an attribute with key `sql.query`, this library automatically create subsegment which has SQL sanitized_query attribute. Then, you can get SQL query with AWS X-Ray web console.

Note: this name is sanitized_query but actually, not sanitized. plese be very careful because anyone who can access to X-Ray web console can read the SQL query.

```
    tracer.getCurrentSpan().putAttribute("sql.query", AttributeValue.stringAttributeValue(sql));
```

#### Java Versions

Java 8 or above is required for using this exporter.

## conversion

Src opensensus span data to X-Ray data.

- OpenCensus Attribute -> X-Ray annocations
- Status -> error, exceptions

## TODO

- [x] Report errors / exceptions
- [x] Support http
- [x] Support SQL
- [x] Subsegments
- [ ] Service (version etc)


## reference

- Golang AWS X-Ray exporter https://github.com/census-ecosystem/opencensus-go-exporter-aws

## LICENSE

Apache License
