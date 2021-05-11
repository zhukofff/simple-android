package org.simple.clinic

import datadog.opentracing.DDTracer
import io.opentracing.util.GlobalTracer
import org.junit.Test
import java.util.Properties

class DatadogReportingTest {

  private val tracer: DDTracer

  init {
    val properties = Properties().apply {
      put("api-key", "7bd4f1381f5be05f217b668973ce6832")
    }

    tracer = DDTracer
        .builder()
        .serviceName("android-perf-regression-tests")
        .withProperties(properties)
        .build()

    GlobalTracer.registerIfAbsent(tracer)
    datadog.trace.api.GlobalTracer.registerIfAbsent(tracer)
  }

  @Test
  fun time_taken_must_be_reported_to_dd() {

  }
}
