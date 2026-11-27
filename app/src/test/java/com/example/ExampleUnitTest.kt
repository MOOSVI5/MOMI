package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun eightyMinuteDurationFormatting_and_showcaseAccountDefaults_work() {
    assertEquals("1:20:00", com.example.util.MediaAssets.formatDuration(4_800_000L))
    assertEquals("1:05", com.example.util.MediaAssets.formatDuration(65_000L))

    val starter = com.example.data.model.ShowcaseAccount(
      username = "elena.maris",
      displayName = "Elena Maris"
    )
    assertTrue(starter.isVerified)
    assertNull(starter.avatarUrl)
  }
}
