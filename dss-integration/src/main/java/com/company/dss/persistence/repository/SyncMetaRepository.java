package com.company.dss.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.company.dss.persistence.entity.SyncMetaEntity;

public interface SyncMetaRepository extends JpaRepository<SyncMetaEntity, Long> {
}
