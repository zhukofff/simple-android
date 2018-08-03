package org.simple.clinic.patient

import android.arch.persistence.db.SimpleSQLiteQuery
import android.arch.persistence.db.SupportSQLiteOpenHelper
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Embedded
import android.arch.persistence.room.Query
import android.arch.persistence.room.Transaction
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import org.intellij.lang.annotations.Language
import org.simple.clinic.patient.sync.PatientPayload
import org.simple.clinic.patient.sync.PatientPhoneNumberPayload
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import java.util.UUID

data class PatientSearchResult(

    val uuid: UUID,

    val fullName: String,

    val gender: Gender,

    val dateOfBirth: LocalDate?,

    @Embedded(prefix = "age_")
    val age: Age?,

    val status: PatientStatus,

    val createdAt: Instant,

    val updatedAt: Instant,

    val syncStatus: SyncStatus,

    @Embedded(prefix = "addr_")
    val address: PatientAddress,

    val phoneUuid: UUID?,

    val phoneNumber: String?,

    val phoneType: PatientPhoneNumberType?,

    val phoneActive: Boolean?,

    val phoneCreatedAt: Instant?,

    val phoneUpdatedAt: Instant?
) {

  @Dao
  interface RoomDao {

    companion object {
      @Language("RoomSql")
      const val mainQuery = """
          SELECT P.uuid, P.fullName, P.gender, P.dateOfBirth, P.age_value, P.age_updatedAt, P.age_computedDateOfBirth, P.status, P.createdAt, P.updatedAt, P.syncStatus,
          PA.uuid addr_uuid, PA.colonyOrVillage addr_colonyOrVillage, PA.district addr_district, PA.state addr_state, PA.country addr_country,
          PA.createdAt addr_createdAt, PA.updatedAt addr_updatedAt,
          PP.uuid phoneUuid, PP.number phoneNumber, PP.phoneType phoneType, PP.active phoneActive, PP.createdAt phoneCreatedAt, PP.updatedAt phoneUpdatedAt
          FROM Patient P
          INNER JOIN PatientAddress PA on PA.uuid = P.addressUuid
          LEFT JOIN PatientPhoneNumber PP ON PP.patientUuid = P.uuid
    """
    }

    @Query("""$mainQuery WHERE P.uuid IN (:uuids)""")
    fun searchByIds(uuids: List<UUID>): Single<List<PatientSearchResult>>

    @Query("$mainQuery WHERE P.searchableName LIKE '%' || :query || '%' OR PP.number LIKE '%' || :query || '%'")
    fun search(query: String): Flowable<List<PatientSearchResult>>

    @Query("""$mainQuery
      WHERE (P.searchableName LIKE '%' || :query || '%' OR PP.number LIKE '%' || :query || '%')
      AND ((P.dateOfBirth BETWEEN :dobUpperBound AND :dobLowerBound) OR (P.age_computedDateOfBirth BETWEEN :dobUpperBound AND :dobLowerBound))
      """)
    fun search(query: String, dobUpperBound: String, dobLowerBound: String): Flowable<List<PatientSearchResult>>

    @Query("""$mainQuery
      WHERE ((P.dateOfBirth BETWEEN :dobUpperBound AND :dobLowerBound)
             OR (P.age_computedDateOfBirth BETWEEN :dobUpperBound AND :dobLowerBound))
      """)
    fun search(dobUpperBound: String, dobLowerBound: String): Flowable<List<PatientSearchResult>>

    @Query("$mainQuery ORDER BY P.updatedAt DESC LIMIT 100")
    fun recentlyUpdated100Records(): Flowable<List<PatientSearchResult>>

    @Transaction
    @Query("$mainQuery WHERE P.syncStatus == :status")
    fun withSyncStatus(status: SyncStatus): Flowable<List<PatientSearchResult>>
  }

  interface FuzzyPatientSearchDao {

    fun updateFuzzySearchTableForPatients(uuids: List<UUID>): Completable

    fun searchForPatientsWithNameLike(query: String): Single<List<PatientSearchResult>>
  }

  class FuzzyPatientSearchDaoImpl(
      private val sqLiteOpenHelper: SupportSQLiteOpenHelper,
      private val patientSearchDao: PatientSearchResult.RoomDao
  ) : FuzzyPatientSearchDao {

    override fun updateFuzzySearchTableForPatients(uuids: List<UUID>) =
        Completable.fromAction {
          sqLiteOpenHelper.writableDatabase.execSQL("""
            INSERT OR REPLACE INTO "PatientFuzzySearch" ("rowid","word")
            SELECT "rowid","searchableName" FROM "Patient" WHERE "uuid" in (${uuids.joinToString(",")})
            """.trimIndent())
        }!!

    override fun searchForPatientsWithNameLike(query: String) =
        Single.fromCallable {
          val searchQuery = SimpleSQLiteQuery("""
            SELECT "P"."uuid" "uuid"
            FROM "Patient" "P" INNER JOIN "PatientFuzzySearch" "PFS"
              ON "P"."rowid"="PFS"."rowid" WHERE "PFS"."word" MATCH '$query*' AND "score" < 1000 AND "top"=5
            """.trimIndent())

          sqLiteOpenHelper.readableDatabase.query(searchQuery)
              .use { cursor ->
                val uuidColumnIndex = cursor.getColumnIndex("uuid")

                generateSequence { cursor.takeIf { it.moveToNext() } }
                    .map { UUID.fromString(it.getString(uuidColumnIndex)) }
                    .toList()
              }

        }.flatMap { patientSearchDao.searchByIds(it) }!!
  }

  fun toPayload(): PatientPayload {
    val payload = PatientPayload(
        uuid = uuid,
        fullName = fullName,
        gender = gender,
        dateOfBirth = dateOfBirth,
        age = age?.value,
        ageUpdatedAt = age?.updatedAt,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        phoneNumbers = null,
        address = address.toPayload())

    if (phoneUuid != null && phoneNumber != null) {
      return payload.copy(
          phoneNumbers = listOf(PatientPhoneNumberPayload(
              uuid = phoneUuid,
              number = phoneNumber,
              type = phoneType!!,
              active = phoneActive!!,
              createdAt = phoneCreatedAt!!,
              updatedAt = phoneUpdatedAt!!
          )))
    }

    return payload
  }
}
