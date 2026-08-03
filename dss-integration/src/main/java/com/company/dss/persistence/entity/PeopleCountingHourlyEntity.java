package com.company.dss.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Créneau horaire People Counting (source DSS history).
 * Clé métier : camera + date + heure de début.
 */
@Getter
@Setter
@Entity
@Table(
        name = "people_counting_hourly",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_counting_slot",
                columnNames = {"camera_id", "slot_date", "hour_start"}
        )
)
public class PeopleCountingHourlyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camera_id", nullable = false)
    private CameraEntity camera;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "hour_start", nullable = false)
    private LocalTime hourStart;

    @Column(name = "hour_end", nullable = false)
    private LocalTime hourEnd;

    @Column(nullable = false)
    private int entries;

    @Column(nullable = false)
    private int exits;

    @Column(nullable = false)
    private int occupancy;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @PrePersist
    @PreUpdate
    void touchSyncedAt() {
        syncedAt = Instant.now();
    }
}
