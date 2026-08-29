package com.crypto.funding.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.crypto.funding")
class EngineArchitectureTest {
    @ArchTest
    static final ArchRule engine_stays_free_of_persistence_dependencies = noClasses()
            .that()
            .resideInAPackage("com.crypto.funding.engine..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "jakarta.persistence..",
                    "javax.persistence..",
                    "org.hibernate..",
                    "org.flywaydb..",
                    "com.crypto.funding.infrastructure.persistence..");
}
