package com.company.dss.persistence.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.dss.persistence.entity.PeopleCountingHourlyEntity;

public interface PeopleCountingHourlyRepository extends JpaRepository<PeopleCountingHourlyEntity, Long> {

    Optional<PeopleCountingHourlyEntity> findByCameraIdAndSlotDateAndHourStart(
            Long cameraId,
            LocalDate slotDate,
            LocalTime hourStart
    );

    @Query("""
            select h from PeopleCountingHourlyEntity h
            join fetch h.camera c
            where h.slotDate in :dates
              and c.id in :cameraIds
            """)
    List<PeopleCountingHourlyEntity> findForUpsert(
            @Param("dates") Collection<LocalDate> dates,
            @Param("cameraIds") Collection<Long> cameraIds
    );

    /**
     * Toutes les caméras — même borne horaire inclusive que {@link #findForReportByChannels}.
     * Méthode séparée de {@link #findForReportByChannels} : éviter le motif
     * {@code :flag = true OR x IN :ids} (bug de binding Hibernate / H2 → 0 ligne).
     */
    @Query("""
            select h from PeopleCountingHourlyEntity h
            join fetch h.camera c
            where h.slotDate between :fromDate and :toDate
              and h.hourStart >= :timeFrom
              and h.hourStart <= :lastHourStart
            order by h.slotDate, h.hourStart, c.name
            """)
    List<PeopleCountingHourlyEntity> findForReportAll(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("timeFrom") LocalTime timeFrom,
            @Param("lastHourStart") LocalTime lastHourStart
    );

    /**
     * Uniquement les channelIds demandés (IN).
     * Tranche inclusive sur {@code hourStart} : du premier créneau au dernier de la liste UI
     * (ex. 09:00–11:00 → 09:00 et 10:00 ; journée complète → 00:00 à 23:00).
     */
    @Query("""
            select h from PeopleCountingHourlyEntity h
            join fetch h.camera c
            where h.slotDate between :fromDate and :toDate
              and h.hourStart >= :timeFrom
              and h.hourStart <= :lastHourStart
              and c.channelId in :channelIds
            order by h.slotDate, h.hourStart, c.name
            """)
    List<PeopleCountingHourlyEntity> findForReportByChannels(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("timeFrom") LocalTime timeFrom,
            @Param("lastHourStart") LocalTime lastHourStart,
            @Param("channelIds") Collection<String> channelIds
    );

    @Query("select max(h.slotDate) from PeopleCountingHourlyEntity h")
    Optional<LocalDate> findMaxSlotDate();

    @Query("select min(h.slotDate) from PeopleCountingHourlyEntity h")
    Optional<LocalDate> findMinSlotDate();

    /** Dates distinctes ayant au moins un créneau en base (inclusives). */
    @Query("""
            select distinct h.slotDate from PeopleCountingHourlyEntity h
            where h.slotDate between :fromDate and :toDate
            order by h.slotDate
            """)
    List<LocalDate> findDistinctSlotDatesBetween(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            select h from PeopleCountingHourlyEntity h
            join fetch h.camera c
            where h.slotDate between :fromDate and :toDate
              and c.channelId in :channelIds
            """)
    List<PeopleCountingHourlyEntity> findForReconcile(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("channelIds") Collection<String> channelIds
    );

    @Query("""
            select h from PeopleCountingHourlyEntity h
            join fetch h.camera c
            where h.finalized = false
              and (h.slotDate < :date
                   or (h.slotDate = :date and h.hourEnd <= :time))
            """)
    List<PeopleCountingHourlyEntity> findUnfinalizedBefore(
            @Param("date") LocalDate date,
            @Param("time") LocalTime time
    );
}
