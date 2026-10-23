package com.deepfine.inventory;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 계층형 구조의 의존 방향을 테스트로 고정한다. 어기면 빌드가 실패한다.
 * 허용 방향: controller → service → repository. domain은 모두가 공유한다. dto는 controller만 쓴다. common → domain.
 */
@AnalyzeClasses(packages = "com.deepfine.inventory", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String CONTROLLER = "com.deepfine.inventory.controller..";
    private static final String DTO = "com.deepfine.inventory.dto..";
    private static final String SERVICE = "com.deepfine.inventory.service..";
    private static final String DOMAIN = "com.deepfine.inventory.domain..";
    private static final String REPOSITORY = "com.deepfine.inventory.repository..";
    private static final String COMMON = "com.deepfine.inventory.common..";

    @ArchTest
    static final ArchRule controller는_repository에_의존하지_않는다 = noClasses()
            .that().resideInAPackage(CONTROLLER)
            .should().dependOnClassesThat().resideInAPackage(REPOSITORY);

    /** 서비스는 요청 DTO 대신 원시 값을 받는다. HTTP 계약이 바뀌어도 서비스 시그니처가 흔들리지 않게 하기 위해서다. */
    @ArchTest
    static final ArchRule service는_controller와_dto에_의존하지_않는다 = noClasses()
            .that().resideInAPackage(SERVICE)
            .should().dependOnClassesThat().resideInAnyPackage(CONTROLLER, DTO);

    @ArchTest
    static final ArchRule domain은_다른_계층과_웹_프레임워크에_의존하지_않는다 = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(
                    CONTROLLER, DTO, SERVICE, REPOSITORY,
                    "org.springframework..", "jakarta.servlet..");

    /** 재고를 바꾸는 SQL은 한 곳(repository)에만 있고, 그곳은 service만 쓴다. */
    @ArchTest
    static final ArchRule repository는_service만_사용한다 = classes()
            .that().resideInAPackage(REPOSITORY)
            .should().onlyHaveDependentClassesThat().resideInAnyPackage(SERVICE, REPOSITORY);

    @ArchTest
    static final ArchRule common은_계층_패키지에_의존하지_않는다 = noClasses()
            .that().resideInAPackage(COMMON)
            .should().dependOnClassesThat().resideInAnyPackage(CONTROLLER, DTO, SERVICE, REPOSITORY);
}
