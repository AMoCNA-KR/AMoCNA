package com.kubiki.daedalus.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TemplateParserTest {
    @Test
    void shouldParseTemplateWithVariables() {
        TemplateParser parser = new TemplateParser();
        List<TemplateToken> tokens = parser.parse("Hello ${name}, welcome to ${place}!");
        assertThat(tokens).hasSize(5);
        assertThat(tokens.get(0)).isEqualTo(new TemplateToken.StaticToken("Hello "));
        assertThat(tokens.get(1)).isEqualTo(new TemplateToken.VariableToken("name"));
        assertThat(tokens.get(2)).isEqualTo(new TemplateToken.StaticToken(", welcome to "));
        assertThat(tokens.get(3)).isEqualTo(new TemplateToken.VariableToken("place"));
        assertThat(tokens.get(4)).isEqualTo(new TemplateToken.StaticToken("!"));
    }

    @Test
    void shouldParseTemplateWithOnlyVariables() {
        TemplateParser parser = new TemplateParser();
        List<TemplateToken> tokens = parser.parse("${a}${b}");
        assertThat(tokens).hasSize(2);
        assertThat(tokens.get(0)).isEqualTo(new TemplateToken.VariableToken("a"));
        assertThat(tokens.get(1)).isEqualTo(new TemplateToken.VariableToken("b"));
    }

    @Test
    void shouldParseTemplateWithOnlyStaticText() {
        TemplateParser parser = new TemplateParser();
        List<TemplateToken> tokens = parser.parse("No variables here.");
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0)).isEqualTo(new TemplateToken.StaticToken("No variables here."));
    }
}
