package com.company.dss.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Métadonnées de synchronisation manuelle (1 seule ligne id=1).
 */
@Getter
@Setter
@Entity
@Table(name = "sync_meta")
public class SyncMetaEntity {

    @Id
    private Long id = 1L;

    private Instant lastStartedAt;
    private Instant lastFinishedAt;

    private LocalDate lastFromDate;
    private LocalDate lastToDate;

    @Column(length = 32)
    private String lastStatus;

    @Column(length = 500)
    private String lastMessage;

    private int lastDaysProcessed;
    private int lastRowsUpserted;
}
