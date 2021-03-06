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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.fileinstall.ArtifactInstaller;
import org.boon.json.JsonFactory;
import org.boon.json.annotations.JsonIgnore;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.AttributeRegistry;
import ddf.catalog.data.MetacardType;
import ddf.catalog.data.impl.AttributeDescriptorImpl;
import ddf.catalog.data.impl.BasicTypes;
import ddf.catalog.data.impl.MetacardTypeImpl;
import ddf.catalog.validation.AttributeValidator;
import ddf.catalog.validation.AttributeValidatorRegistry;
import ddf.catalog.validation.MetacardValidator;
import ddf.catalog.validation.impl.validator.EnumerationValidator;
import ddf.catalog.validation.impl.validator.FutureDateValidator;
import ddf.catalog.validation.impl.validator.PastDateValidator;
import ddf.catalog.validation.impl.validator.PatternValidator;
import ddf.catalog.validation.impl.validator.RangeValidator;
import ddf.catalog.validation.impl.validator.RequiredAttributesMetacardValidator;
import ddf.catalog.validation.impl.validator.SizeValidator;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings
public class ValidationParser implements ArtifactInstaller {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationParser.class);

    private AttributeRegistry attributeRegistry;

    private AttributeValidatorRegistry attributeValidatorRegistry;

    private static Map<String, Outer> sourceMap = new ConcurrentHashMap<>();

    @Override
    public void install(File file) throws Exception {
        String data;
        try (InputStream input = new FileInputStream(file)) {
            data = IOUtils.toString(input, StandardCharsets.UTF_8.name());
            LOGGER.debug("Installing file [{}}. Contents:\n{}", file.getAbsolutePath(), data);
        }
        if (StringUtils.isEmpty(data)) {
            LOGGER.warn("File is empty [{}]", file.getAbsolutePath());
            return; /* nothing to install */
        }

        Outer outer;
        try {
            outer = JsonFactory.create()
                    .readValue(data, Outer.class);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Cannot parse json [" + file.getAbsolutePath() + "]",
                    e);
        }

        /* Must manually parse validators */
        Map<String, Object> root = JsonFactory.create()
                .parser()
                .parseMap(data);
        parseValidators(root, outer);

        sourceMap.put(file.getName(), outer);
        List<Callable<Boolean>> stagedAdds = new ArrayList<>();

        try {
            if (outer.attributeTypes != null) {
                stagedAdds.addAll(parseAttributeTypes(outer.attributeTypes));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Could not parse Attribute Types section of [" + file.getAbsolutePath() + "]",
                    e);
        }
        LOGGER.debug("Committing staged attribute type definitions.");
        commitStaged(stagedAdds);

        try {
            if (outer.metacardTypes != null) {
                stagedAdds.addAll(parseMetacardTypes(outer.metacardTypes));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Could not parse Metacard Types section of [" + file.getAbsolutePath() + "]",
                    e);
        }
        LOGGER.debug("Committing staged metacard type definitions.");
        commitStaged(stagedAdds);

        try {
            if (outer.validators != null) {
                stagedAdds.addAll(parseValidators(outer.validators));

            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Could not parse Validators section of [" + file.getAbsolutePath() + "]", e);
        }
        LOGGER.debug("Committing staged validator definitions.");
        commitStaged(stagedAdds);
    }

    private void commitStaged(Collection<Callable<Boolean>> stagedAdds) throws Exception {
        for (Callable<Boolean> staged : stagedAdds) {
            try {
                staged.call();
            } catch (RuntimeException e) {
                LOGGER.warn("Error adding staged item {}", staged, e);
            }
        }
        stagedAdds.clear();
    }

    @Override
    public void update(File file) throws Exception {
    }

    @Override
    public void uninstall(File file) throws Exception {
    }

    @Override
    public boolean canHandle(File file) {
        return file.getName()
                .endsWith(".json");
    }

    public void setAttributeRegistry(AttributeRegistry attributeRegistry) {
        this.attributeRegistry = attributeRegistry;
    }

    public void setAttributeValidatorRegistry(
            AttributeValidatorRegistry attributeValidatorRegistry) {
        this.attributeValidatorRegistry = attributeValidatorRegistry;
    }

    @SuppressWarnings("unchecked")
    private void parseValidators(Map<String, Object> root, Outer outer) {
        if (root == null || root.get("validators") == null) {
            return;
        }

        Map<String, List<Outer.Validator>> validators = new HashMap<>();
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) root.get("validators")).entrySet()) {
            String rejson = JsonFactory.create()
                    .toJson(entry.getValue());
            List<Outer.Validator> lv = JsonFactory.create()
                    .readValue(rejson, List.class, Outer.Validator.class);
            validators.put(entry.getKey(), lv);
        }
        outer.validators = validators;
    }

    private List<Callable<Boolean>> parseAttributeTypes(
            Map<String, Outer.AttributeType> attributeTypes) {
        List<Callable<Boolean>> staged = new ArrayList<>();
        for (Map.Entry<String, Outer.AttributeType> entry : attributeTypes.entrySet()) {
            final AttributeDescriptor descriptor = new AttributeDescriptorImpl(entry.getKey(),
                    entry.getValue().indexed,
                    entry.getValue().stored,
                    entry.getValue().tokenized,
                    entry.getValue().multivalued,
                    BasicTypes.getAttributeType(entry.getValue().type));

            staged.add(() -> attributeRegistry.registerAttribute(descriptor));
        }
        return staged;
    }

    private List<Callable<Boolean>> parseMetacardTypes(List<Outer.MetacardType> metacardTypes) {
        List<Callable<Boolean>> staged = new ArrayList<>();
        Bundle bundle = FrameworkUtil.getBundle(this.getClass());
        if (bundle == null) {
            LOGGER.warn(String.format(
                    "Unable to get bundle for [%s], Metacard Types will not be registered",
                    this.getClass()
                            .getName()));
            return new ArrayList<>();
        }
        BundleContext context = bundle.getBundleContext();
        for (Outer.MetacardType metacardType : metacardTypes) {
            Set<AttributeDescriptor> attributeDescriptors =
                    new HashSet<>(BasicTypes.BASIC_METACARD.getAttributeDescriptors());
            Set<String> requiredAttributes = new HashSet<>();

            for (Map.Entry<String, Outer.MetacardAttribute> entry : metacardType.attributes.entrySet()) {
                String attributeName = entry.getKey();
                AttributeDescriptor descriptor = attributeRegistry.getAttributeDescriptor(
                        attributeName)
                        .orElseThrow(() -> new IllegalStateException(String.format(
                                "Metacard type [%s] includes the attribute [%s], but that attribute is not in the attribute registry.",
                                metacardType.type,
                                attributeName)));
                attributeDescriptors.add(descriptor);
                if (entry.getValue().required) {
                    requiredAttributes.add(attributeName);
                }
            }
            if (!requiredAttributes.isEmpty()) {
                final MetacardValidator validator = new RequiredAttributesMetacardValidator(
                        metacardType.type,
                        requiredAttributes);
                staged.add(() -> context.registerService(MetacardValidator.class, validator, null)
                        != null);
            }
            Dictionary<String, Object> properties = new Hashtable<>();
            properties.put("name", metacardType.type);
            MetacardType type = new MetacardTypeImpl(metacardType.type, attributeDescriptors);
            staged.add(() -> context.registerService(MetacardType.class, type, properties) != null);
        }
        return staged;
    }

    private List<Callable<Boolean>> parseValidators(Map<String, List<Outer.Validator>> validators) {
        List<Callable<Boolean>> staged = new ArrayList<>();
        for (Map.Entry<String, List<Outer.Validator>> entry : validators.entrySet()) {
            Set<AttributeValidator> attributeValidators = validatorFactory(entry.getValue());
            staged.add(() -> {
                attributeValidatorRegistry.registerValidators(entry.getKey(), attributeValidators);
                return true;
            });
        }
        return staged;
    }

    private Set<AttributeValidator> validatorFactory(List<Outer.Validator> validators) {
        return validators.stream()
                .filter(Objects::nonNull)
                .filter(v -> StringUtils.isNotBlank(v.validator))
                .map(this::getValidator)
                .collect(Collectors.toSet());
    }

    private AttributeValidator getValidator(Outer.Validator validator) {
        switch (validator.validator) {
        case "size": {
            long lmin = Long.parseLong(validator.arguments.get(0));
            long lmax = Long.parseLong(validator.arguments.get(1));
            return new SizeValidator(lmin, lmax);
        }
        case "pattern": {
            String regex = validator.arguments.get(0);
            return new PatternValidator(regex);
        }
        case "pastdate": {
            return PastDateValidator.getInstance();
        }
        case "futuredate": {
            return FutureDateValidator.getInstance();
        }
        case "enumeration": {
            Set<String> values = new HashSet<>(validator.arguments);
            return new EnumerationValidator(values);
        }
        case "range": {
            BigDecimal min = new BigDecimal(validator.arguments.get(0));
            BigDecimal max = new BigDecimal(validator.arguments.get(1));
            if (validator.arguments.size() > 2) {
                BigDecimal epsilon = new BigDecimal(validator.arguments.get(2));
                return new RangeValidator(min, max, epsilon);
            }
            return new RangeValidator(min, max);
        }
        default:
            throw new IllegalStateException(
                    "Validator does not exist. (" + validator.validator + ")");
        }
    }

    private class Outer {
        List<MetacardType> metacardTypes;

        Map<String, AttributeType> attributeTypes;

        @JsonIgnore
        Map<String, List<Validator>> validators;

        class MetacardType {
            String type;

            Map<String, MetacardAttribute> attributes;
        }

        class MetacardAttribute {
            boolean required;
        }

        class AttributeType {
            String type;

            boolean tokenized;

            boolean stored;

            boolean indexed;

            boolean multivalued;
        }

        class Validator {
            String validator;

            List<String> arguments;
        }
    }

}
