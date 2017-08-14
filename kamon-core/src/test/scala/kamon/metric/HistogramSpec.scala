package kamon.metric

import kamon.Kamon
import org.scalatest.{Matchers, WordSpec}
import MeasurementUnit._


class HistogramSpec extends WordSpec with Matchers {
  import HistogramTestHelper.HistogramMetricSyntax

  "a Histogram" should {
    "record values and reset internal state when a snapshot is taken" in {
      val histogram = Kamon.histogram("test", unit = time.nanoseconds)
      histogram.record(100)
      histogram.record(150, 998)
      histogram.record(200)

      val distribution = histogram.distribution()
      distribution.min shouldBe(100)
      distribution.max shouldBe(200)
      distribution.count shouldBe(1000)
      distribution.buckets.length shouldBe 3
      distribution.buckets.map(b => (b.value, b.frequency)) should contain.allOf(
        (100 -> 1),
        (150 -> 998),
        (200 -> 1)
      )

      val emptyDistribution = histogram.distribution()
      emptyDistribution.min shouldBe(0)
      emptyDistribution.max shouldBe(0)
      emptyDistribution.count shouldBe(0)
      emptyDistribution.buckets.length shouldBe 0
    }

    "accept a smallest discernible value configuration" in {
      // The lowestDiscernibleValue gets rounded down to the closest power of 2, so, here it will be 64.
      val histogram = Kamon.histogram("test-lowest-discernible-value", unit = time.nanoseconds, dynamicRange = DynamicRange.Fine.withLowestDiscernibleValue(100))
      histogram.record(100)
      histogram.record(200)
      histogram.record(300)
      histogram.record(1000)
      histogram.record(2000)
      histogram.record(3000)

      val distribution = histogram.distribution()
      distribution.min shouldBe(64)
      distribution.max shouldBe(2944)
      distribution.count shouldBe(6)
      distribution.buckets.length shouldBe 6
      distribution.buckets.map(b => (b.value, b.frequency)) should contain.allOf(
        (64 -> 1),
        (192 -> 1),
        (256 -> 1),
        (960 -> 1),
        (1984 -> 1),
        (2944 -> 1)
      )
    }

    "[private api] record values and optionally keep the internal state when a snapshot is taken" in {
      val histogram = Kamon.histogram("test", unit = time.nanoseconds)
      histogram.record(100)
      histogram.record(150, 998)
      histogram.record(200)

      val distribution = {
        histogram.distribution(resetState = false) // first one gets discarded
        histogram.distribution(resetState = false)
      }

      distribution.min shouldBe(100)
      distribution.max shouldBe(200)
      distribution.count shouldBe(1000)
      distribution.buckets.length shouldBe 3
      distribution.buckets.map(b => (b.value, b.frequency)) should contain.allOf(
        (100 -> 1),
        (150 -> 998),
        (200 -> 1)
      )
    }
  }
}

object HistogramTestHelper {

  implicit class HistogramMetricSyntax(metric: HistogramMetric) {
    def distribution(resetState: Boolean = true): Distribution =
      metric.refine(Map.empty[String, String]) match {
        case h: AtomicHdrHistogram  => h.snapshot(resetState).distribution
        case h: HdrHistogram        => h.snapshot(resetState).distribution
      }
  }
}
