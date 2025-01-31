package org.simple.clinic.home.overdue

import androidx.paging.PagingData
import com.spotify.mobius.test.NextMatchers.hasEffects
import com.spotify.mobius.test.NextMatchers.hasNoModel
import com.spotify.mobius.test.UpdateSpec
import com.spotify.mobius.test.UpdateSpec.assertThatNext
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.facility.FacilityConfig
import java.time.LocalDate
import java.util.UUID

class OverdueUpdateTest {

  private val dateOnClock = LocalDate.parse("2018-01-01")
  private val updateSpec = UpdateSpec(OverdueUpdate(dateOnClock))
  private val defaultModel = OverdueModel.create()

  @Test
  fun `when patient name is clicked, then open patient summary screen`() {
    val patientUuid = UUID.fromString("1211bce0-0b5d-4203-b5e3-004709059eca")

    updateSpec
        .given(defaultModel)
        .whenEvent(PatientNameClicked(patientUuid))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(OpenPatientSummary(patientUuid))
        ))
  }

  @Test
  fun `when overdue appointments are loaded, then show overdue appointments`() {
    val overdueAppointments = PagingData.from(listOf(
        TestData.overdueAppointment(appointmentUuid = UUID.fromString("4e4baeba-3a8e-4453-ace1-d3149088aefc")),
        TestData.overdueAppointment(appointmentUuid = UUID.fromString("79c4bda9-50cf-4484-8a2a-c5336ce8af84"))
    ))
    val facility = TestData.facility(
        uuid = UUID.fromString("6d66fda7-7ca6-4431-ac3b-b570f1123624"),
        facilityConfig = FacilityConfig(
            diabetesManagementEnabled = true,
            teleconsultationEnabled = false
        )
    )
    val facilityLoadedModel = defaultModel
        .currentFacilityLoaded(facility)

    updateSpec
        .given(facilityLoadedModel)
        .whenEvent(OverdueAppointmentsLoaded(overdueAppointments))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(ShowOverdueAppointments(overdueAppointments, isDiabetesManagementEnabled = true))
        ))
  }
}
