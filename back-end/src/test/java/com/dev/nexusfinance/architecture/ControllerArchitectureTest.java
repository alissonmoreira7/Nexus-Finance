package com.dev.nexusfinance.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Testes de arquitetura (QA-08).
 *
 * <p>Estes testes nao verificam um caso de uso; verificam um <em>invariante</em>.
 * A diferenca importa: um teste de caso impede que <em>aquele</em> bug volte, um
 * teste de arquitetura impede que a <em>classe</em> de bug volte — inclusive em
 * endpoints que ainda nao existem.
 *
 * <p>Escritos com a API core do ArchUnit em {@code @Test} comum, nao com o engine
 * {@code archunit-junit5}: este projeto usa JUnit Jupiter 6, e o engine 1.3.0 foi
 * construido para o JUnit 5.
 */
class ControllerArchitectureTest {
    private static JavaClasses classesDeProducao;

    @BeforeAll
    static void importarClasses() {
        classesDeProducao = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.dev.nexusfinance");
    }

    /**
     * A regra de ouro derivada do S-01 (IDOR no delete de transacao): nenhum id
     * vindo da URL pode ser usado sem que o metodo tambem receba a identidade do
     * requisitante. Antes da correcao do S-01 esta regra falhava em
     * {@code TransactionController.delete}.
     */
    @Test
    @DisplayName("todo endpoint que recebe id na URL recebe tambem o usuario autenticado")
    void endpointComIdNaUrlExigeUsuarioAutenticado() {
        methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().arePublic()
            .should(receberUsuarioAutenticadoQuandoRecebeIdNaUrl())
            .because("um @PathVariable UUID sem @RequestAttribute UUID authenticatedUserId "
                + "permite que qualquer usuario autenticado acesse o recurso de outro (IDOR, ver S-01)")
            .check(classesDeProducao);
    }

    private static ArchCondition<JavaMethod> receberUsuarioAutenticadoQuandoRecebeIdNaUrl() {
        return new ArchCondition<>("receber @RequestAttribute UUID quando recebe @PathVariable UUID") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Method reflexivo = method.reflect();
                if (!temParametro(reflexivo, PathVariable.class, UUID.class)) {
                    return;
                }
                if (temParametro(reflexivo, RequestAttribute.class, UUID.class)) {
                    return;
                }
                events.add(SimpleConditionEvent.violated(method, String.format(
                    "%s.%s recebe @PathVariable UUID mas nao recebe @RequestAttribute UUID authenticatedUserId",
                    method.getOwner().getSimpleName(), method.getName())));
            }
        };
    }

    private static boolean temParametro(Method method, Class<? extends Annotation> anotacao, Class<?> tipo) {
        for (Parameter parametro : method.getParameters()) {
            if (parametro.isAnnotationPresent(anotacao) && tipo.equals(parametro.getType())) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("controller nao acessa repositorio direto, sempre via service")
    void controllerNaoAcessaRepositorioDireto() {
        noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repositories..")
            .because("pular o service tira do caminho as validacoes e as checagens de dono")
            .check(classesDeProducao);
    }

    @Test
    @DisplayName("service nao depende de controller")
    void serviceNaoDependeDeController() {
        noClasses()
            .that().resideInAPackage("..services..")
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .because("a dependencia entre camadas aponta numa direcao so")
            .check(classesDeProducao);
    }

    @Test
    @DisplayName("repositorio nao depende de service nem de controller")
    void repositorioNaoDependeDeCamadasAcima() {
        noClasses()
            .that().resideInAPackage("..repositories..")
            .should().dependOnClassesThat().resideInAnyPackage("..services..", "..controller..")
            .check(classesDeProducao);
    }

    @Test
    @DisplayName("modelo de dominio nao depende de Spring Web")
    void modeloNaoDependeDeSpringWeb() {
        noClasses()
            .that().resideInAPackage("..models..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.web..", "..controller..")
            .because("entidade acoplada a camada HTTP e o caminho para vazar campo por acidente")
            .check(classesDeProducao);
    }

    @Test
    @DisplayName("nenhuma injecao de dependencia por campo")
    void semInjecaoPorCampo() {
        com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
            .because("injecao por construtor deixa a dependencia obrigatoria e o objeto testavel sem reflexao")
            .check(classesDeProducao);
    }

    @Test
    @DisplayName("nenhum uso de System.out ou System.err no codigo de producao")
    void semSaidaPadraoNoCodigo() {
        noClasses()
            .should(com.tngtech.archunit.library.GeneralCodingRules.ACCESS_STANDARD_STREAMS)
            .because("saida padrao nao vai para o agregador de logs e nao tem nivel de severidade")
            .check(classesDeProducao);
    }
}
