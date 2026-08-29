package com.crypto.funding.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.crypto.funding")
class PlatformCoreArchitectureTest {
    @ArchTest
    static final ArchRule platform_core_stays_framework_and_persistence_free = noClasses()
            .that()
            .resideInAPackage("com.crypto.funding..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "javax.persistence..",
                    "org.hibernate..",
                    "org.flywaydb..",
                    "com.crypto.funding.infrastructure.persistence..");
}
