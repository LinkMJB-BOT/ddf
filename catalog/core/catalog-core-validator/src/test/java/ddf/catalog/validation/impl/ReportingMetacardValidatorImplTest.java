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
package ddf.catalog.validation.impl;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;

import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.validation.AttributeValidatorRegistry;
import ddf.catalog.validation.ValidationException;
import ddf.catalog.validation.impl.validator.EnumerationValidator;
import ddf.catalog.validation.impl.validator.FutureDateValidator;
import ddf.catalog.validation.impl.validator.PastDateValidator;
import ddf.catalog.validation.impl.validator.PatternValidator;
import ddf.catalog.validation.impl.validator.SizeValidator;
import ddf.catalog.validation.report.MetacardValidationReport;

public class ReportingMetacardValidatorImplTest {
    private AttributeValidatorRegistry registry;

    private ReportingMetacardValidatorImpl validator;

    private static final Date PAST_DATE = Date.from(Instant.now()
            .minus(5, ChronoUnit.DAYS));

    private static final Date FUTURE_DATE = Date.from(Instant.now()
            .plus(5, ChronoUnit.DAYS));

    @Before
    public void setup() {
        registry = new AttributeValidatorRegistryImpl();
        validator = new ReportingMetacardValidatorImpl(registry);
        registerValidators();
    }

    private void registerValidators() {
        registry.registerValidators(Metacard.TITLE,
                Sets.newHashSet(new SizeValidator(1, 20), new PatternValidator("[A-Z]+")));
        registry.registerValidators(Metacard.MODIFIED, Sets.newHashSet(PastDateValidator.getInstance()));
        registry.registerValidators(Metacard.EFFECTIVE, Sets.newHashSet(FutureDateValidator.getInstance()));
        final EnumerationValidator enumerationValidator = new EnumerationValidator(Sets.newHashSet(
                "application/xml",
                "text/xml"));
        registry.registerValidators(Metacard.CONTENT_TYPE, Sets.newHashSet(enumerationValidator));
    }

    private Metacard getValidMetacard() {
        final MetacardImpl metacard = new MetacardImpl();
        metacard.setTitle("VALIDTITLE");
        metacard.setModifiedDate(PAST_DATE);
        metacard.setEffectiveDate(FUTURE_DATE);
        metacard.setContentTypeName("application/xml");
        return metacard;
    }

    private Metacard getInvalidMetacard() {
        final MetacardImpl metacard = new MetacardImpl();
        metacard.setTitle("title is too long and doesn't match the pattern");
        metacard.setModifiedDate(FUTURE_DATE);
        metacard.setEffectiveDate(PAST_DATE);
        metacard.setContentTypeName("application/json");
        return metacard;
    }

    @Test
    public void testValidateValidMetacardByReport() {
        final MetacardValidationReport report = validator.validateMetacard(getValidMetacard());
        assertThat(report.getAttributeValidationViolations(), empty());
        assertThat(report.getMetacardValidationViolations(), empty());
    }

    @Test
    public void testValidateInvalidMetacardByReport() {
        final MetacardValidationReport report = validator.validateMetacard(getInvalidMetacard());
        // 2 title violations and 1 violation each for modified, effective, and content type
        assertThat(report.getAttributeValidationViolations(), hasSize(5));
        assertThat(report.getMetacardValidationViolations(), empty());

        final List<String> violatedAttributes = report.getAttributeValidationViolations()
                .stream()
                .flatMap(violation -> violation.getAttributes()
                        .stream())
                .collect(Collectors.toList());
        assertThat(violatedAttributes,
                containsInAnyOrder(Metacard.TITLE,
                        Metacard.TITLE,
                        Metacard.MODIFIED,
                        Metacard.EFFECTIVE,
                        Metacard.CONTENT_TYPE));
    }

    @Test
    public void testValidateValidMetacardByException() {
        try {
            validator.validate(getValidMetacard());
        } catch (ValidationException e) {
            assertThat(e.getErrors(), nullValue());
            assertThat(e.getWarnings(), nullValue());
        }
    }

    @Test
    public void testValidateInvalidMetacardByException() {
        try {
            validator.validate(getInvalidMetacard());
        } catch (ValidationException e) {
            // 2 title violations and 1 violation each for modified, effective, and content type
            assertThat(e.getErrors(), hasSize(5));
            assertThat(e.getWarnings(), nullValue());
        }
    }
}
