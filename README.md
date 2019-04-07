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
        classpath("info.tdoc:opencensus-exporter-trace-xray:0.0.1")
    }
}

repositories {
    maven { url 'https://raw.githubusercontent.com/shirou/opencensus-exporter-trace-xray/master/public' }
}


dependencies {
    compile 'io.opencensus:opencensus-api:0.19.2'
    compile 'io.opencensus:opencensus-impl:0.19.2'

    compile 'io.opencensus:opencensus-exporter-trace-xray:0.0.1'
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

#### Java Versions

Java 8 or above is required for using this exporter.

## conversion

Src opensensus span data to X-Ray data.

- OpenCensus Attribute -> X-Ray annocations
- Status -> error, exceptions

## TODO

- [ ] Report errors / exceptions
- [ ] Support http
- [ ] Subsegments
- [ ] Service (version etc)


## reference

- Golang AWS X-Ray exporter https://github.com/census-ecosystem/opencensus-go-exporter-aws

## LICENSE

Apache License
