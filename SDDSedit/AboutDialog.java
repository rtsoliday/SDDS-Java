/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/** The About window: an animated banner, the credits, and an OK button. */
final class AboutDialog extends JDialog {
    private AboutDialog(Frame owner) {
        super(owner, "About SDDS Editor", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        final Banner banner = new Banner();

        JLabel title = new JLabel("SDDS Editor");
        title.setFont(Theme.uiFont().deriveFont(Font.BOLD, Theme.uiFont().getSize2D() * 1.7f));
        title.setForeground(Theme.t.text);
        JLabel subtitle = new JLabel("Self Describing Data Sets, lovingly edited");
        subtitle.setFont(Theme.uiFont());
        subtitle.setForeground(Theme.t.muted);

        String css = "<style>body { font-family: '" + Theme.uiFont().getFamily() + "'; font-size: 14px; color: "
                + hex(Theme.t.text) + "; background-color: " + hex(Theme.t.win) + "; } p { margin-top: 0; margin-bottom: 8px; } a { color: "
                + hex(Theme.t.accent) + "; }</style>";
        JEditorPane message = new JEditorPane("text/html", "<html><head>" + css + "</head><body>"
                + "<p>Programmed by Robert Soliday &lt;<a href=\"mailto:soliday@anl.gov\">soliday@anl.gov</a>&gt;<br>"
                + "Powered (mostly) by caffeine and stubbornness.</p>"
                + "<p>This is the Java Swing SDDSedit, modeled on the sddseditor program and built on the "
                + "SDDS Java library.</p>"
                + "<p>Proceed with caution: may contain puns, dad jokes, and the occasional infinite loop.</p>"
                + "</body></html>");
        message.setEditable(false);
        message.setOpaque(false);
        message.setBorder(null);
        message.setSize(new Dimension(464, Short.MAX_VALUE));
        message.setPreferredSize(new Dimension(464, message.getPreferredSize().height));
        message.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED || !Desktop.isDesktopSupported())
                    return;
                try {
                    Desktop.getDesktop().mail(new java.net.URI(e.getDescription()));
                } catch (Exception ex) {
                    /* No mail program is set up; the address is still on screen. */
                }
            }
        });

        JLabel footer = new JLabel("Psst: click the mug.");
        footer.setFont(Theme.uiFont());
        footer.setForeground(Theme.t.muted);

        JButton ok = Flat.primaryButton("OK");
        ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(ok);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(Theme.t.win);
        root.setBorder(Flat.pad(18, 18, 14, 18));
        for (JComponent part : new JComponent[] { banner, title, subtitle, message, footer, buttons })
            part.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(banner);
        root.add(Box.createVerticalStrut(14));
        root.add(title);
        root.add(Box.createVerticalStrut(6));
        root.add(subtitle);
        root.add(Box.createVerticalStrut(10));
        message.setMaximumSize(message.getPreferredSize());
        root.add(message);
        root.add(Box.createVerticalStrut(6));
        root.add(footer);
        root.add(Box.createVerticalStrut(6));
        root.add(buttons);
        setContentPane(root);
        Flat.dialogKeys(this, ok);
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                banner.start();
            }

            public void windowClosed(WindowEvent e) {
                banner.stop();
            }

            public void windowClosing(WindowEvent e) {
                banner.stop();
            }
        });
    }

    /** Show the About window and wait until it is closed. */
    static void showAbout(Frame owner) {
        AboutDialog dialog = new AboutDialog(owner);
        dialog.setVisible(true);
        dialog.dispose();
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color alpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * Math.max(0, Math.min(1, a))));
    }

    /**
     * A live plot of data points streams past a glass mug of coffee that slowly drains. Clicking
     * the mug refills it and gives the plot a caffeine rush; clicking elsewhere tosses up some
     * digits. Time advances only through advance().
     */
    private static final class Banner extends JComponent {
        private static final String[] FORTUNES = { "Now with 100% more self-description.",
            "No columns were harmed in the making of this editor.", "Arrays may be larger than they appear.",
            "Undo is always there for you.", "Every page tells a story.", "Tested on real data. Mostly." };
        private static final String[] GLYPHS = { "0", "1", "x", "y", "+", "\u03c0" };
        private static final double BURST_LENGTH = 2.0;
        private static final double FORTUNE_SECONDS = 4.5;

        private static final class Particle {
            double x, y, vx, vy, age, life;
            String glyph;
            Color color;
        }

        private final Timer timer;
        private final List<Particle> particles = new ArrayList<Particle>();
        private long seed = 20260929L;
        private long last;
        private double time, stream, level = 0.86, burst, fortuneTime;
        private int fortune;
        private boolean refilling;

        Banner() {
            setOpaque(false);
            Dimension size = new Dimension(500, 190);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
            timer = new Timer(16, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    long now = System.nanoTime();
                    // Clamp the step so a stalled event loop does not make the scene jump.
                    advance(Math.min((now - last) / 1e9, 0.1));
                    last = now;
                }
            });
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e))
                        return;
                    if (mugHit().contains(e.getPoint()))
                        refill();
                    else
                        toss(e.getX(), e.getY());
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(
                            mugHit().contains(e.getPoint()) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            });
        }

        void start() {
            last = System.nanoTime();
            timer.start();
        }

        void stop() {
            timer.stop();
        }

        public void removeNotify() {
            timer.stop();
            super.removeNotify();
        }

        /** The mug's resting position; it bobs and jiggles around this when drawn. */
        private Rectangle2D.Double mugRect() {
            double w = 58, h = 56;
            return new Rectangle2D.Double(getWidth() - 40 - 18 - w, getHeight() * 0.33, w, h);
        }

        private Rectangle2D mugHit() {
            Rectangle2D.Double m = mugRect();
            return new Rectangle2D.Double(m.x - 12, m.y - 12, m.width + 34, m.height + 22);
        }

        private double random() {
            seed = (seed * 1664525L + 1013904223L) & 0xFFFFFFFFL;
            return (seed >>> 8) / 16777216.0;
        }

        private void advance(double dt) {
            double rush = 1.0 + 2.0 * burst / BURST_LENGTH;
            time += dt;
            stream += dt * 42.0 * rush;
            burst = Math.max(0.0, burst - dt);
            if (refilling) {
                level = Math.min(1.0, level + dt * 1.25);
                refilling = level < 1.0;
            } else {
                level = Math.max(0.0, level - dt * 0.012);
            }
            fortuneTime += dt;
            while (fortuneTime >= FORTUNE_SECONDS) {
                fortuneTime -= FORTUNE_SECONDS;
                fortune = (fortune + 1) % FORTUNES.length;
            }
            for (Iterator<Particle> it = particles.iterator(); it.hasNext();) {
                Particle p = it.next();
                p.age += dt;
                p.vy += 70.0 * dt;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                if (p.age >= p.life)
                    it.remove();
            }
            repaint();
        }

        /** Top up the mug, with a caffeine rush and a spray of digits. */
        private void refill() {
            refilling = true;
            burst = BURST_LENGTH;
            Rectangle2D.Double mug = mugRect();
            for (int i = 0; i < 10; i++)
                spawn(mug.getCenterX(), mug.y);
        }

        private void toss(double x, double y) {
            for (int i = 0; i < 6; i++)
                spawn(x, y);
        }

        private void spawn(double x, double y) {
            Color[] colors = { Theme.t.accent, Theme.t.accentText, Theme.t.warn };
            Particle p = new Particle();
            p.x = x;
            p.y = y;
            p.vx = -45.0 + 90.0 * random();
            p.vy = -70.0 - 60.0 * random();
            p.life = 1.1 + 0.7 * random();
            p.glyph = GLYPHS[(int) (random() * 6) % 6];
            p.color = colors[(int) (random() * 3) % 3];
            particles.add(p);
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            Theme t = Theme.t;
            Rectangle2D.Double r = new Rectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1);
            RoundRectangle2D.Double frame = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 24, 24);
            g2.setPaint(new GradientPaint((float) r.x, (float) r.y, t.accentSoft, (float) (r.x + r.width),
                    (float) (r.y + r.height), t.header));
            g2.fill(frame);
            Shape oldClip = g2.getClip();
            g2.clip(frame);
            drawGrid(g2, r);
            drawStream(g2, r);
            drawMug(g2);
            drawParticles(g2);
            drawFortune(g2, r);
            g2.setClip(oldClip);
            g2.setColor(t.border);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(frame);
            g2.dispose();
        }

        /** A faint table grid that scrolls with the data. */
        private void drawGrid(Graphics2D g, Rectangle2D.Double r) {
            g.setColor(alpha(Theme.t.border, 110 / 255.0));
            g.setStroke(new BasicStroke(1f));
            double cellWidth = 32, cellHeight = 20;
            for (double x = r.x - (stream * 0.35) % cellWidth; x < r.x + r.width; x += cellWidth)
                g.draw(new Line2D.Double(x, r.y, x, r.y + r.height));
            for (double y = r.y + cellHeight; y < r.y + r.height; y += cellHeight)
                g.draw(new Line2D.Double(r.x, y, r.x + r.width, y));
        }

        /** Data points flowing right to left, fading in and out at the edges. */
        private void drawStream(Graphics2D g, Rectangle2D.Double r) {
            double left = r.x + 16, right = mugRect().x - 40;
            double mid = r.y + r.height * 0.42, amplitude = r.height * 0.2;
            double spacing = 14;
            double shift = stream % spacing;
            long first = (long) Math.floor(stream / spacing);
            List<Point2D.Double> curve = new ArrayList<Point2D.Double>();
            List<Double> fades = new ArrayList<Double>();
            for (int i = 0; left + i * spacing - shift <= right + spacing; i++) {
                double x = left + i * spacing - shift;
                double k = (double) (first + i);
                double value = 0.75 * Math.sin(k * 0.32) + 0.25 * Math.sin(k * 0.9 + 1.3);
                curve.add(new Point2D.Double(x, mid - value * amplitude));
                fades.add(Math.max(0.0, Math.min(1.0, Math.min((x - left) / 40.0, (right - x) / 40.0))));
            }
            Path2D.Double line = new Path2D.Double();
            for (int i = 0; i < curve.size(); i++) {
                Point2D.Double p = curve.get(i);
                if (i == 0)
                    line.moveTo(p.x, p.y);
                else
                    line.lineTo(p.x, p.y);
            }
            g.setColor(alpha(Theme.t.accent, 70 / 255.0));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(line);
            for (int i = 0; i < curve.size(); i++) {
                if (fades.get(i) <= 0)
                    continue;
                g.setColor(alpha(Theme.t.accent, fades.get(i)));
                Point2D.Double p = curve.get(i);
                g.fill(new Ellipse2D.Double(p.x - 3.2, p.y - 3.2, 6.4, 6.4));
            }
        }

        private void drawMug(Graphics2D g) {
            Theme t = Theme.t;
            Rectangle2D.Double rest = mugRect();
            double jiggle = burst > 0 ? Math.sin(time * 40.0) * 1.8 * burst / BURST_LENGTH : 0.0;
            Rectangle2D.Double mug = new Rectangle2D.Double(rest.x + jiggle, rest.y + Math.sin(time * 1.6) * 1.2,
                    rest.width, rest.height);
            double bottom = mug.y + mug.height, right = mug.x + mug.width;

            g.setColor(t.border);
            g.fill(new Ellipse2D.Double(mug.x - 14, bottom - 5, mug.width + 46, 12));

            g.setColor(t.text);
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(right - 10, mug.y + 12, 24, 26, -90, 180, Arc2D.OPEN));

            RoundRectangle2D.Double body = new RoundRectangle2D.Double(mug.x, mug.y, mug.width, mug.height, 20, 20);
            g.setColor(alpha(t.surface, 230 / 255.0));
            g.fill(body);
            g.setColor(t.text);
            g.setStroke(new BasicStroke(2.2f));
            g.draw(body);

            // Coffee, with a gentle wave on top that sloshes harder during a rush.
            Rectangle2D.Double inner = new Rectangle2D.Double(mug.x + 4, mug.y + 6, mug.width - 8, mug.height - 10);
            if (level > 0.0) {
                double top = inner.y + inner.height - level * inner.height;
                double slosh = 1.3 * (1.0 + 2.0 * burst / BURST_LENGTH);
                Path2D.Double coffee = new Path2D.Double();
                coffee.moveTo(inner.x, top);
                for (double x = inner.x; x <= inner.x + inner.width; x += 2)
                    coffee.lineTo(x, top + Math.sin(x * 0.25 + time * 4.0) * slosh);
                coffee.lineTo(inner.x + inner.width, inner.y + inner.height);
                coffee.lineTo(inner.x, inner.y + inner.height);
                coffee.closePath();
                Graphics2D c = (Graphics2D) g.create();
                c.clip(new RoundRectangle2D.Double(inner.x, inner.y, inner.width, inner.height, 14, 14));
                c.setColor(Color.decode("#6F4E37"));
                c.fill(coffee);
                c.setColor(Color.decode("#C8A27A"));
                c.setStroke(new BasicStroke(2f));
                c.draw(coffee);
                c.dispose();
            }

            if (level > 0.12) {
                // Steam: each wisp is drawn in short segments that fade as they rise.
                double strength = Math.min(1.0, (level - 0.12) * 3.0) * (burst > 0 ? 1.0 : 0.75);
                g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int wisp = 0; wisp < 3; wisp++) {
                    double x0 = mug.x + mug.width * (0.3 + 0.2 * wisp);
                    double y0 = mug.y - 6;
                    double px = x0, py = y0;
                    for (int s = 1; s <= 12; s++) {
                        double f = s / 12.0;
                        double x = x0 + Math.sin(time * 2.6 + f * 5.0 + wisp * 2.1) * 6.0 * f;
                        double y = y0 - f * 42.0;
                        g.setColor(alpha(t.muted, strength * (1.0 - f) * 0.8));
                        g.draw(new Line2D.Double(px, py, x, y));
                        px = x;
                        py = y;
                    }
                }
            } else {
                // An empty mug dozes off.
                for (int i = 0; i < 3; i++) {
                    double f = (time * 0.5 + i / 3.0) % 1.0;
                    g.setFont(Theme.uiFont().deriveFont(Font.BOLD, (float) (9.0 + 6.0 * f)));
                    g.setColor(alpha(t.muted, 1.0 - f));
                    g.drawString("z", (float) (mug.x + mug.width / 2 + 4 + f * 18), (float) (mug.y - 4 - f * 38));
                }
            }

            Font caption = Theme.uiFont().deriveFont(Theme.uiFont().getSize2D() * 0.85f);
            g.setFont(caption);
            g.setColor(t.muted);
            String text = level > 0.12 ? "caffeine " + Math.round(level * 100) + "%" : "refill?";
            FontMetrics fm = g.getFontMetrics();
            double cx = mug.x - 30 + (mug.width + 78) / 2;
            g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0),
                    (float) (bottom + 9 + (18 + fm.getAscent() - fm.getDescent()) / 2.0));
        }

        private void drawParticles(Graphics2D g) {
            g.setFont(Theme.uiFont().deriveFont(Font.BOLD, Theme.uiFont().getSize2D() * 1.15f));
            for (Particle p : particles) {
                g.setColor(alpha(p.color, 1.0 - p.age / p.life));
                g.drawString(p.glyph, (float) p.x, (float) p.y);
            }
        }

        /** One-liners that fade in and out along the bottom edge. */
        private void drawFortune(Graphics2D g, Rectangle2D.Double r) {
            double fade = 0.45;
            double a = Math.max(0.0, Math.min(1.0, Math.min(fortuneTime / fade, (FORTUNE_SECONDS - fortuneTime) / fade)));
            g.setFont(Theme.uiFont().deriveFont(Font.ITALIC));
            g.setColor(alpha(Theme.t.text, a * 0.85));
            FontMetrics fm = g.getFontMetrics();
            double width = mugRect().x - 40;
            Graphics2D c = (Graphics2D) g.create();
            c.clip(new Rectangle2D.Double(r.x + 16, r.y + r.height - 30, width, 22));
            c.setFont(g.getFont());
            c.setColor(g.getColor());
            c.drawString(FORTUNES[fortune], (float) (r.x + 16),
                    (float) (r.y + r.height - 30 + (22 + fm.getAscent() - fm.getDescent()) / 2.0));
            c.dispose();
        }
    }
}
