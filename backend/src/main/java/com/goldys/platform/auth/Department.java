package com.goldys.platform.auth;

/**
 * One axis of the role model (spec Requirement 3). ALL means "not
 * department-scoped" - e.g. an Owner's access, or a field that isn't
 * department-specific to begin with.
 *
 * Extensibility contract: adding a new value here (e.g. BAR) must NOT require
 * changing any existing Permission row - only new rows granting BAR access
 * are added. If adding a department ever forces you to touch unrelated
 * permissions, the model has drifted from spec Requirement 3's acceptance
 * criteria - fix the model, not the enum.
 */
public enum Department {
    BOH,
    FOH,
    ALL
}
