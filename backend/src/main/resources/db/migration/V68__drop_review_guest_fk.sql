-- Correct the canonical_review.guest_id link. V65 declared `guest_id uuid REFERENCES
-- canonical_guest(id)`, but the entity stores the guest's logical identity there —
-- `UUID.nameUUIDFromBytes("guest:" + Guest ID)`, the same value canonical_guest.logical_entity_id
-- holds — so a review links to its guest without a matching step. A row's `id` is a random per-row
-- UUID (BitemporalEntity), and the logical identity is shared across superseded versions, so the FK
-- can never be satisfied and rejected every review carrying a Guest ID. Drop it: guest_id remains a
-- denormalized logical-id link backed by ix_review_guest_id, matching canonical_reservation.guest_id
-- (no FK) and the natural-key links used elsewhere (e.g. canonical_sale_recipe_link.recipe_id).

ALTER TABLE canonical_review DROP CONSTRAINT canonical_review_guest_id_fkey;
