package com.likide.spec;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Concrete adapter wired to the planned fluent API names via reflection.
 *
 * Expected (planned) API entrypoint:
 * com.likide.a11y.pdf.A11yPdfDocument
 *
 * Planned fluent pattern examples used by this adapter:
 * - A11yPdfDocument.builder()
 * - .lang("en-US")
 * - .title("...")
 * - .displayDocTitle(true)
 * - .columns(int, float)
 * - .paragraph(String)
 * - .heading(int, String)
 * - .image(String pathOrId, String altText, boolean decorative)
 * - .unorderedList().item(String)...endList()
 * - .artifactHeaderFooter(String)
 * - .buildBytes()
 *
 * If the API is not implemented yet (or method signatures differ), the adapter throws
 * IllegalStateException with a detailed message describing the missing planned symbol.
 */
public class PlannedFluentApiPdfLibraryAdapter implements PdfLibraryAdapter {

    private static final String DOC_CLASS = "com.likide.a11y.pdf.A11yPdfDocument";

    @Override
    public byte[] buildValidMinimalDocument() throws Exception {
        Object builder = startBuilder();
        builder = invoke(builder, "lang", new Class<?>[]{String.class}, "en-US");
        builder = invoke(builder, "title", new Class<?>[]{String.class}, "Accessible PDF Example");
        builder = invoke(builder, "displayDocTitle", new Class<?>[]{boolean.class}, true);
        builder = invoke(builder, "columns", new Class<?>[]{int.class, float.class}, 2, 24.0f);
        builder = invoke(builder, "paragraph", new Class<?>[]{String.class}, "Hello PDF/UA world.");
        return asBytes(invoke(builder, "buildBytes", new Class<?>[]{}));
    }

    @Override
    public void buildWithSkippedHeadingLevels() throws Exception {
        Object builder = startBuilder();
        builder = invoke(builder, "lang", new Class<?>[]{String.class}, "en-US");
        builder = invoke(builder, "title", new Class<?>[]{String.class}, "Invalid heading hierarchy");
        builder = invoke(builder, "heading", new Class<?>[]{int.class, String.class}, 1, "Top level");
        builder = invoke(builder, "heading", new Class<?>[]{int.class, String.class}, 3, "Skipped level");
        invoke(builder, "buildBytes", new Class<?>[]{});
    }

    @Override
    public void buildWithImageMissingAltText() throws Exception {
        Object builder = startBuilder();
        builder = invoke(builder, "lang", new Class<?>[]{String.class}, "en-US");
        builder = invoke(builder, "title", new Class<?>[]{String.class}, "Invalid image metadata");
        builder = invoke(builder, "image", new Class<?>[]{String.class, String.class, boolean.class},
                "sample-image", null, false);
        invoke(builder, "buildBytes", new Class<?>[]{});
    }

    @Override
    public byte[] buildListDocument() throws Exception {
        Object builder = startBuilder();
        builder = invoke(builder, "lang", new Class<?>[]{String.class}, "en-US");
        builder = invoke(builder, "title", new Class<?>[]{String.class}, "List semantics");

        Object listBuilder = invoke(builder, "unorderedList", new Class<?>[]{});
        listBuilder = invoke(listBuilder, "item", new Class<?>[]{String.class}, "First list item");
        listBuilder = invoke(listBuilder, "item", new Class<?>[]{String.class}, "Second list item");
        builder = invoke(listBuilder, "endList", new Class<?>[]{});

        return asBytes(invoke(builder, "buildBytes", new Class<?>[]{}));
    }

    @Override
    public byte[] buildDocumentWithPageArtifacts() throws Exception {
        Object builder = startBuilder();
        builder = invoke(builder, "lang", new Class<?>[]{String.class}, "en-US");
        builder = invoke(builder, "title", new Class<?>[]{String.class}, "Artifact tagging");
        builder = invoke(builder, "artifactHeaderFooter", new Class<?>[]{String.class}, "Page %d of %d");
        builder = invoke(builder, "paragraph", new Class<?>[]{String.class}, "Body text.");
        return asBytes(invoke(builder, "buildBytes", new Class<?>[]{}));
    }

    private Object startBuilder() {
        try {
            Class<?> docType = Class.forName(DOC_CLASS);
            Method builderFactory = docType.getMethod("builder");
            return builderFactory.invoke(null);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Missing planned fluent API class: " + DOC_CLASS, e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Missing planned fluent API factory: " + DOC_CLASS + ".builder()", e);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to call planned fluent API factory: " + DOC_CLASS + ".builder()", e);
        }
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            throw new IllegalStateException("Cannot invoke method on null target: " + methodName);
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "Missing planned fluent API method: " + target.getClass().getName() + "." + signature(methodName, parameterTypes), e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "Cannot access planned fluent API method: " + target.getClass().getName() + "." + signature(methodName, parameterTypes), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(
                    "Planned fluent API method threw: " + target.getClass().getName() + "." + signature(methodName, parameterTypes), cause);
        }
    }

    private byte[] asBytes(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        throw new IllegalStateException("Expected buildBytes() to return byte[] but got: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private String signature(String methodName, Class<?>[] parameterTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append(methodName).append('(');
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parameterTypes[i].getSimpleName());
        }
        sb.append(')');
        return sb.toString();
    }
}
