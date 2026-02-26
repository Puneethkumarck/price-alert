package com.pricealert.notifier;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String BASE_PACKAGE = "com.pricealert.notifier";

    private final JavaClasses classes =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(BASE_PACKAGE);

    @Test
    void shouldEnforceHexagonalLayerDependencies() {
        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Application")
                .definedBy(BASE_PACKAGE + ".application..")
                .layer("Domain")
                .definedBy(BASE_PACKAGE + ".domain..")
                .layer("Infrastructure")
                .definedBy(BASE_PACKAGE + ".infrastructure..")
                .whereLayer("Domain")
                .mayNotAccessAnyLayer()
                .whereLayer("Application")
                .mayOnlyAccessLayers("Domain", "Infrastructure")
                .whereLayer("Infrastructure")
                .mayOnlyAccessLayers("Domain", "Application")
                .check(classes);
    }
}
