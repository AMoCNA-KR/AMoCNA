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

    public record TestPojo(String id, String name, boolean active) {}

    public interface TestRepository {
        @Template(resource = "templates/test-template.txt")
        String greet(@Type(TemplateType.PLAIN) @Bind("name") String name, @Type(TemplateType.PLAIN) @Bind("place") String place);

        @Template(resource = "templates/test-template.txt")
        String greetLiteral(@Bind("name") String name, @Bind("place") String place);

        @Template(resource = "templates/test-template.txt")
        String greetMissingBind(String name, String place);

        @Template(resource = "templates/test-pojo.json")
        TestPojo createPojo(@Type(TemplateType.PLAIN) @Bind("id") String id, @Type(TemplateType.PLAIN) @Bind("name") String name, @Type(TemplateType.PLAIN) @Bind("active") boolean active);
    }

    @Test
    void shouldHydrateTemplateWithPlainValues() {
        TestRepository repository = createRepository();

        String result = repository.greet("Alice", "Wonderland");

        assertThat(result).isEqualTo("Hello Alice, welcome to Wonderland!\nGlobal: GlobalValue\n");
    }

    @Test
    void shouldHydrateTemplateWithLiteralValues() {
        TestRepository repository = createRepository();

        String result = repository.greetLiteral("Alice", "Wonderland");

        assertThat(result).isEqualTo("Hello \"Alice\", welcome to \"Wonderland\"!\nGlobal: GlobalValue\n");
    }

    @Test
    void shouldHydrateAndConvertPojo() {
        TestRepository repository = createRepository();

        TestPojo pojo = repository.createPojo("123", "ItemName", true);

        assertThat(pojo).isEqualTo(new TestPojo("123", "ItemName", true));
    }

    @Test
    void shouldHandleObjectMethods() {
        TestRepository repository1 = createRepository();
        TestRepository repository2 = createRepository();

        assertThat(repository1.toString()).isEqualTo("DaedalusProxy[TestRepository]");
        assertThat(repository1.hashCode()).isNotZero();
        assertThat(repository1).isEqualTo(repository1);
        assertThat(repository1).isNotEqualTo(repository2);
    }

    private TestRepository createRepository() {
        return (TestRepository) Proxy.newProxyInstance(
                TestRepository.class.getClassLoader(),
                new Class[]{TestRepository.class},
                new DaedalusInvocationHandler(TestRepository.class, globalContext)
        );
    }
}
