package org.simple.clinic.main

import com.spotify.mobius.rx2.RxMobius
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.storage.MemoryValue
import org.simple.clinic.user.NewlyVerifiedUser
import org.simple.clinic.user.UserSession
import java.util.Optional
import org.simple.clinic.util.UtcClock
import org.simple.clinic.util.filterTrue
import org.simple.clinic.util.scheduler.SchedulersProvider
import org.simple.clinic.util.toOptional
import java.time.Instant

class TheActivityEffectHandler @AssistedInject constructor(
    private val schedulers: SchedulersProvider,
    private val userSession: UserSession,
    private val utcClock: UtcClock,
    private val patientRepository: PatientRepository,
    private val lockAfterTimestamp: MemoryValue<Optional<Instant>>,
    @Assisted private val uiActions: TheActivityUiActions
) {

  @AssistedFactory
  interface InjectionFactory {
    fun create(uiActions: TheActivityUiActions): TheActivityEffectHandler
  }

  fun build(): ObservableTransformer<TheActivityEffect, TheActivityEvent> {
    return RxMobius
        .subtypeEffectHandler<TheActivityEffect, TheActivityEvent>()
        .addTransformer(LoadAppLockInfo::class.java, loadShowAppLockInto())
        .addAction(ClearLockAfterTimestamp::class.java, lockAfterTimestamp::clear)
        .addAction(ShowAppLockScreen::class.java, uiActions::showAppLockScreen, schedulers.ui())
        .addTransformer(ListenForUserVerifications::class.java, listenForUserVerifications())
        .addAction(ShowUserLoggedOutOnOtherDeviceAlert::class.java, uiActions::showUserLoggedOutOnOtherDeviceAlert, schedulers.ui())
        .addTransformer(ListenForUserUnauthorizations::class.java, listenForUserUnauthorizations())
        .addAction(RedirectToLoginScreen::class.java, uiActions::redirectToLogin, schedulers.ui())
        .addTransformer(ListenForUserDisapprovals::class.java, listenForUserDisapprovals())
        .addTransformer(ClearPatientData::class.java, clearPatientData())
        .addTransformer(ShowAccessDeniedScreen::class.java, openAccessDeniedScreen())
        .build()
  }

  private fun loadShowAppLockInto(): ObservableTransformer<LoadAppLockInfo, TheActivityEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(schedulers.io())
          .map { userSession.loggedInUserImmediate().toOptional() }
          .map {
            AppLockInfoLoaded(
                user = it,
                currentTimestamp = Instant.now(utcClock),
                lockAtTimestamp = lockAfterTimestamp.get()
            )
          }
    }
  }

  private fun listenForUserVerifications(): ObservableTransformer<ListenForUserVerifications, TheActivityEvent> {
    return ObservableTransformer { effects ->
      effects
          .switchMap {
            userSession
                .loggedInUser()
                .subscribeOn(schedulers.io())
                .compose(NewlyVerifiedUser())
                .map { UserWasJustVerified }
          }
    }
  }

  private fun listenForUserUnauthorizations(): ObservableTransformer<ListenForUserUnauthorizations, TheActivityEvent> {
    return ObservableTransformer { effects ->
      effects
          .switchMap {
            userSession
                .isUserUnauthorized()
                .subscribeOn(schedulers.io())
                .distinctUntilChanged()
                .filterTrue()
                .map { UserWasUnauthorized }
          }
    }
  }

  private fun listenForUserDisapprovals(): ObservableTransformer<ListenForUserDisapprovals, TheActivityEvent> {
    return ObservableTransformer { effects ->
      effects
          .switchMap {
            userSession
                .isUserDisapproved()
                .subscribeOn(schedulers.io())
                .filterTrue()
                .map { UserWasDisapproved }
          }
    }
  }

  private fun clearPatientData(): ObservableTransformer<ClearPatientData, TheActivityEvent> {
    return ObservableTransformer { effects ->
      effects
          .switchMap {
            patientRepository
                .clearPatientData()
                .subscribeOn(schedulers.io())
                .andThen(Observable.just(PatientDataCleared))
          }
    }
  }

  private fun openAccessDeniedScreen(): ObservableTransformer<ShowAccessDeniedScreen, TheActivityEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(schedulers.io())
          .map { userSession.loggedInUserImmediate()!!.fullName }
          .observeOn(schedulers.ui())
          .doOnNext(uiActions::showAccessDeniedScreen)
          .flatMap { Observable.empty<TheActivityEvent>() }
    }
  }
}
