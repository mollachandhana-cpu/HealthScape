package com.healthscapeai;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnvironmentalSnapshotRepository extends JpaRepository<EnvironmentalSnapshot,Long> {
    List<EnvironmentalSnapshot> findTop24ByLatitudeBetweenAndLongitudeBetweenOrderByCapturedAtDesc(double latMin,double latMax,double lonMin,double lonMax);
}
