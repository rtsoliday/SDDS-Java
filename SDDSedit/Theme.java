/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;

import SDDS.java.SDDS.SDDSUtil;

/**
 * The editor's colors and fonts, the same palette as the Qt <code>sddseditor</code>.
 * It is light or dark to match the desktop when the toolkit reports that. The
 * <code>sddsedit.theme</code> property or <code>SDDSEDIT_THEME</code> environment
 * variable can force <code>light</code> or <code>dark</code>.
 */
final class Theme {
    /** The theme in use, chosen once at startup. */
    static Theme t = new Theme(false);

    final boolean dark;
    final Color win, chrome, surface, header, border, grid, text, muted;
    final Color accent, accentSoft, accentText, onAccent, zebra, warn, hover;
    final Color gridSoft, ok, hatch;
    final Color intBg, intFg, fltBg, fltFg, txtBg, txtFg;

    private Theme(boolean dark) {
        this.dark = dark;
        if (dark) {
            win = c("#16181C");
            chrome = c("#1D2025");
            surface = c("#1B1E23");
            header = c("#252930");
            border = c("#363B44");
            grid = c("#2A2E35");
            text = c("#E6E8EC");
            muted = c("#A3ABB8");
            accent = c("#7AA7FF");
            accentSoft = c("#22324F");
            accentText = c("#B9CFFF");
            onAccent = c("#0B1220");
            zebra = c("#1F2228");
            warn = c("#F0A04B");
            hover = c("#2A2F37");
            gridSoft = c("#202328");
            ok = c("#4CC27E");
            hatch = c("#2E333B");
            intBg = c("#1F2D4A");
            intFg = c("#A9C3FF");
            fltBg = c("#16342B");
            fltFg = c("#85DCC0");
            txtBg = c("#3A2C14");
            txtFg = c("#F0C47E");
        } else {
            win = c("#F4F5F7");
            chrome = c("#FBFBFC");
            surface = c("#FFFFFF");
            header = c("#F1F3F6");
            border = c("#DCE0E6");
            grid = c("#ECEEF2");
            text = c("#1C2230");
            muted = c("#5A6374");
            accent = c("#2463D6");
            accentSoft = c("#E6EEFC");
            accentText = c("#1A4FB0");
            onAccent = c("#FFFFFF");
            zebra = c("#FAFBFC");
            warn = c("#A6520A");
            hover = c("#ECEFF3");
            gridSoft = c("#F3F4F6");
            ok = c("#1F8A4C");
            hatch = c("#E1E4EA");
            intBg = c("#E8EFFD");
            intFg = c("#1F4FB2");
            fltBg = c("#E1F3EB");
            fltFg = c("#0E6A4E");
            txtBg = c("#FCEED6");
            txtFg = c("#8A4A00");
        }
    }

    private static Color c(String hex) {
        return Color.decode(hex);
    }

    /** The two colors of a type badge: integers, floating point, or text. */
    Color[] badgeColors(int type) {
        if (type == SDDSUtil.SDDS_STRING || type == SDDSUtil.SDDS_CHARACTER)
            return new Color[] { txtBg, txtFg };
        if (type == SDDSUtil.SDDS_FLOAT || type == SDDSUtil.SDDS_DOUBLE || type == SDDSUtil.SDDS_LONGDOUBLE)
            return new Color[] { fltBg, fltFg };
        return new Color[] { intBg, intFg };
    }

    /** The color of a type's marker in the outline. */
    Color markerColor(int type) {
        return badgeColors(type)[1];
    }

    /* ---- fonts ---- */

    private static Font uiFont;
    private static Font tableFont;

