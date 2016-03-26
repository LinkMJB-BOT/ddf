/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.catalog.validation.report;

import java.util.Set;

import ddf.catalog.validation.violation.ValidationViolation;

/**
 * Describes the outcome of validating a single metacard attribute.
 * <p>
 * <b> This code is experimental. While this interface is functional and tested, it may change or be
 * removed in a future version of the library. </b>
 * </p>
 */
public interface AttributeValidationReport {
    /**
     * Returns the set of {@link ValidationViolation}s for the validated attribute.
     *
     * @return the set of violations
     */
    Set<ValidationViolation> getAttributeValidationViolations();

    /**
     * Returns a set of suggested values for the attribute.
     *
     * @return the set of suggested values
     */
    Set<String> getSuggestedValues();
}
