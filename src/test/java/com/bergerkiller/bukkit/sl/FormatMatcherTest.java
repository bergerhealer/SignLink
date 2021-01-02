package com.bergerkiller.bukkit.sl;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.Queue;

import org.junit.Test;

import com.bergerkiller.bukkit.sl.impl.format.FormatMatcher;

/**
 * Tests the format matcher using a custom test harness
 * to check the callbacks are correct
 */
public class FormatMatcherTest {

    @Test
    public void testConstantOnly() {
        match("").constant("").end();
        match("%").constant("%").end();
        match("Hello, world!").constant("Hello, world!").end();
        match("%Hello, world!").constant("%Hello, world!").end();
        match("Hello, world!%").constant("Hello, world!%").end();
        match("This is % not % a variable").constant("This is % not % a variable").end();
        match("This is % not% a variable").constant("This is % not% a variable").end();
        match("This is %not % a variable").constant("This is %not % a variable").end();
    }

    @Test
    public void testEscaped() {
        match("%%").constant("%").end();
        match("%%Hello, world!").constant("%Hello, world!").end();
        match("Hello, world!%%").constant("Hello, world!%").end();
        match("Hello, %%world!").constant("Hello, %world!").end();
        match("%%Hello, world!%%").constant("%Hello, world!%").end();
        match("%%%%").constant("%%").end();
        match("%%%%Hello, world!").constant("%%Hello, world!").end();
        match("Hello, world!%%%%").constant("Hello, world!%%").end();
        match("Hello, %%%%world!").constant("Hello, %%world!").end();
        match("%%%%Hello, world!%%%%").constant("%%Hello, world!%%").end();
    }

    @Test
    public void testVariable() {
        match("%variable%").variable("variable").end();
        match("pre%variable%").constant("pre").variable("variable").end();
        match("%variable%post").variable("variable").constant("post").end();
        match("%variable%post%").variable("variable").constant("post%").end();
        match("pre%var1%mid%var2%end")
            .constant("pre").variable("var1")
            .constant("mid").variable("var2")
            .constant("end").end();
        match("%variable% %constant 100%").variable("variable")
            .constant(" %constant 100%").end();
    }

    @Test
    public void testMixed() {
        match("100% %variable% action%%").constant("100% ").variable("variable").constant(" action%").end();
        match("%one% %% %two%").variable("one").constant(" % ").variable("two").end();
    }

    @Test
    public void testUnescapeConstantOnly() {
        unescape("Hello, world!", "Hello, world!").constant("Hello, world!").end();
        unescape("%Hello, world!", "%Hello, world!").constant("%Hello, world!").end();
        unescape("Hello, world!%", "Hello, world!%").constant("Hello, world!%").end();
        unescape("This is % not % a variable", "This is % not % a variable").constant("This is % not % a variable").end();
        unescape("This is % not% a variable", "This is % not% a variable").constant("This is % not% a variable").end();
        unescape("This is %not % a variable", "This is %not % a variable").constant("This is %not % a variable").end();
    }

    @Test
    public void testUnescapeEscaped() {
        unescape("%%", "%").constant("%").end();
        unescape("%%Hello, world!", "%Hello, world!").constant("%Hello, world!").end();
        unescape("Hello, world!%%", "Hello, world!%").constant("Hello, world!%").end();
        unescape("Hello, %%world!", "Hello, %world!").constant("Hello, %world!").end();
        unescape("%%Hello, world!%%", "%Hello, world!%").constant("%Hello, world!%").end();
        unescape("%%%%", "%%").constant("%%").end();
        unescape("%%%%Hello, world!", "%%Hello, world!").constant("%%Hello, world!").end();
        unescape("Hello, world!%%%%", "Hello, world!%%").constant("Hello, world!%%").end();
        unescape("Hello, %%%%world!", "Hello, %%world!").constant("Hello, %%world!").end();
        unescape("%%%%Hello, world!%%%%", "%%Hello, world!%%").constant("%%Hello, world!%%").end();
    }

