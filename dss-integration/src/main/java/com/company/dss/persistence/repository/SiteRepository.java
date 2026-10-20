package com.company.dss.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.company.dss.persistence.entity.SiteEntity;

public interface SiteRepository extends JpaRepository<SiteEntity, Long> {

    Optional<SiteEntity> findByNameIgnoreCase(String name);
}
