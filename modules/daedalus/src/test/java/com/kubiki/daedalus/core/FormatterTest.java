package com.kubiki.daedalus.core;

import com.kubiki.daedalus.annotation.TemplateType;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FormatterTest {
    private final Formatter formatter = new Formatter();

    @Test
    public void testFormatLiteralString() {
        assertEquals("\"hello\"", formatter.format("hello", TemplateType.LITERAL));
    }

    @Test
    public void testFormatLiteralNumber() {
        assertEquals("123", formatter.format(123, TemplateType.LITERAL));
    }

    @Test
    public void testFormatIRI() {
        assertEquals("<http://example.org>", formatter.format("http://example.org", TemplateType.IRI));
    }

    @Test
    public void testFormatCollection() {
        List<String> items = Arrays.asList("a", "b", "c");
        assertEquals("\"a\", \"b\", \"c\"", formatter.format(items, TemplateType.COLLECTION));
    }

    @Test
    public void testFormatNull() {
        assertEquals("", formatter.format(null, TemplateType.LITERAL));
    }
}