    @Test
    public void testUnescapeVariable() {
        unescape("%variable%", "%variable%")
            .variable("variable").end();
        unescape("pre%variable%", "pre%variable%")
            .constant("pre").variable("variable").end();
        unescape("%variable%post", "%variable%post")
            .variable("variable").constant("post").end();
        unescape("%variable%post%", "%variable%post%")
            .variable("variable").constant("post%").end();
        unescape("pre%var1%mid%var2%end", "pre%var1%mid%var2%end")
            .constant("pre").variable("var1").constant("mid").variable("var2").constant("end").end();
        unescape("%variable% %constant 100%", "%variable% %constant 100%")
            .variable("variable").constant(" %constant 100%").end();
    }

    @Test
    public void testUnescapeMixed() {
        unescape("100% %variable% action%%", "100% %variable% action%")
            .constant("100% ").variable("variable").constant(" action%").end();
        unescape("%one% %% %two%", "%one% % %two%")
            .variable("one").constant(" % ").variable("two").end();
    }

    /**
     * Performs the matching
     *
     * @param format Input format
     * @return matcher with results
     */
    public TestFormatMatcher match(String format) {
        return new TestFormatMatcher(format, false);
    }

    /**
     * Uses the StringBuilder function to match and unescape
     * the input format
     *
     * @param format Input format
     * @param expected Expected unescaped String
     * @return matcher with results
     */
    public TestFormatMatcher unescape(String format, String expected) {
        TestFormatMatcher matcher = new TestFormatMatcher(format, true);
        if (!expected.equals(matcher.output)) {
            System.err.println("Expected StringBuilder: " + expected);
            System.err.println(" But StringBuilder was: " + matcher.output);
            fail("StringBuilder output of matchAndUnescape() did not match");
        }
        return matcher;
    }

    /**
     * This is used during tests
     */
    public static class TestFormatMatcher extends FormatMatcher {
        private final String format;
        private final String output;
        private final Queue<Element> elements = new LinkedList<Element>();

        public TestFormatMatcher(String format, boolean withBuilder) {
            this.format = format;
            if (withBuilder) {
                StringBuilder tmp = new StringBuilder(format);
                this.matchAndUnescape(tmp);
                this.output = tmp.toString();
            } else {
                this.match(format);
                this.output = format;
            }
        }

        public TestFormatMatcher variable(String name) {
            return expect(new Element(Type.VARIABLE, name));
        }

        public TestFormatMatcher constant(String text) {
            return expect(new Element(Type.CONSTANT, text));
        }

        public TestFormatMatcher expect(Element element) {
            Element next = elements.poll();
            if (next == null) {
                System.err.println("Processing: " + format);
                System.err.println("Expected: " + element.toString());
                System.err.println(" But was: No more elements");
                fail("Expected " + element.toString() + ", but instead there were no more elements");
            }

            if (!next.equals(element)) {
                System.err.println("Processing: " + format);
                System.err.println("Expected: " + element.toString());
                System.err.println(" But was: " + next.toString());
                fail("Expected " + element.toString() + ", but was instead " + next.toString());
            }

            return this;
        }

        public void end() {
            Element next = elements.poll();
            if (next != null) {
                System.err.println("Processing: " + format);
                System.err.println("Expected: No more elements");
                System.err.println(" But was: " + next.toString());
                fail("Expected no more elements, but instead got " + next.toString());
            }
        }

        @Override
        public void onTextConstant(String constant) {
            this.elements.add(new Element(Type.CONSTANT, constant));
        }

        @Override
        public void onVariable(String variableName) {
            this.elements.add(new Element(Type.VARIABLE, variableName));
        }

        public static class Element {
            public final Type type;
            public String value;

            public Element(Type type, String value) {
                this.type = type;
                this.value = value;
            }

            @Override
            public boolean equals(Object o) {
                if (o instanceof Element) {
                    Element other = (Element) o;
                    return this.type == other.type
                            && this.value.equals(other.value);
                }
                return false;
            }

            @Override
            public String toString() {
                return "[" + type.name() + " " + value + "]";
            }
        }
    }

    public static enum Type {
        CONSTANT,
        VARIABLE
    }
}
