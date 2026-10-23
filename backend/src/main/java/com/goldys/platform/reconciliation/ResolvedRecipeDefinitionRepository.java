package com.goldys.platform.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

interface ResolvedRecipeDefinitionRepository
    extends JpaRepository<ResolvedRecipeDefinition, Integer> {}
