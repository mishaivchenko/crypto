package com.crypto.funding.telegram;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.crypto.funding")
class TelegramArchitectureTest {
    @ArchTest
    static final ArchRule telegram_does_not_depend_on_monitor_internals = noClasses()
            .that()
            .resideInAPackage("com.crypto.funding.telegram..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.crypto.funding.api..",
                    "com.crypto.funding.application..",
                    "com.crypto.funding.config..",
                    "com.crypto.funding.infrastructure..",
                    "com.crypto.funding.security..",
                    "db.migration..");
}
