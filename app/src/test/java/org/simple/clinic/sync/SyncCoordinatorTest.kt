package org.simple.clinic.sync

import com.f2prateek.rx.preferences2.Preference
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.simple.clinic.patient.SyncStatus
import java.util.Optional
import org.simple.clinic.util.RxErrorsRule
import java.time.Instant
import java.util.UUID

class SyncCoordinatorTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private lateinit var syncCoordinator: SyncCoordinator
  private lateinit var repository: SynceableRepository<Any, Any>
  private lateinit var lastSyncTimestamp: Preference<Optional<Instant>>

  @Before
  fun setup() {
    repository = mock()
    lastSyncTimestamp = mock()

    syncCoordinator = SyncCoordinator()
  }

  @Test
  fun `when pending sync records are empty, then the push network call should not be made`() {
    whenever(repository.recordsWithSyncStatus(SyncStatus.PENDING)).thenReturn(emptyList())

    var networkCallMade = false

    syncCoordinator.push(repository) {
      networkCallMade = true
      DataPushResponse(emptyList())
    }

    assertThat(networkCallMade).isFalse()
  }

  @Test
  fun `if there are validation errors in push, then the failing records should be marked as invalid`() {
    whenever(repository.recordsWithSyncStatus(SyncStatus.PENDING)).thenReturn(listOf(1, 2, 3))

    val validationErrors = listOf(
        ValidationErrors(uuid = UUID.randomUUID(), schemaErrorMessages = listOf("error-1")),
        ValidationErrors(uuid = UUID.randomUUID(), schemaErrorMessages = listOf("error-2"))
    )

    syncCoordinator.push(repository) {
      DataPushResponse(validationErrors)
    }

    verify(repository).setSyncStatus(validationErrors.map { it.uuid }, SyncStatus.INVALID)
  }
}