    private static Set<String> installedFonts() {
        return new HashSet<String>(
                Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
    }

    /** The interface font. */
    static Font uiFont() {
        if (uiFont == null) {
            String family = Font.DIALOG;
            Set<String> installed = installedFonts();
            for (String name : new String[] { "Noto Sans", "Cantarell", "Segoe UI", "Helvetica Neue", "Inter",
                    "DejaVu Sans", "Liberation Sans" }) {
                if (installed.contains(name)) {
                    family = name;
                    break;
                }
            }
            uiFont = withFallback(family, 14);
        }
        return uiFont;
    }

    /** The table font, the first installed of the fixed-width fonts the Qt editor prefers. */
    static Font tableFont() {
        if (tableFont == null) {
            String family = Font.MONOSPACED;
            Set<String> installed = installedFonts();
            for (String name : new String[] { "Source Code Pro", "JetBrains Mono", "Cascadia Mono", "Consolas", "Menlo",
                    "DejaVu Sans Mono", "Liberation Mono" }) {
                if (installed.contains(name)) {
                    family = name;
                    break;
                }
            }
            tableFont = withFallback(family, 14);
        }
        return tableFont;
    }

    /* A font from StyleContext draws characters it lacks with a font that has them. */
    private static Font withFallback(String family, int size) {
        return new javax.swing.text.StyleContext().getFont(family, Font.PLAIN, size);
    }

    static Font bold(Font font) {
        return font.deriveFont(Font.BOLD);
    }

    static Font small(Font font) {
        return font.deriveFont(Math.max(9f, font.getSize2D() - 1f));
    }

    /** The font of a type badge. */
    static Font badgeFont() {
        Font font = uiFont();
        return font.deriveFont(Font.BOLD, Math.max(9f, font.getSize2D() - 2f));
    }

    /* ---- painting ---- */

    static Dimension badgeSize(Font font, String text, FontMetrics fm) {
        return new Dimension(fm.stringWidth(text) + 12, fm.getHeight() + 2);
    }

    /**
     * Paint a rounded badge with its top-left corner at the given point.
     *
     * @param border outline color, or <code>null</code> for none
     * @return its width
     */
    static int paintBadge(Graphics2D g, int x, int y, String text, Font font, Color bg, Color fg, Color border) {
        FontMetrics fm = g.getFontMetrics(font);
        Dimension size = badgeSize(font, text, fm);
        Graphics2D g2 = (Graphics2D) g.create();
        smooth(g2);
        g2.setColor(bg);
        g2.fill(new RoundRectangle2D.Float(x, y, size.width, size.height, 8, 8));
        if (border != null) {
            g2.setColor(border);
            g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, size.width - 1, size.height - 1, 8, 8));
        }
        g2.setFont(font);
        g2.setColor(fg);
        g2.drawString(text, x + 6, y + 1 + fm.getAscent());
        g2.dispose();
        return size.width;
    }

    /** Diagonal hatching that lines up across neighboring cells. */
    static void paintHatch(Graphics2D g, Rectangle rect, Color color, int originX, int originY) {
        int spacing = 6;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.clipRect(rect.x, rect.y, rect.width, rect.height);
        g2.setColor(color);
        int left = rect.x + originX, top = rect.y + originY;
        int first = (left + top) / spacing * spacing - spacing;
        for (int s = first; s <= left + rect.width + top + rect.height + spacing; s += spacing)
            g2.drawLine(s - top - originX, rect.y, s - top - rect.height - 1 - originX, rect.y + rect.height + 1);
        g2.dispose();
    }

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** Shorten text to fit a width with an ellipsis at the end. */
    static String elide(String text, FontMetrics fm, int width) {
        if (fm.stringWidth(text) <= width)
            return text;
        int ellipsis = fm.stringWidth("\u2026");
        int used = 0, i = 0;
        while (i < text.length() && used + fm.charWidth(text.charAt(i)) + ellipsis <= width)
            used += fm.charWidth(text.charAt(i++));
        return text.substring(0, i) + "\u2026";
    }

    /* ---- startup ---- */

    private static boolean wantsDark() {
        String choice = System.getProperty("sddsedit.theme");
        if (choice == null)
            choice = System.getenv("SDDSEDIT_THEME");
        if (choice != null)
            return choice.equalsIgnoreCase("dark");
        Object name = Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Net/ThemeName");
        return name != null && name.toString().toLowerCase().contains("dark");
    }

    /** Choose the theme and set up Swing to use it. Call once, before making any window. */
    static void install() {
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");
        t = new Theme(wantsDark());
        MetalLookAndFeel.setCurrentTheme(new OceanTheme());
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (UnsupportedLookAndFeelException e) {
            /* The default look still works. */
        }
        Font font = uiFont();
        FontUIResource ui = new FontUIResource(font);
        for (String key : new String[] { "Button", "CheckBox", "CheckBoxMenuItem", "ComboBox", "EditorPane",
                "FormattedTextField", "Label", "List", "Menu", "MenuBar", "MenuItem", "OptionPane", "Panel",
                "PasswordField", "PopupMenu", "RadioButton", "RadioButtonMenuItem", "ScrollPane", "Spinner", "TabbedPane",
                "Table", "TableHeader", "TextArea", "TextField", "TextPane", "TitledBorder", "ToggleButton", "ToolBar",
                "ToolTip", "Tree", "Viewport" })
            UIManager.put(key + ".font", ui);
        ColorUIResource win = new ColorUIResource(t.win), text = new ColorUIResource(t.text);
        ColorUIResource surface = new ColorUIResource(t.surface), chrome = new ColorUIResource(t.chrome);
        ColorUIResource muted = new ColorUIResource(t.muted), border = new ColorUIResource(t.border);
        ColorUIResource soft = new ColorUIResource(t.accentSoft), accent = new ColorUIResource(t.accent);
        for (String key : new String[] { "Panel", "OptionPane", "Label", "CheckBox", "RadioButton", "Viewport",
                "ScrollPane", "Slider", "ToolTip", "TabbedPane" }) {
            UIManager.put(key + ".background", win);
            UIManager.put(key + ".foreground", text);
        }
        for (String key : new String[] { "TextField", "TextArea", "EditorPane", "TextPane", "List", "Table", "Tree",
                "FormattedTextField", "PasswordField", "Spinner", "ComboBox" }) {
            UIManager.put(key + ".background", surface);
            UIManager.put(key + ".foreground", text);
            UIManager.put(key + ".caretForeground", text);
            UIManager.put(key + ".selectionBackground", soft);
            UIManager.put(key + ".selectionForeground", text);
            UIManager.put(key + ".inactiveBackground", win);
            UIManager.put(key + ".inactiveForeground", muted);
        }
        UIManager.put("ComboBox.squareButton", Boolean.FALSE);
        UIManager.put("ComboBox.disabledBackground", win);
        UIManager.put("ComboBox.disabledForeground", muted);
        UIManager.put("Button.background", chrome);
        UIManager.put("Button.foreground", text);
        UIManager.put("Button.select", new ColorUIResource(t.hover));
        UIManager.put("Button.focus", accent);
        /* A null in the user defaults would only reveal the look and feel's own entry. */
        for (String key : new String[] { "Button.gradient", "ToggleButton.gradient", "RadioButton.gradient",
                "CheckBox.gradient", "ScrollBar.gradient", "Slider.gradient", "Slider.focusGradient",
                "MenuBar.gradient", "CheckBoxMenuItem.gradient", "RadioButtonMenuItem.gradient" })
            UIManager.getLookAndFeelDefaults().remove(key);
        UIManager.put("MenuBar.background", chrome);
        UIManager.put("MenuBar.foreground", text);
        UIManager.put("MenuBar.border", BorderFactory.createEmptyBorder());
        for (String key : new String[] { "Menu", "MenuItem", "CheckBoxMenuItem", "RadioButtonMenuItem", "PopupMenu" }) {
            UIManager.put(key + ".background", surface);
            UIManager.put(key + ".foreground", text);
            UIManager.put(key + ".selectionBackground", soft);
            UIManager.put(key + ".selectionForeground", new ColorUIResource(t.accentText));
            UIManager.put(key + ".acceleratorForeground", muted);
            UIManager.put(key + ".acceleratorSelectionForeground", muted);
            UIManager.put(key + ".disabledForeground", muted);
        }
        UIManager.put("Menu.background", chrome);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(t.border));
        UIManager.put("Separator.background", surface);
        UIManager.put("Separator.foreground", border);
        UIManager.put("ToolTip.background", surface);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(t.border),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        UIManager.put("Label.disabledForeground", muted);
        UIManager.put("control", win);
        UIManager.put("controlText", text);
        UIManager.put("text", surface);
        UIManager.put("textText", text);
    }
}
