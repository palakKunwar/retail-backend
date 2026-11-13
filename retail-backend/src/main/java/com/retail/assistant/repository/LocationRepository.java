package com.retail.assistant.repository;

import com.retail.assistant.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationRepository
        extends JpaRepository<Location, Long> {
}