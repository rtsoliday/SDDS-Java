/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

/**
 * Non-modal help window: a topic list, the help text, and a find bar that highlights every
 * match. The topic list follows the text as it scrolls.
 */
final class HelpDialog extends JDialog {
    private static HelpDialog instance;

    /** Show the help window, reusing it if it is already open so it can stay beside the editor. */
    static void showHelp(Frame owner) {
        if (instance == null) {
            instance = new HelpDialog(owner);
            instance.setVisible(true);
            return;
        }
        instance.setVisible(true);
        instance.toFront();
        instance.requestFocus();
    }

    private final JList<String> topicList;
    private final JEditorPane browser;
    private final JScrollPane browserScroll;
    private final Flat.SearchField findField;
    private final JLabel matchLabel;
    private final List<String> anchors = new ArrayList<String>();
    private int[] topicOffsets;
    private final List<int[]> matches = new ArrayList<int[]>();
    private final List<Object> tags = new ArrayList<Object>();
    private int currentMatch = -1;
    private int currentMatchStart = -1;
    private boolean navigating;

    private static final Highlighter.HighlightPainter ALL_PAINTER = new MatchPainter(48);
    private static final Highlighter.HighlightPainter CURRENT_PAINTER = new MatchPainter(150);

    private static final class MatchPainter extends DefaultHighlighter.DefaultHighlightPainter {
        MatchPainter(int alpha) {
            super(new Color(Theme.t.accent.getRed(), Theme.t.accent.getGreen(), Theme.t.accent.getBlue(), alpha));
        }
    }

