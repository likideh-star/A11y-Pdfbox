package com.likide.spec;

final class AdapterLoader {

    private static final String ADAPTER_PROPERTY = "a11y.pdf.adapter";

    private AdapterLoader() {
    }

    static PdfLibraryAdapter load() {
        String fqcn = System.getProperty(ADAPTER_PROPERTY, DefaultPdfLibraryAdapter.class.getName());
        try {
            Class<?> type = Class.forName(fqcn);
            Object adapter = type.getDeclaredConstructor().newInstance();
            if (!(adapter instanceof PdfLibraryAdapter casted)) {
                throw new IllegalArgumentException(
                        "Adapter does not implement PdfLibraryAdapter: " + fqcn);
            }
            return casted;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to load adapter: " + fqcn, ex);
        }
    }
}
