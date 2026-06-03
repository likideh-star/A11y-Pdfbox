package com.likide.a11y.pdf.model;

import java.util.Map;

public record FluentCustomNode(String family, String type, Map<String, String> attributes) implements FluentNode {
}