    private HelpDialog(Frame owner) {
        super(owner, "SDDS Editor Help", false);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        getContentPane().setBackground(Theme.t.win);

        findField = new Flat.SearchField("Find in help", Theme.t.surface);
        findField.setFont(Theme.uiFont());
        findField.setPreferredSize(new Dimension(280, 30));
        Flat.ToolButton prev = new Flat.ToolButton("", rotated(Icons.CHEVRON_DOWN.icon(14, Theme.t.muted)),
                "Previous match (Shift+F3)");
        Flat.ToolButton next = new Flat.ToolButton("", Icons.CHEVRON_DOWN.icon(14, Theme.t.muted),
                "Next match (F3)");
        matchLabel = new JLabel();
        matchLabel.setFont(Theme.uiFont());
        matchLabel.setForeground(Theme.t.muted);
        matchLabel.setPreferredSize(new Dimension(
                matchLabel.getFontMetrics(Theme.uiFont()).stringWidth("No matches") + 12, 24));

        JPanel findBar = new JPanel(new GridBagLayout());
        findBar.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        findBar.add(findField, c);
        c.weightx = 0;
        c.insets = new Insets(0, 6, 0, 0);
        findBar.add(prev, c);
        c.insets = new Insets(0, 0, 0, 0);
        findBar.add(next, c);
        c.insets = new Insets(0, 4, 0, 0);
        findBar.add(matchLabel, c);
        c.weightx = 0.0001;
        findBar.add(Box.createHorizontalStrut(1), c);

        DefaultListModel<String> model = new DefaultListModel<String>();
        StringBuilder html = new StringBuilder();
        int listWidth = 0;
        FontMetrics fm = getFontMetrics(Theme.uiFont());
        for (String[] topic : HelpTopics.TOPICS) {
            model.addElement(topic[1]);
            anchors.add(topic[0]);
            listWidth = Math.max(listWidth, fm.stringWidth(topic[1]));
            html.append("<h2><a name=\"").append(topic[0]).append("\">").append(topic[1]).append("</a></h2>")
                    .append(topic[2]);
        }
        topicList = new JList<String>(model);
        topicList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        topicList.setOpaque(false);
        topicList.setFixedCellHeight(32);
        topicList.setCellRenderer(new TopicRenderer());
        topicList.setSelectedIndex(0);
        JScrollPane listScroll = Flat.scrollPane(new JScrollPane(topicList));
        listScroll.getViewport().setBackground(Theme.t.chrome);
        Flat.Card listCard = new Flat.Card(new BorderLayout(), Theme.t.chrome, Theme.t.border, 10);
        listCard.setBorder(Flat.pad(5, 5, 5, 5));
        listCard.add(listScroll);

        browser = new JEditorPane();
        HTMLEditorKit kit = new HTMLEditorKit();
        kit.setStyleSheet(styleSheet(kit.getStyleSheet()));
        browser.setEditorKit(kit);
        browser.setEditable(false);
        browser.setBackground(Theme.t.surface);
        browser.setMargin(new Insets(14, 14, 14, 14));
        browser.setSelectionColor(Theme.t.accentSoft);
        browser.setSelectedTextColor(Theme.t.text);
        browser.setText("<html><body>" + html + "</body></html>");
        browser.setCaretPosition(0);
        browserScroll = Flat.scrollPane(new JScrollPane(browser));
        Flat.Card browserCard = new Flat.Card(new BorderLayout(), Theme.t.surface, Theme.t.border, 10);
        browserCard.setBorder(Flat.pad(5, 5, 5, 5));
        browserCard.add(browserScroll);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listCard, browserCard);
        split.setUI(new BasicSplitPaneUI() {
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    public void paint(Graphics g) {
                        g.setColor(Theme.t.win);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        split.setBorder(null);
        split.setBackground(Theme.t.win);
        split.setDividerSize(8);
        split.setContinuousLayout(true);
        split.setDividerLocation(listWidth + 56);
        split.setResizeWeight(0);
        listCard.setMinimumSize(new Dimension(listWidth + 40, 100));

        JButton close = Flat.button("Close");
        close.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(close);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setOpaque(false);
        root.setBorder(Flat.pad(12, 12, 12, 12));
        root.add(findBar, BorderLayout.NORTH);
        root.add(split);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        root.setBackground(Theme.t.win);
        root.setOpaque(true);

        topicList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting() && !navigating)
                    showTopic(topicList.getSelectedIndex());
            }
        });
        browserScroll.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
            public void adjustmentValueChanged(AdjustmentEvent e) {
                followScroll();
            }
        });
        browser.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED)
                    return;
                String target = e.getDescription();
                if (target != null && target.startsWith("#"))
                    showTopic(anchors.indexOf(target.substring(1)));
            }
        });
        findField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateMatches(true);
            }

            public void removeUpdate(DocumentEvent e) {
                updateMatches(true);
            }

            public void changedUpdate(DocumentEvent e) {
            }
        });
        // Enter in the field steps through matches; there is no default button to close the window.
        findField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                findNext((e.getModifiers() & ActionEvent.SHIFT_MASK) != 0 ? -1 : 1);
            }
        });
        prev.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                findNext(-1);
            }
        });
        next.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                findNext(1);
            }
        });
        Flat.dialogKeys(this, null);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), new Runnable() {
            public void run() {
                findField.requestFocusInWindow();
                findField.selectAll();
            }
        });
        Runnable forward = new Runnable() {
            public void run() {
                findNext(1);
            }
        };
        Runnable backward = new Runnable() {
            public void run() {
                findNext(-1);
            }
        };
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), forward);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK), forward);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK), backward);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), backward);

        setSize(900, 640);
        setLocationRelativeTo(owner);
        // Layout settles after the first show, so measure the anchors then.
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                measureTopics();
            }
        });
        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                measureTopics();
            }
        });
    }

    private void bind(KeyStroke key, final Runnable action) {
        Object name = "help" + key;
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private static Icon rotated(final Icon icon) {
        return new Icon() {
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.transform(AffineTransform.getRotateInstance(Math.PI, x + getIconWidth() / 2.0,
                        y + getIconHeight() / 2.0));
                icon.paintIcon(c, g2, x, y);
                g2.dispose();
            }

            public int getIconWidth() {
                return icon.getIconWidth();
            }

            public int getIconHeight() {
                return icon.getIconHeight();
            }
        };
    }

    private static StyleSheet styleSheet(StyleSheet base) {
        Theme t = Theme.t;
        String mono = Theme.tableFont().getFamily();
        String ui = Theme.uiFont().getFamily();
        StyleSheet ss = new StyleSheet();
        ss.addStyleSheet(base);
        String[] rules = {
            "body { font-family: '" + ui + "'; font-size: 14px; color: " + hex(t.text) + "; background-color: "
                    + hex(t.surface) + "; }",
            "p, li { font-family: '" + ui + "'; font-size: 14px; margin-top: 3px; margin-bottom: 3px; color: "
                    + hex(t.text) + "; }",
            "td, th { font-family: '" + ui + "'; font-size: 14px; color: " + hex(t.text) + "; }",
            "h2 { font-family: '" + ui + "'; color: " + hex(t.accentText)
                    + "; font-size: 18px; margin-top: 20px; margin-bottom: 6px; }",
            "h3 { font-family: '" + ui + "'; color: " + hex(t.text)
                    + "; font-size: 15px; margin-top: 14px; margin-bottom: 4px; }",
            "code { font-family: '" + mono + "'; font-size: 13px; color: " + hex(t.accentText) + "; }",
            ".key { font-family: '" + mono + "'; font-size: 13px; background-color: " + hex(t.header)
                    + "; color: " + hex(t.text) + "; }",
            ".note { color: " + hex(t.muted) + "; }",
            "table { margin-top: 4px; margin-bottom: 8px; }",
            "th { background-color: " + hex(t.header) + "; color: " + hex(t.text)
                    + "; text-align: left; font-weight: bold; }",
            "td, th { border-bottom-width: 1px; border-bottom-style: solid; border-bottom-color: " + hex(t.border)
                    + "; }",
            "a { color: " + hex(t.accent) + "; }",
        };
        for (String rule : rules)
            ss.addRule(rule);
        return ss;
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Record where each topic heading sits in the text. */
    private void measureTopics() {
        javax.swing.text.Document doc = browser.getDocument();
        if (!(doc instanceof HTMLDocument))
            return;
        int[] offsets = new int[anchors.size()];
        java.util.Arrays.fill(offsets, -1);
        for (HTMLDocument.Iterator it = ((HTMLDocument) doc).getIterator(HTML.Tag.A); it.isValid(); it.next()) {
            Object name = it.getAttributes().getAttribute(HTML.Attribute.NAME);
            int row = name == null ? -1 : anchors.indexOf(name.toString());
            if (row >= 0)
                offsets[row] = it.getStartOffset();
        }
        topicOffsets = offsets;
        followScroll();
    }

    private int topicY(int row) {
        if (topicOffsets == null || row < 0 || row >= topicOffsets.length || topicOffsets[row] < 0)
            return -1;
        try {
            Rectangle2D r = browser.modelToView2D(topicOffsets[row]);
            return r == null ? -1 : (int) r.getY();
        } catch (BadLocationException e) {
            return -1;
        }
    }

    /** Scroll to a topic, by its row in the topic list. */
    private void showTopic(int row) {
        if (row < 0 || row >= anchors.size())
            return;
        if (topicOffsets == null)
            measureTopics();
        int y = topicY(row);
        if (y < 0)
            return;
        navigating = true;
        try {
            if (topicList.getSelectedIndex() != row)
                topicList.setSelectedIndex(row);
            JScrollBar bar = browserScroll.getVerticalScrollBar();
            bar.setValue(Math.max(0, y - 6));
        } finally {
            navigating = false;
        }
    }

    /** Select the topic whose heading is at or above the top of the view. */
    private void followScroll() {
        if (navigating || topicOffsets == null)
            return;
        int top = browserScroll.getVerticalScrollBar().getValue();
        int row = 0;
        for (int i = 0; i < topicOffsets.length; i++) {
            int y = topicY(i);
            if (y >= 0 && y <= top + 24)
                row = i;
        }
        if (topicList.getSelectedIndex() != row) {
            navigating = true;
            try {
                topicList.setSelectedIndex(row);
                topicList.ensureIndexIsVisible(row);
            } finally {
                navigating = false;
            }
        }
    }

    /** Move to the next (1) or previous (-1) match, wrapping around. */
    private void findNext(int direction) {
        if (matches.isEmpty())
            return;
        currentMatch = (currentMatch + direction + matches.size()) % matches.size();
        showMatches(true);
    }

    /** Find every match, keeping the current one or starting from the visible text. */
    private void updateMatches(boolean moveToMatch) {
        String needle = findField.getText().toLowerCase(java.util.Locale.ROOT);
        int from = currentMatchStart;
        if (from < 0) {
            Point top = browserScroll.getViewport().getViewPosition();
            from = browser.viewToModel2D(new Point(0, top.y));
        }
        matches.clear();
        currentMatch = -1;
        if (!needle.isEmpty()) {
            String text;
            try {
                javax.swing.text.Document doc = browser.getDocument();
                text = doc.getText(0, doc.getLength()).toLowerCase(java.util.Locale.ROOT);
            } catch (BadLocationException e) {
                text = "";
            }
            for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
                if (currentMatch < 0 && at >= from)
                    currentMatch = matches.size();
                matches.add(new int[] { at, at + needle.length() });
            }
            if (currentMatch < 0 && !matches.isEmpty())
                currentMatch = 0;
        }
        showMatches(moveToMatch);
    }

    private void showMatches(boolean moveToMatch) {
        Highlighter highlighter = browser.getHighlighter();
        for (Object tag : tags)
            highlighter.removeHighlight(tag);
        tags.clear();
        try {
            for (int i = 0; i < matches.size(); i++) {
                int[] m = matches.get(i);
                tags.add(highlighter.addHighlight(m[0], m[1], i == currentMatch ? CURRENT_PAINTER : ALL_PAINTER));
            }
        } catch (BadLocationException e) {
            /* A stale range can only mean the text changed; there is nothing to mark. */
        }
        boolean empty = findField.getText().isEmpty();
        boolean missing = !empty && matches.isEmpty();
        matchLabel.setText(empty ? "" : missing ? "No matches" : (currentMatch + 1) + " of " + matches.size());
        matchLabel.setForeground(missing ? Theme.t.warn : Theme.t.muted);

        currentMatchStart = currentMatch >= 0 ? matches.get(currentMatch)[0] : -1;
        if (!moveToMatch || currentMatch < 0)
            return;
        try {
            Rectangle2D r = browser.modelToView2D(currentMatchStart);
            JViewport viewport = browserScroll.getViewport();
            int top = viewport.getViewPosition().y;
            int height = viewport.getExtentSize().height;
            if (r != null && (r.getY() < top || r.getY() + r.getHeight() > top + height))
                browserScroll.getVerticalScrollBar().setValue((int) Math.max(0, r.getY() - height / 3));
        } catch (BadLocationException e) {
            /* Leave the view where it is. */
        }
    }

    private static final class TopicRenderer extends JComponent implements ListCellRenderer<String> {
        private String text = "";
        private boolean selected;

        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                boolean isSelected, boolean focus) {
            text = value;
            selected = isSelected;
            return this;
        }

        public Dimension getPreferredSize() {
            return new Dimension(100, 32);
        }

        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.smooth(g2);
            if (selected) {
                g2.setColor(Theme.t.accentSoft);
                g2.fillRoundRect(0, 1, getWidth(), getHeight() - 2, 10, 10);
            }
            g2.setFont(Theme.uiFont());
            g2.setColor(selected ? Theme.t.accentText : Theme.t.text);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 10, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }
}
