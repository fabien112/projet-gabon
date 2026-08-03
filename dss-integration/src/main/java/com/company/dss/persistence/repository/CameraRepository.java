package com.company.dss.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.company.dss.persistence.entity.CameraEntity;

public interface CameraRepository extends JpaRepository<CameraEntity, Long> {

    Optional<CameraEntity> findByChannelId(String channelId);
}
