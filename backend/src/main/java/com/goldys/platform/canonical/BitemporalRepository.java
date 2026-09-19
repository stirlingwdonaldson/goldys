package com.goldys.platform.canonical;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Common repository base for bitemporal entities.
 *
 * <p>Deliberately carries no generic current/as-of query strings: each concrete entity's time and
 * fact semantics differ, so its own repository declares the typed queries it actually needs.
 */
@NoRepositoryBean
public interface BitemporalRepository<T extends BitemporalEntity> extends JpaRepository<T, UUID> {}
