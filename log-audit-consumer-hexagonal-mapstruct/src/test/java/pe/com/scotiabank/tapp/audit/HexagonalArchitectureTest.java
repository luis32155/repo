package pe.com.scotiabank.tapp.audit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HexagonalArchitectureTest {

    private static final String ROOT = "pe.com.scotiabank.tapp.audit";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void domainDependsOnlyOnJavaAndDomain() {
        classes().that().resideInAPackage(ROOT + ".domain..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", ROOT + ".domain..")
                .check(CLASSES);
    }

    @Test
    void applicationDependsOnlyOnJavaDomainAndApplication() {
        classes().that().resideInAPackage(ROOT + ".application..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", ROOT + ".domain..", ROOT + ".application..")
                .check(CLASSES);
    }

    @Test
    void inboundAdaptersDoNotDependOnOutboundAdapters() {
        noClasses().that().resideInAPackage(ROOT + ".adapter.in..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".adapter.out..")
                .check(CLASSES);
    }

    @Test
    void outboundAdaptersDoNotDependOnInboundAdapters() {
        noClasses().that().resideInAPackage(ROOT + ".adapter.out..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".adapter.in..")
                .check(CLASSES);
    }

    @Test
    void inputAndOutputPortsAreInterfaces() {
        classes().that().resideInAnyPackage(ROOT + ".application.port.in..", ROOT + ".application.port.out..")
                .should().beInterfaces()
                .check(CLASSES);
    }
}
