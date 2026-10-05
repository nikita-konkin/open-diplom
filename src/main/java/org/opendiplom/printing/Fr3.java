package org.opendiplom.printing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * A FastReport 4 template as CyberDiploma keeps it: XML, usually gzipped.
 *
 * <p>Read leniently: the files declare UTF-8, yet a few attributes, such as
 * the names of data sets, are in Windows-1251. The texts that matter, with
 * the field expressions, are in UTF-8.
 */
public final class Fr3 {
    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z]\\w*)((?:\\s+[\\w.:]+\\s*=\\s*\"[^\"]*\")*)\\s*(/?)>");
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w.:]+)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9A-Fa-f]+|quot|amp|lt|gt|apos);");

    private Fr3() {
    }

    /** An element with its attributes, entities decoded. */
    public static final class Node {
        public final String tag;
        public final Map<String, String> attributes;
        public final List<Node> children = new ArrayList<>();

        Node(final String tag, final Map<String, String> attributes) {
            this.tag = tag;
            this.attributes = Collections.unmodifiableMap(attributes);
        }

        /** The attribute, or the default. */
        public String text(final String name, final String otherwise) {
            return this.attributes.getOrDefault(name, otherwise);
        }

        /** A number written with a decimal comma, or the default. */
        public double number(final String name, final double otherwise) {
            final String value = this.attributes.get(name);
            if (value == null || value.isBlank()) {
                return otherwise;
            }
            try {
                return Double.parseDouble(value.replace(',', '.'));
            } catch (final NumberFormatException error) {
                return otherwise;
            }
        }

        /** Children of a tag, in order. */
        public List<Node> all(final String child) {
            final List<Node> found = new ArrayList<>();
            for (final Node node : this.children) {
                if (node.tag.equals(child)) {
                    found.add(node);
                }
            }
            return found;
        }
    }

    /**
     * The root element, {@code TfrxReport}.
     *
     * @throws IOException when the content is not a FastReport template
     */
    public static Node read(final byte[] content) throws IOException {
        final String xml = new String(unzipped(content), StandardCharsets.UTF_8);
        final Deque<Node> open = new ArrayDeque<>();
        Node root = null;
        final Matcher tag = TAG.matcher(xml);
        while (tag.find()) {
            if (!tag.group(1).isEmpty()) {
                if (!open.isEmpty()) {
                    open.pop();
                }
                continue;
            }
            final Map<String, String> attributes = new LinkedHashMap<>();
            final Matcher attribute = ATTRIBUTE.matcher(tag.group(3));
            while (attribute.find()) {
                attributes.put(attribute.group(1), unescape(attribute.group(2)));
            }
            final Node node = new Node(tag.group(2), attributes);
            if (open.isEmpty()) {
                if (root == null) {
                    root = node;
                }
            } else {
                open.peek().children.add(node);
            }
            if (tag.group(4).isEmpty()) {
                open.push(node);
            }
        }
        if (root == null || !"TfrxReport".equals(root.tag)) {
            throw new IOException("Это не шаблон FastReport (.fr3): нет элемента TfrxReport");
        }
        return root;
    }

    private static byte[] unzipped(final byte[] content) throws IOException {
        if (content.length < 2 || (content[0] & 0xff) != 0x1f || (content[1] & 0xff) != 0x8b) {
            return content;
        }
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(content));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    static String unescape(final String value) {
        final Matcher entity = ENTITY.matcher(value);
        final StringBuilder text = new StringBuilder();
        while (entity.find()) {
            final String name = entity.group(1);
            final String replacement;
            if (name.startsWith("#x") || name.startsWith("#X")) {
                replacement = new String(Character.toChars(Integer.parseInt(name.substring(2), 16)));
            } else if (name.startsWith("#")) {
                replacement = new String(Character.toChars(Integer.parseInt(name.substring(1))));
            } else {
                replacement = "quot".equals(name) ? "\"" : "amp".equals(name) ? "&" : "lt".equals(name) ? "<"
                    : "gt".equals(name) ? ">" : "'";
            }
            entity.appendReplacement(text, Matcher.quoteReplacement(replacement));
        }
        entity.appendTail(text);
        return text.toString();
    }
}
