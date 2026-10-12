package com.healthscapeai;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface AlertRepository extends JpaRepository<Alert, Long> {
    boolean existsByVillageIdAndStatusNot(Long villageId, String status);
    List<Alert> findAllByOrderByCreatedAtDesc();
}
interface AlertEventRepository extends JpaRepository<AlertEvent, Long> { List<AlertEvent> findByAlertIdOrderByAtAsc(Long alertId); }
interface ObservationRepository extends JpaRepository<Observation, Long> { List<Observation> findTop30ByOrderByCreatedAtDesc(); }

interface AuditLogRepository extends org.springframework.data.jpa.repository.JpaRepository<AuditLog, Long> { java.util.List<AuditLog> findTop50ByOrderByCreatedDesc(); }
