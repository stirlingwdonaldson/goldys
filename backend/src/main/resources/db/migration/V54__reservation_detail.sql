-- Extend canonical_reservation with the rich OpenTable GuestCenter reservation export.
-- Every new column is nullable (the thin 8-column path keeps working) and immutable
-- (updatable = false on the entity); a correction supersedes the whole row, so none of
-- these columns are ever edited in place. Guest ID has no FK this slice: canonical_guest
-- arrives in a later migration.

-- Visit lifecycle
ALTER TABLE canonical_reservation
    ADD COLUMN created_at timestamp(6) with time zone,
    ADD COLUMN cancelled_at timestamp(6) with time zone,
    ADD COLUMN no_show_at timestamp(6) with time zone,
    ADD COLUMN seated_at timestamp(6) with time zone,
    ADD COLUMN finished_at timestamp(6) with time zone,
    ADD COLUMN last_updated_at timestamp(6) with time zone,
    ADD COLUMN total_actual_duration_minutes integer;

-- Operational
ALTER TABLE canonical_reservation
    ADD COLUMN table_type varchar(1024),
    ADD COLUMN dining_area_assigned varchar(1024),
    ADD COLUMN dining_area_booked varchar(1024),
    ADD COLUMN dining_area_id_assigned varchar(1024),
    ADD COLUMN dining_area_id_booked varchar(1024),
    ADD COLUMN wait_staff varchar(1024),
    ADD COLUMN created_by varchar(1024),
    ADD COLUMN turn_time_setting varchar(1024),
    ADD COLUMN reservation_type varchar(1024),
    ADD COLUMN occasion varchar(1024),
    ADD COLUMN reservation_status varchar(1024),
    ADD COLUMN online_waitlist boolean,
    ADD COLUMN is_flex_reso boolean,
    ADD COLUMN quoted_wait_time integer,
    ADD COLUMN total_wait_time integer;

-- Source/marketing
ALTER TABLE canonical_reservation
    ADD COLUMN online_channel varchar(1024),
    ADD COLUMN partner varchar(1024),
    ADD COLUMN campaign_type varchar(1024),
    ADD COLUMN campaign_details varchar(1024),
    ADD COLUMN referrer_name varchar(1024),
    ADD COLUMN referrer_business varchar(1024),
    ADD COLUMN restref_source varchar(1024),
    ADD COLUMN restref_campaign_name varchar(1024),
    ADD COLUMN restref_id varchar(1024);

-- Guest
ALTER TABLE canonical_reservation
    ADD COLUMN guest_first_name varchar(1024),
    ADD COLUMN guest_last_name varchar(1024),
    ADD COLUMN guest_nickname varchar(1024),
    ADD COLUMN guest_email varchar(1024),
    ADD COLUMN guest_phone varchar(1024),
    ADD COLUMN company varchar(1024),
    ADD COLUMN marketing_opt_in boolean,
    ADD COLUMN birthday date,
    ADD COLUMN anniversary date,
    ADD COLUMN first_visit_date date,
    ADD COLUMN recent_visit_date date,
    ADD COLUMN upcoming_visits integer,
    ADD COLUMN completed_visits integer,
    ADD COLUMN cancelled_count integer,
    ADD COLUMN no_show_count integer,
    ADD COLUMN lifetime_spend numeric(19,4),
    ADD COLUMN lifetime_per_cover_spend numeric(19,4),
    ADD COLUMN guest_id uuid;

-- Experience/prepayment
ALTER TABLE canonical_reservation
    ADD COLUMN experience_title varchar(1024),
    ADD COLUMN experience_type varchar(1024),
    ADD COLUMN experience_type_enum varchar(1024),
    ADD COLUMN experience_price_type varchar(1024),
    ADD COLUMN prepayment_method varchar(1024),
    ADD COLUMN prepayment_status varchar(1024),
    ADD COLUMN vendor_payout_status varchar(1024),
    ADD COLUMN currency varchar(16),
    ADD COLUMN experience_price numeric(19,4),
    ADD COLUMN experience_subtotal numeric(19,4),
    ADD COLUMN addons_subtotal numeric(19,4),
    ADD COLUMN service_charges numeric(19,4),
    ADD COLUMN prepaid_experience_tip numeric(19,4),
    ADD COLUMN prepaid_experience_vat numeric(19,4),
    ADD COLUMN prepaid_experience_total_paid numeric(19,4),
    ADD COLUMN experience_total_sales numeric(19,4),
    ADD COLUMN experience_total_sales_with_tip numeric(19,4),
    ADD COLUMN prepayment_required_total numeric(19,4),
    ADD COLUMN prepayment_paid_total numeric(19,4);

-- Payment/revenue
ALTER TABLE canonical_reservation
    ADD COLUMN pos_check_ids varchar(1024),
    ADD COLUMN pos_subtotal numeric(19,4),
    ADD COLUMN pos_tax numeric(19,4),
    ADD COLUMN pos_service_charges numeric(19,4),
    ADD COLUMN pos_tip numeric(19,4),
    ADD COLUMN pos_paid numeric(19,4),
    ADD COLUMN pos_due numeric(19,4),
    ADD COLUMN total_tips numeric(19,4),
    ADD COLUMN total_vat numeric(19,4),
    ADD COLUMN total_income numeric(19,4),
    ADD COLUMN total_income_with_tip numeric(19,4),
    ADD COLUMN total_revenue numeric(19,4),
    ADD COLUMN total_revenue_with_tip numeric(19,4),
    ADD COLUMN total_revenue_without_tip numeric(19,4),
    ADD COLUMN pos_spend numeric(19,4),
    ADD COLUMN pos_refund numeric(19,4),
    ADD COLUMN pos_revenue numeric(19,4),
    ADD COLUMN refund_amount numeric(19,4),
    ADD COLUMN vendor_payout_amount numeric(19,4);

-- Notes/tags
ALTER TABLE canonical_reservation
    ADD COLUMN guest_requests varchar(1024),
    ADD COLUMN visit_notes varchar(1024),
    ADD COLUMN reservation_tags varchar(1024),
    ADD COLUMN guest_tags varchar(1024);
