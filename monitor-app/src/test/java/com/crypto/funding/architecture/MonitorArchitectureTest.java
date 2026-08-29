package com.crypto.funding.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Converter;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.crypto.funding")
class MonitorArchitectureTest {
    @ArchTest
    static final ArchRule jpa_entities_live_in_persistence_model = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .resideInAPackage("..infrastructure.persistence.model..");

    @ArchTest
    static final ArchRule jpa_repositories_live_in_persistence_repository = classes()
            .that()
            .areAssignableTo(JpaRepository.class)
            .should()
            .resideInAPackage("..infrastructure.persistence.repository..");

    @ArchTest
    static final ArchRule persistence_annotations_stay_in_persistence = noClasses()
            .that()
            .resideOutsideOfPackage("..infrastructure.persistence..")
            .should()
            .beAnnotatedWith(Entity.class)
            .orShould()
            .beAnnotatedWith(Embeddable.class)
            .orShould()
            .beAnnotatedWith(MappedSuperclass.class)
            .orShould()
            .beAnnotatedWith(Converter.class);

    @ArchTest
    static final ArchRule monitor_controllers_live_in_api =
            classes().that().areAnnotatedWith(RestController.class).should().resideInAPackage("..api..");
}
