package com.goldys.platform.canonical;

/**
 * Repository for {@link CanonicalGuest}. Deliberately empty this slice: the guest entity is not
 * populated until the reviews slice, so no typed queries are needed yet.
 */
interface CanonicalGuestRepository extends BitemporalRepository<CanonicalGuest> {}
