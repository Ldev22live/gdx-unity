package io.github.ldev22live.gdxunity.engine.vector;

import java.util.Objects;

/**
 * Writes a {@link VectorDocument} as SVG 1.1 within the TD-104 subset: one {@code path} per shape with its {@code id}, transform,
 * fill, fill rule, stroke, stroke width and opacity, so {@link SvgImport} reads it back to the same shapes.
 */
public final class SvgExport {
    private SvgExport() { }

    public static String write(VectorDocument document) {
        Objects.requireNonNull(document, "document cannot be null");
        StringBuilder out = new StringBuilder();
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(document.width()).append("\" height=\"")
            .append(document.height()).append("\" viewBox=\"0 0 ").append(document.width()).append(' ').append(document.height())
            .append("\">\n");
        for (VectorShape shape : document.shapes()) {
            out.append("  <path id=\"").append(escape(shape.name())).append("\" d=\"").append(PathData.format(shape.path())).append('"');
            if (!shape.transform().isIdentity()) {
                Affine t = shape.transform();
                out.append(" transform=\"matrix(");
                float[] values = {t.a(), t.b(), t.c(), t.d(), t.e(), t.f()};
                for (int i = 0; i < values.length; i++) {
                    out.append(i == 0 ? "" : " ").append(PathData.number(values[i]));
                }
                out.append(")\"");
            }
            paint(out, "fill", shape.fill());
            if (shape.fillRule() == VectorShape.FillRule.EVENODD) {
                out.append(" fill-rule=\"evenodd\"");
            }
            paint(out, "stroke", shape.stroke());
            if (shape.stroke() != null) {
                out.append(" stroke-width=\"").append(PathData.number(shape.strokeWidth())).append('"');
            }
            if (shape.opacity() < 1f) {
                out.append(" opacity=\"").append(PathData.number(shape.opacity())).append('"');
            }
            out.append("/>\n");
        }
        return out.append("</svg>\n").toString();
    }

    private static void paint(StringBuilder out, String property, Rgba colour) {
        if (colour == null) {
            out.append(' ').append(property).append("=\"none\"");
            return;
        }
        Rgba opaque = new Rgba(colour.r(), colour.g(), colour.b(), 1f);
        out.append(' ').append(property).append("=\"").append(opaque.toHex()).append('"');
        if (colour.a() < 1f) {
            out.append(' ').append(property).append("-opacity=\"").append(PathData.number(colour.a())).append('"');
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
