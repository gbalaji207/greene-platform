package com.greene.training.repository

import com.greene.training.entity.Batch
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BatchRepository : JpaRepository<Batch, UUID>

