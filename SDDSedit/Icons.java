/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.Icon;

/** Icons drawn in code from outlines on a 24 by 24 grid, so they stay crisp at any size. */
enum Icons {
    NEW, OPEN, SAVE, UNDO, REDO, PREV, NEXT, FILTER, PLOT, GRID, PLUS, SLIDERS, TERMINAL, CLOSE, SEARCH, CHEVRON_DOWN,
    CHEVRON_RIGHT, FILE;

    private Path2D path() {
        Path2D.Float p = new Path2D.Float();
        switch (this) {
            case OPEN:
                line(p, 3, 18.5f, 3, 6, 9, 6, 11, 8.5f, 20, 8.5f, 20, 18.5f);
                p.closePath();
                break;
            case SAVE:
                line(p, 4, 3.5f, 16, 3.5f, 20.5f, 8, 20.5f, 20.5f, 4, 20.5f);
                p.closePath();
                line(p, 7.5f, 3.5f, 7.5f, 8.5f, 15, 8.5f, 15, 3.5f);
                line(p, 7.5f, 20.5f, 7.5f, 14, 17, 14, 17, 20.5f);
                break;
            case UNDO:
                line(p, 9, 14, 4, 9, 9, 4);
                line(p, 4, 9, 14, 9);
                p.append(new Arc2D.Float(8, 9, 12, 12, 90, -180, Arc2D.OPEN), true);
                p.lineTo(11, 21);
                break;
            case REDO:
                line(p, 15, 14, 20, 9, 15, 4);
                line(p, 20, 9, 10, 9);
                p.append(new Arc2D.Float(4, 9, 12, 12, 90, 180, Arc2D.OPEN), true);
                p.lineTo(13, 21);
                break;
            case PREV:
                line(p, 15, 18, 9, 12, 15, 6);
                break;
            case NEXT:
                line(p, 9, 18, 15, 12, 9, 6);
                break;
            case FILTER:
                line(p, 3, 5, 21, 5, 14, 13, 14, 19, 10, 17, 10, 13);
                p.closePath();
                break;
            case PLOT:
                line(p, 3, 3, 3, 21, 21, 21);
                line(p, 7, 15, 11, 10, 15, 13, 20, 6);
                break;
            case GRID:
                p.append(new RoundRectangle2D.Float(3, 3, 18, 18, 4, 4), false);
                line(p, 3, 9, 21, 9);
                line(p, 3, 15, 21, 15);
                line(p, 9, 3, 9, 21);
                line(p, 15, 3, 15, 21);
                break;
            case PLUS:
                line(p, 12, 5, 12, 19);
                line(p, 5, 12, 19, 12);
                break;
            case SLIDERS:
                line(p, 4, 7, 13, 7);
                line(p, 17, 7, 20, 7);
                line(p, 4, 17, 7, 17);
                line(p, 11, 17, 20, 17);
                p.append(new Ellipse2D.Float(13, 5, 4, 4), false);
                p.append(new Ellipse2D.Float(7, 15, 4, 4), false);
                break;
            case TERMINAL:
                line(p, 4, 17, 10, 12, 4, 7);
                line(p, 12, 19, 20, 19);
                break;
            case CLOSE:
                line(p, 18, 6, 6, 18);
                line(p, 6, 6, 18, 18);
                break;
            case SEARCH:
                p.append(new Ellipse2D.Float(4, 4, 14, 14), false);
                line(p, 20, 20, 16, 16);
                break;
            case CHEVRON_DOWN:
                line(p, 6, 9, 12, 15, 18, 9);
                break;
            case CHEVRON_RIGHT:
                line(p, 9, 6, 15, 12, 9, 18);
                break;
            case NEW:
            case FILE:
                line(p, 14, 3, 6, 3, 6, 21, 19, 21, 19, 8);
                p.closePath();
                line(p, 14, 3, 14, 8, 19, 8);
                if (this == FILE) {
                    line(p, 9, 13, 16, 13);
                    line(p, 9, 17, 14, 17);
                } else {
                    line(p, 12.5f, 11, 12.5f, 17);
                    line(p, 9.5f, 14, 15.5f, 14);
                }
                break;
        }
        return p;
    }

    private static void line(Path2D p, float... points) {
        p.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2)
            p.lineTo(points[i], points[i + 1]);
    }

    /** Returns this icon at a size in pixels, drawn in a color. */
    Icon icon(final int size, final Color color) {
        final Path2D outline = path();
        return new Icon() {
            public int getIconWidth() {
                return size;
            }

            public int getIconHeight() {
                return size;
            }

            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.smooth(g2);
                g2.translate(x, y);
                double scale = size / 24.0;
                g2.scale(scale, scale);
                float width = (float) Math.max(1.75, 1.15 / scale);
                boolean enabled = c == null || c.isEnabled();
                g2.setColor(enabled ? color : Theme.t.muted.brighter());
                g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(outline);
                g2.dispose();
            }
        };
    }
}
