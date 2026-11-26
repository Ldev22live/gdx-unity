package io.github.ldev22live.gdxunity.engine.vector;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the SVG subset of TD-104 into a {@link VectorDocument}. Supported: {@code svg} ({@code width}, {@code height},
 * {@code viewBox}), {@code g}, {@code path} (every {@code d} command; arcs become cubics), {@code rect} (with {@code rx}/{@code ry}),
 * {@code circle}, {@code ellipse}, {@code line}, {@code polyline} and {@code polygon}; the attributes {@code transform} (matrix,
 * translate, scale, rotate with an optional centre, skewX, skewY), {@code fill}, {@code stroke}, {@code stroke-width},
 * {@code opacity}, {@code fill-opacity}, {@code stroke-opacity}, {@code fill-rule} and {@code id}, also inside an inline
 * {@code style}; colours as {@code #rgb}, {@code #rrggbb}, {@code rgb(r, g, b)}, the 16 basic CSS names and {@code none}.
 *
 * <p>Everything else is <b>reported by name and skipped, never guessed</b> ({@link Result#unsupported()}): other elements (text,
 * gradients, patterns, filters, masks, clip paths, use, image, style sheets, ...) and other presentation attributes (dashes, caps,
 * joins, ...), and colours it cannot read (the paint is then left out). Groups are folded into each shape's transform; a group's
 * opacity multiplies into its shapes', which differs from SVG where group shapes overlap. Doctypes and external entities are refused.
 */
public final class SvgImport {
    /** Elements read. */
    private static final Set<String> SHAPES = Set.of("path", "rect", "circle", "ellipse", "line", "polyline", "polygon");
    /** Attributes read or harmless (no visual effect here). */
    private static final Set<String> KNOWN_ATTRIBUTES = Set.of("id", "transform", "fill", "stroke", "stroke-width", "opacity",
        "fill-opacity", "stroke-opacity", "fill-rule", "style", "d", "x", "y", "width", "height", "rx", "ry", "cx", "cy", "r", "x1",
        "y1", "x2", "y2", "points", "viewBox", "version", "xmlns", "class", "preserveAspectRatio");
    /** Bézier handle length for a quarter circle of radius 1. */
    private static final float KAPPA = 0.5522847498f;
    /** The 16 basic CSS colour keywords. */
    private static final Map<String, String> NAMED = Map.ofEntries(Map.entry("black", "#000000"), Map.entry("silver", "#c0c0c0"),
        Map.entry("gray", "#808080"), Map.entry("white", "#ffffff"), Map.entry("maroon", "#800000"), Map.entry("red", "#ff0000"),
        Map.entry("purple", "#800080"), Map.entry("fuchsia", "#ff00ff"), Map.entry("green", "#008000"), Map.entry("lime", "#00ff00"),
        Map.entry("olive", "#808000"), Map.entry("yellow", "#ffff00"), Map.entry("navy", "#000080"), Map.entry("blue", "#0000ff"),
        Map.entry("teal", "#008080"), Map.entry("aqua", "#00ffff"));
    private static final Pattern TRANSFORM = Pattern.compile("(matrix|translate|scale|rotate|skewX|skewY)\\s*\\(([^)]*)\\)");
    private static final Pattern RGB = Pattern.compile("rgb\\(\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*\\)");

    /**
     * What was read, and what was not.
     *
     * @param document    the shapes read
     * @param unsupported one line per unsupported element, attribute or value with how often it occurred, sorted; empty when everything
     *                    in the file was read
     */
    public record Result(VectorDocument document, List<String> unsupported) {
        public Result {
            Objects.requireNonNull(document, "document cannot be null");
            unsupported = List.copyOf(unsupported);
        }
    }

    private SvgImport() { }

    /**
     * Reads SVG text.
     *
     * @throws IllegalArgumentException when it is not well-formed XML with an {@code svg} root and a size
     */
    public static Result read(String svg) {
        Document xml = parse(Objects.requireNonNull(svg, "svg cannot be null"));
        Element root = xml.getDocumentElement();
        if (!"svg".equals(localName(root))) {
            throw new IllegalArgumentException("Not an SVG: the root element is " + localName(root));
        }
        Reading reading = new Reading();
        for (String size : List.of("width", "height")) {
            String value = root.getAttribute(size).trim();
            if (!value.isEmpty() && !value.matches("[0-9.eE+-]+(px)?")) {
                reading.skip("unit in " + size + "=\"" + value + "\" (the viewBox size is used)");
            }
        }
        float[] viewBox = numbers(root.getAttribute("viewBox"));
        float width = length(root.getAttribute("width"), viewBox.length == 4 ? viewBox[2] : Float.NaN);
        float height = length(root.getAttribute("height"), viewBox.length == 4 ? viewBox[3] : Float.NaN);
        if (!(width > 0f) || !(height > 0f)) {
            throw new IllegalArgumentException("An SVG needs a width and height or a viewBox");
        }
        Affine base = Affine.IDENTITY;
        if (viewBox.length == 4 && viewBox[2] > 0f && viewBox[3] > 0f) {
            base = Affine.scale(width / viewBox[2], height / viewBox[3]).times(Affine.translate(-viewBox[0], -viewBox[1]));
        }
        reading.attributes(root);
        Style style = Style.DEFAULT.inherit(root, reading);
        reading.children(root, base, style);
        return new Result(new VectorDocument(Math.round(width), Math.round(height), reading.shapes), reading.report());
    }

    private static Document parse(String svg) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(new InputSource(new StringReader(svg)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Not a readable SVG: " + e.getMessage(), e);
        }
    }

    /** The state of one read: the shapes so far, their names, and what was skipped. */
    private static final class Reading {
        final List<VectorShape> shapes = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        final Map<String, Integer> skipped = new TreeMap<>();

        void skip(String what) {
            skipped.merge(what, 1, Integer::sum);
        }

        List<String> report() {
            List<String> lines = new ArrayList<>();
            skipped.forEach((what, count) -> lines.add(what + (count > 1 ? " (" + count + ")" : "")));
            return lines;
        }

        void attributes(Element element) {
            for (int i = 0; i < element.getAttributes().getLength(); i++) {
                Node attribute = element.getAttributes().item(i);
                String name = attribute.getNodeName();
                if (!KNOWN_ATTRIBUTES.contains(name) && !name.startsWith("xmlns") && !name.contains(":")) {
                    skip("attribute " + name);
                }
            }
        }

        void children(Element parent, Affine transform, Style style) {
            for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element element) {
                    element(element, transform, style);
                }
            }
        }

        void element(Element element, Affine parentTransform, Style parentStyle) {
            String name = localName(element);
            if (!name.equals("g") && !SHAPES.contains(name)) {
                if (!name.equals("title") && !name.equals("desc") && !name.equals("metadata")) {
                    skip("element " + name);
                }
                return;
            }
            attributes(element);
            Affine transform = parentTransform.times(transform(element.getAttribute("transform"), this));
            Style style = parentStyle.inherit(element, this);
            if (name.equals("g")) {
                children(element, transform, style);
                return;
            }
            VectorPath path = shapePath(name, element);
            if (path == null || path.segments().isEmpty()) {
                skip("empty " + name);
                return;
            }
            boolean stroked = style.stroke != null && style.strokeWidth > 0f;
            Rgba stroke = stroked ? style.stroke.withOpacity(style.strokeOpacity) : null;
            // A line has no inside to fill; every other shape fills (an open polyline or path closes for filling, as in SVG)
            Rgba fill = name.equals("line") || style.fill == null ? null : style.fill.withOpacity(style.fillOpacity);
            shapes.add(new VectorShape(uniqueName(element.getAttribute("id"), name), path, transform, fill, style.fillRule, stroke,
                stroked ? style.strokeWidth : 0f, Math.max(0f, Math.min(1f, style.opacity))));
        }

        String uniqueName(String id, String element) {
            String base = id == null || id.isBlank() ? element : id;
            String candidate = base;
            for (int n = 2; !names.add(candidate); n++) {
                candidate = base + " " + n;
            }
            return candidate;
        }
    }

    /** Inherited presentation properties. */
    private record Style(Rgba fill, Rgba stroke, float strokeWidth, float opacity, float fillOpacity, float strokeOpacity,
                         VectorShape.FillRule fillRule) {
        static final Style DEFAULT = new Style(Rgba.BLACK, null, 1f, 1f, 1f, 1f, VectorShape.FillRule.NONZERO);

        Style inherit(Element element, Reading reading) {
            Map<String, String> properties = new LinkedHashMap<>();
            for (String name : List.of("fill", "stroke", "stroke-width", "opacity", "fill-opacity", "stroke-opacity", "fill-rule")) {
                if (element.hasAttribute(name)) {
                    properties.put(name, element.getAttribute(name).trim());
                }
            }
            for (String declaration : element.getAttribute("style").split(";")) {
                int colon = declaration.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String name = declaration.substring(0, colon).trim();
                String value = declaration.substring(colon + 1).trim();
                if (Set.of("fill", "stroke", "stroke-width", "opacity", "fill-opacity", "stroke-opacity", "fill-rule").contains(name)) {
                    properties.put(name, value);
                } else {
                    reading.skip("style " + name);
                }
            }
            Rgba newFill = fill;
            Rgba newStroke = stroke;
            float newWidth = strokeWidth;
            float newOpacity = opacity;
            float newFillOpacity = fillOpacity;
            float newStrokeOpacity = strokeOpacity;
            VectorShape.FillRule newRule = fillRule;
            for (Map.Entry<String, String> property : properties.entrySet()) {
                String value = property.getValue();
                switch (property.getKey()) {
                    case "fill" -> newFill = paint(value, reading);
                    case "stroke" -> newStroke = paint(value, reading);
                    case "stroke-width" -> newWidth = Math.max(0f, length(value, newWidth));
                    case "opacity" -> newOpacity = opacity * fraction(value);
                    case "fill-opacity" -> newFillOpacity = fraction(value);
                    case "stroke-opacity" -> newStrokeOpacity = fraction(value);
                    case "fill-rule" -> newRule = value.equals("evenodd") ? VectorShape.FillRule.EVENODD : VectorShape.FillRule.NONZERO;
                    default -> { }
                }
            }
            return new Style(newFill, newStroke, newWidth, newOpacity, newFillOpacity, newStrokeOpacity, newRule);
        }
    }

    private static Rgba paint(String value, Reading reading) {
        String text = value.trim().toLowerCase(Locale.ROOT);
        if (text.equals("none")) {
            return null;
        }
        if (NAMED.containsKey(text)) {
            return Rgba.hex(NAMED.get(text));
        }
        if (text.startsWith("#")) {
            try {
                return Rgba.hex(text);
            } catch (IllegalArgumentException e) {
                reading.skip("colour " + value);
                return null;
            }
        }
        Matcher rgb = RGB.matcher(text);
        if (rgb.matches()) {
            return new Rgba(byteChannel(rgb.group(1)), byteChannel(rgb.group(2)), byteChannel(rgb.group(3)), 1f);
        }
        reading.skip("colour " + value);
        return null;
    }

    private static float byteChannel(String text) {
        return Math.max(0f, Math.min(255f, Float.parseFloat(text))) / 255f;
    }

    private static float fraction(String text) {
        try {
            return Math.max(0f, Math.min(1f, Float.parseFloat(text.trim())));
        } catch (NumberFormatException e) {
            return 1f;
        }
    }

    static Affine transform(String text, Reading reading) {
        Affine result = Affine.IDENTITY;
        if (text == null || text.isBlank()) {
            return result;
        }
        Matcher m = TRANSFORM.matcher(text);
        int end = 0;
        while (m.find()) {
            if (!text.substring(end, m.start()).replace(",", "").isBlank()) {
                reading.skip("transform " + text.trim());
                return Affine.IDENTITY;
            }
            float[] v = numbers(m.group(2));
            Affine step = switch (m.group(1)) {
                case "matrix" -> v.length == 6 ? new Affine(v[0], v[1], v[2], v[3], v[4], v[5]) : null;
                case "translate" -> v.length == 1 ? Affine.translate(v[0], 0f) : v.length == 2 ? Affine.translate(v[0], v[1]) : null;
                case "scale" -> v.length == 1 ? Affine.scale(v[0], v[0]) : v.length == 2 ? Affine.scale(v[0], v[1]) : null;
                case "rotate" -> v.length == 1 ? Affine.rotate(v[0]) : v.length == 3
                    ? Affine.translate(v[1], v[2]).times(Affine.rotate(v[0])).times(Affine.translate(-v[1], -v[2])) : null;
                case "skewX" -> v.length == 1 ? Affine.skewX(v[0]) : null;
                case "skewY" -> v.length == 1 ? Affine.skewY(v[0]) : null;
                default -> null;
            };
            if (step == null) {
                reading.skip("transform " + text.trim());
                return Affine.IDENTITY;
            }
            result = result.times(step);
            end = m.end();
        }
        if (!text.substring(end).replace(",", "").isBlank()) {
            reading.skip("transform " + text.trim());
            return Affine.IDENTITY;
        }
        return result;
    }

    private static VectorPath shapePath(String name, Element e) {
        return switch (name) {
            case "path" -> PathData.parse(e.getAttribute("d"));
            case "rect" -> rect(number(e, "x"), number(e, "y"), number(e, "width"), number(e, "height"), e);
            case "circle" -> ellipse(number(e, "cx"), number(e, "cy"), number(e, "r"), number(e, "r"));
            case "ellipse" -> ellipse(number(e, "cx"), number(e, "cy"), number(e, "rx"), number(e, "ry"));
            case "line" -> new VectorPath(List.of(new VectorPath.MoveTo(number(e, "x1"), number(e, "y1")),
                new VectorPath.LineTo(number(e, "x2"), number(e, "y2"))));
            case "polyline", "polygon" -> points(numbers(e.getAttribute("points")), name.equals("polygon"));
            default -> null;
        };
    }

    private static VectorPath rect(float x, float y, float w, float h, Element e) {
        if (!(w > 0f) || !(h > 0f)) {
            return null;
        }
        float rx = e.hasAttribute("rx") ? number(e, "rx") : e.hasAttribute("ry") ? number(e, "ry") : 0f;
        float ry = e.hasAttribute("ry") ? number(e, "ry") : rx;
        rx = Math.min(Math.max(0f, rx), w / 2f);
        ry = Math.min(Math.max(0f, ry), h / 2f);
        if (rx == 0f || ry == 0f) {
            return VectorPath.polygon(x, y, x + w, y, x + w, y + h, x, y + h);
        }
        return PathData.parse(String.format(Locale.ROOT,
            "M %f %f H %f A %f %f 0 0 1 %f %f V %f A %f %f 0 0 1 %f %f H %f A %f %f 0 0 1 %f %f V %f A %f %f 0 0 1 %f %f Z",
            x + rx, y, x + w - rx, rx, ry, x + w, y + ry, y + h - ry, rx, ry, x + w - rx, y + h, x + rx, rx, ry, x, y + h - ry, y + ry,
            rx, ry, x + rx, y));
    }

    private static VectorPath ellipse(float cx, float cy, float rx, float ry) {
        if (!(rx > 0f) || !(ry > 0f)) {
            return null;
        }
        float kx = rx * KAPPA;
        float ky = ry * KAPPA;
        return new VectorPath(List.of(
            new VectorPath.MoveTo(cx + rx, cy),
            new VectorPath.CubicTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry),
            new VectorPath.CubicTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy),
            new VectorPath.CubicTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry),
            new VectorPath.CubicTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy),
            new VectorPath.Close()));
    }

    private static VectorPath points(float[] xy, boolean closed) {
        if (xy.length < 4) {
            return null;
        }
        List<VectorPath.Segment> segments = new ArrayList<>();
        segments.add(new VectorPath.MoveTo(xy[0], xy[1]));
        for (int i = 2; i + 1 < xy.length; i += 2) {
            segments.add(new VectorPath.LineTo(xy[i], xy[i + 1]));
        }
        if (closed) {
            segments.add(new VectorPath.Close());
        }
        return new VectorPath(segments);
    }

    private static float number(Element e, String attribute) {
        return length(e.getAttribute(attribute), 0f);
    }

    /** A length in user units; a trailing {@code px} is accepted, other units fall back to {@code otherwise}. */
    private static float length(String text, float otherwise) {
        if (text == null || text.isBlank()) {
            return otherwise;
        }
        String value = text.trim();
        if (value.endsWith("px")) {
            value = value.substring(0, value.length() - 2);
        }
        try {
            float result = Float.parseFloat(value);
            return Float.isFinite(result) ? result : otherwise;
        } catch (NumberFormatException e) {
            return otherwise;
        }
    }

    private static float[] numbers(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        String[] parts = text.trim().split("[\\s,]+");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Float.parseFloat(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not a number list: " + text, e);
            }
        }
        return result;
    }

    private static String localName(Node node) {
        return node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
    }
}
