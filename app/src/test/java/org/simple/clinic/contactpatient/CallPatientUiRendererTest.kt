package org.simple.clinic.contactpatient

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.overdue.AppointmentConfig
import org.simple.clinic.overdue.TimeToAppointment
import org.simple.clinic.overdue.TimeToAppointment.Days
import org.simple.clinic.overdue.TimeToAppointment.Weeks
import org.simple.clinic.patient.Age
import org.simple.clinic.patient.Gender
import java.util.Optional
import org.simple.clinic.util.TestUserClock
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.UUID

class CallPatientUiRendererTest {

  private val patientUuid = UUID.fromString("e6ff79b9-0ac8-4d7b-ada9-7b3056db2972")

  private val ui = mock<ContactPatientUi>()
  private val timeToAppointments = listOf(
      Days(1),
      Weeks(1),
      Weeks(2)
  )
  private val clock = TestUserClock(LocalDate.parse("2018-01-01"))
  private val uiRenderer = ContactPatientUiRenderer(ui, clock)

  @Test
  fun `when the patient details are loaded with date of birth, render the patient details`() {
    // given
    val name = "Anisha Acharya"
    val dateOfBirth = LocalDate.parse("1970-01-01")
    val phoneNumber = "1234567890"
    val gender = Gender.Female

    val patientProfile = TestData.patientProfile(
        patientUuid = patientUuid,
        patientName = name,
        patientPhoneNumber = phoneNumber,
        age = null,
        dateOfBirth = dateOfBirth,
        gender = gender
    )

    // when
    uiRenderer.render(defaultModel().patientProfileLoaded(patientProfile))

    // then
    verify(ui).hideSecureCallUi()
    verify(ui).switchToCallPatientView()

    val expectedAge = 48 // difference between clock date and DOB
    verify(ui).renderPatientDetails(name, gender, expectedAge, phoneNumber)
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when the patient details are loaded with age, render the patient details`() {
    // given
    val name = "Anish Acharya"
    val age = Age(value = 47, updatedAt = Instant.parse("2017-01-01T00:00:00Z"))
    val phoneNumber = "1234567890"
    val gender = Gender.Male

    val patientProfile = TestData.patientProfile(
        patientUuid = patientUuid,
        patientName = name,
        patientPhoneNumber = phoneNumber,
        age = age,
        dateOfBirth = null,
        gender = gender
    )

    // when
    uiRenderer.render(defaultModel().patientProfileLoaded(patientProfile))

    // then
    verify(ui).hideSecureCallUi()
    verify(ui).switchToCallPatientView()

    val expectedAge = 48 // difference between clock date and Age
    verify(ui).renderPatientDetails(name, gender, expectedAge, phoneNumber)
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `display the call result section if there is an overdue appointment`() {
    // given
    val overdueAppointment = TestData.overdueAppointment(
        facilityUuid = UUID.fromString("a607a97f-4bf6-4ce6-86a3-b266059c7734"),
        patientUuid = patientUuid
    )

    // when
    uiRenderer.render(defaultModel().overdueAppointmentLoaded(Optional.of(overdueAppointment)))

    // then
    verify(ui).hideSecureCallUi()
    verify(ui).switchToCallPatientView()

    verify(ui).showCallResultSection()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `hide the call result section if there is no overdue appointment`() {
    // when
    uiRenderer.render(defaultModel().overdueAppointmentLoaded(Optional.empty()))

    // then
    verify(ui).hideSecureCallUi()
    verify(ui).switchToCallPatientView()

    verify(ui).hideCallResultSection()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `if the secure call feature is enabled, show the secure call ui`() {
    // when
    val model = defaultModel(phoneMaskFeatureEnabled = true)
    uiRenderer.render(model)

    // then
    verify(ui).switchToCallPatientView()

    verify(ui).showSecureCallUi()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `if the secure call feature is disabled, hide the secure call ui`() {
    // when
    val model = defaultModel(phoneMaskFeatureEnabled = false)
    uiRenderer.render(model)

    // then
    verify(ui).switchToCallPatientView()

    verify(ui).hideSecureCallUi()
    verifyNoMoreInteractions(ui)
  }

  private fun defaultModel(
      phoneMaskFeatureEnabled: Boolean = false,
      timeToAppointments: List<TimeToAppointment> = this.timeToAppointments
  ): ContactPatientModel {
    val appointmentConfig = AppointmentConfig(
        appointmentDuePeriodForDefaulters = Period.ZERO,
        scheduleAppointmentsIn = emptyList(),
        defaultTimeToAppointment = Days(0),
        periodForIncludingOverdueAppointments = Period.ZERO,
        remindAppointmentsIn = timeToAppointments
    )

    return ContactPatientModel.create(
        patientUuid = patientUuid,
        appointmentConfig = appointmentConfig,
        userClock = clock,
        mode = UiMode.CallPatient,
        secureCallFeatureEnabled = phoneMaskFeatureEnabled
    )
  }
}
