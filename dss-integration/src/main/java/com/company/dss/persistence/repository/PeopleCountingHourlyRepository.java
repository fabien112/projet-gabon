package com.company.dss.persistence.repository;

import java.time.LocalDate;
import java.time.LocalTime;
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
            where h.slotDate between :fromDate and :toDate
              and h.hourStart >= :timeFrom
              and h.hourStart < :timeTo
              and (:channelId is null or c.channelId = :channelId)
            order by h.slotDate, h.hourStart, c.name
            """)
    List<PeopleCountingHourlyEntity> findForReport(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("timeFrom") LocalTime timeFrom,
            @Param("timeTo") LocalTime timeTo,
            @Param("channelId") String channelId
    );

    @Query("select max(h.slotDate) from PeopleCountingHourlyEntity h")
    Optional<LocalDate> findMaxSlotDate();
}
