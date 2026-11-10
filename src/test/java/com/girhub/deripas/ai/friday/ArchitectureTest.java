package com.girhub.deripas.ai.friday;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Проверяемые правила структуры. Единственное место, где они записаны.
 *
 * <pre>
 * friday (сборка: FridayApplication, DemoRunner)
 *   ├── hud ──────► agent, context      презентация: монитор и экраны
 *   │    └── hud.tui                    виджеты терминала, про агента не знают
 *   ├── agent                           ядро: агент, разговор, события
 *   └── context                         оценка размера контекста
 * </pre>
 *
 * <ul>
 *   <li>Модули — пакеты верхнего уровня (Spring Modulith): без циклов, чужие подпакеты закрыты.</li>
 *   <li>От {@code hud} не зависит никто: убрать пакет — агент продолжит работать.</li>
 *   <li>Терминальные библиотеки (Spring Shell, JLine) видит только {@code hud}.</li>
 *   <li>{@code hud.tui} — виджеты общего назначения: ни агента, ни метафоры из {@code hud}.</li>
 * </ul>
 */
class ArchitectureTest {

    private static final String ROOT = "com.girhub.deripas.ai.friday";

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(ROOT);

    @Test
    void modulesHaveNoCyclesAndKeepInternalsClosed() {
        ApplicationModules.of(FridayApplication.class).verify();
    }

    @Test
    void nothingDependsOnHud() {
        noClasses().that().resideOutsideOfPackage(ROOT + ".hud..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".hud..")
            .check(CLASSES);
    }

    @Test
    void terminalLibrariesAreUsedOnlyByHud() {
        noClasses().that().resideOutsideOfPackage(ROOT + ".hud..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.shell..", "org.jline..")
            .check(CLASSES);
    }

    @Test
    void widgetsKnowNeitherAgentNorHud() {
        noClasses().that().resideInAPackage(ROOT + ".hud.tui..")
            .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".agent..", ROOT + ".context..", ROOT + ".hud")
            .check(CLASSES);
    }
}
