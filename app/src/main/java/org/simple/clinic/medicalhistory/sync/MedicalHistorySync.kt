package org.simple.clinic.medicalhistory.sync

import com.f2prateek.rx.preferences2.Preference
import io.reactivex.Completable
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.medicalhistory.MedicalHistory
import org.simple.clinic.medicalhistory.MedicalHistoryRepository
import org.simple.clinic.sync.ModelSync
import org.simple.clinic.sync.SyncConfig
import org.simple.clinic.sync.SyncCoordinator
import java.util.Optional
import org.simple.clinic.util.read
import javax.inject.Inject
import javax.inject.Named

class MedicalHistorySync @Inject constructor(
    private val syncCoordinator: SyncCoordinator,
    private val repository: MedicalHistoryRepository,
    private val api: MedicalHistorySyncApi,
    @Named("last_medicalhistory_pull_token") private val lastPullToken: Preference<Optional<String>>,
    @Named("sync_config_frequent") private val config: SyncConfig
) : ModelSync {

  override val name: String = "Medical History"

  override val requiresSyncApprovedUser = true

  override fun sync(): Completable = Completable
      .mergeArrayDelayError(
          Completable.fromAction { push() },
          Completable.fromAction { pull() }
      )

  override fun push() {
    syncCoordinator.push(repository, pushNetworkCall = { api.push(toRequest(it)).execute().read()!! })
  }

  override fun pull() {
    val batchSize = config.batchSize
    syncCoordinator.pull(repository, lastPullToken, batchSize) { api.pull(batchSize, it).execute().read()!! }
  }

  override fun syncConfig(): SyncConfig = config

  private fun toRequest(histories: List<MedicalHistory>): MedicalHistoryPushRequest {
    val payloads = histories
        .map {
          it.run {
            MedicalHistoryPayload(
                uuid = uuid,
                patientUuid = patientUuid,
                diagnosedWithHypertension = diagnosedWithHypertension,
                isOnTreatmentForHypertension = Answer.Unanswered,
                hasHadHeartAttack = hasHadHeartAttack,
                hasHadStroke = hasHadStroke,
                hasHadKidneyDisease = hasHadKidneyDisease,
                hasDiabetes = diagnosedWithDiabetes,
                hasHypertension = diagnosedWithHypertension,
                createdAt = createdAt,
                updatedAt = updatedAt,
                deletedAt = deletedAt)
          }
        }
    return MedicalHistoryPushRequest(payloads)
  }
}
