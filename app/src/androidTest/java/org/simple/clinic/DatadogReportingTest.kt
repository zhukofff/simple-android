package org.simple.clinic

import android.os.Build
import datadog.opentracing.DDTracer
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import org.junit.Test
import java.util.Properties

class DatadogReportingTest {

  private val tracer: Tracer

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
    val span = tracer.buildSpan("TestDatadogIntegration").start().apply {
      setTag("os_version", Build.VERSION.SDK_INT)
      setTag("os_build", Build.VERSION.BASE_OS)
      setTag("device_model", Build.MODEL)
      setTag("device_brand", Build.BRAND)
    }

    Thread.sleep(2000L)

    span.finish()

    tracer.close()
  }
}
