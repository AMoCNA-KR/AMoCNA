package com.kubiki.daedalus.proxy;

import com.kubiki.daedalus.annotation.Bind;
import com.kubiki.daedalus.annotation.Template;
import com.kubiki.daedalus.annotation.TemplateType;
import com.kubiki.daedalus.annotation.Type;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class DaedalusInvocationHandlerTest {

    private GlobalTemplateContext globalContext;

    @BeforeEach
    void setUp() {
        globalContext = new GlobalTemplateContext();
        globalContext.set("GLOBAL_VAR", "GlobalValue");
    }

    public interface TestRepository {
        @Template(resource = "templates/test-template.txt")
        String greet(@Type(TemplateType.PLAIN) @Bind("name") String name, @Type(TemplateType.PLAIN) @Bind("place") String place);

        @Template(resource = "templates/test-template.txt")
        String greetLiteral(@Bind("name") String name, @Bind("place") String place);

        @Template(resource = "templates/test-template.txt")
        String greetMissingBind(String name, String place);
    }

    @Test
    void shouldHydrateTemplateWithPlainValues() {
        TestRepository repository = (TestRepository) Proxy.newProxyInstance(
                TestRepository.class.getClassLoader(),
                new Class[]{TestRepository.class},
                new DaedalusInvocationHandler(TestRepository.class, globalContext)
        );

        String result = repository.greet("Alice", "Wonderland");

        assertThat(result).isEqualTo("Hello Alice, welcome to Wonderland!\nGlobal: GlobalValue\n");
    }

    @Test
    void shouldHydrateTemplateWithLiteralValues() {
        TestRepository repository = (TestRepository) Proxy.newProxyInstance(
                TestRepository.class.getClassLoader(),
                new Class[]{TestRepository.class},
                new DaedalusInvocationHandler(TestRepository.class, globalContext)
        );

        String result = repository.greetLiteral("Alice", "Wonderland");

        assertThat(result).isEqualTo("Hello \"Alice\", welcome to \"Wonderland\"!\nGlobal: GlobalValue\n");
    }
}
