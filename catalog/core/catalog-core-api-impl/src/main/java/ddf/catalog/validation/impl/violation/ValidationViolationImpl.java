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
package ddf.catalog.validation.impl.violation;

import java.util.Set;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import ddf.catalog.validation.violation.ValidationViolation;

public class ValidationViolationImpl implements ValidationViolation {
    private final Set<String> attributes;

    private final String message;

    private final Severity severity;

    public ValidationViolationImpl(final Set<String> attributes, final String message,
            final Severity severity) {
        this.attributes = attributes;
        this.message = message;
        this.severity = severity;
    }

    @Override
    public Set<String> getAttributes() {
        return attributes;
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ValidationViolationImpl that = (ValidationViolationImpl) o;

        return new EqualsBuilder().append(attributes, that.attributes)
                .append(message, that.message)
                .append(severity, that.severity)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(attributes)
                .append(message)
                .append(severity)
                .toHashCode();
    }
}
