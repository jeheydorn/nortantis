package nortantis.swing;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;

import nortantis.editor.UserPreferences;

/**
 * A card that shows a title above content that can be collapsed and expanded. One rounded border surrounds both the title row and the
 * content, and the title row is a tinted, clickable strip inside that border.
 */
@SuppressWarnings("serial")
public class CollapsiblePanel extends JPanel
{
	private static final int cornerArc = 8;
	/**
	 * How far the outline sits in from the card's edge, so that cards stacked against each other keep a visible gap.
	 */
	private static final int cardMargin = 1;
	/**
	 * How far the title strip's color is moved from the panel's background toward its text color.
	 */
	private static final double titleTint = 0.08;
	/**
	 * The tint to use when the text color is darker than the background, since moving a light background that far toward dark text is a
	 * bigger visible step than moving a dark background the same fraction toward light text.
	 */
	private static final double titleTintOnLightBackground = 0.05;
	/**
	 * How much stronger the tint is while the title strip is hovered or focused.
	 */
	private static final double highlightedTintMultiplier = 2.0;

	private final String namespace;
	private final String untranslatedName;
	private final JPanel contentPanel;
	private final JPanel titlePanel;
	private final DisclosureArrow arrow;
	private boolean isCollapsed;
	private boolean isHighlighted;

	/**
	 * Create a CollapsiblePanel with the given name and content.
	 *
	 * @param namespace
	 *            A unique identifier to use when storing the name in user preferences to store the collapsed state. Untranslated name +
	 *            namespace must be globally unique.
	 * @param untranslatedName
	 *            A name that is the same in every language, which the collapsed state is stored under so that it survives changing the
	 *            language.
	 * @param name
	 *            Name to display
	 * @param contentPanel
	 *            Content to display when expanded
	 */
	public CollapsiblePanel(String namespace, String untranslatedName, String name, JPanel contentPanel)
	{
		this(namespace, untranslatedName, name, contentPanel, false);
	}

	/**
	 * @param defaultCollapsed
	 *            Whether the panel starts collapsed when the user hasn't collapsed or expanded it.
	 */
	public CollapsiblePanel(String namespace, String untranslatedName, String name, JPanel contentPanel, boolean defaultCollapsed)
	{
		this.namespace = namespace;
		this.untranslatedName = untranslatedName;
		this.contentPanel = contentPanel;
		isCollapsed = UserPreferences.getInstance().panelCollapsedStates.getOrDefault(getNameKey(), defaultCollapsed);

		setLayout(new BorderLayout());
		setOpaque(false);
		setBorder(new CardBorder());

		arrow = new DisclosureArrow();

		JLabel titleLabel = new JLabel(name);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));

		titlePanel = new JPanel();
		titlePanel.setOpaque(false);
		titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.X_AXIS));
		titlePanel.setBorder(new EmptyBorder(4, 7, 4, 7));
		titlePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		titlePanel.add(arrow);
		titlePanel.add(Box.createHorizontalStrut(6));
		titlePanel.add(titleLabel);
		titlePanel.add(Box.createHorizontalGlue());
		titlePanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				titlePanel.requestFocusInWindow();
				toggleCollapsed();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setHighlighted(true);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setHighlighted(false);
			}
		});

		// The title strip is not a button, so it needs its own keyboard handling to be usable without a mouse.
		titlePanel.setFocusable(true);
		titlePanel.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				setHighlighted(true);
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				setHighlighted(false);
			}
		});
		titlePanel.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("SPACE"), "toggleCollapsed");
		titlePanel.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "toggleCollapsed");
		titlePanel.getActionMap().put("toggleCollapsed", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				toggleCollapsed();
			}
		});

		contentPanel.setBorder(new EmptyBorder(4, 7, 7, 7));
		// The card's rounded bottom corners curve into the content's area, so content that painted its own background would cover them.
		contentPanel.setOpaque(false);

		add(titlePanel, BorderLayout.NORTH);
		add(contentPanel, BorderLayout.CENTER);

		updateCollapsed();
	}

	public void toggleCollapsed()
	{
		isCollapsed = !isCollapsed;
		updateCollapsed();
		storeCollapsedState();
	}

	public boolean isCollapsed()
	{
		return isCollapsed;
	}

	private void updateCollapsed()
	{
		contentPanel.setVisible(!isCollapsed);
		arrow.repaint();
		titlePanel.repaint();
		revalidate();
	}

	private void setHighlighted(boolean isHighlighted)
	{
		this.isHighlighted = isHighlighted;
		titlePanel.repaint();
	}

	/**
	 * Keeps the card from being stretched taller than its content by layouts that hand out extra vertical space, such as BoxLayout.
	 */
	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(super.getMaximumSize().width, getPreferredSize().height);
	}

	private void storeCollapsedState()
	{
		UserPreferences.getInstance().panelCollapsedStates.put(getNameKey(), isCollapsed);
	}

	private String getNameKey()
	{
		return namespace + "~" + untranslatedName;
	}

	private static Color getLineColor()
	{
		Color color = UIManager.getColor("controlShadow");
		return color == null ? Color.GRAY : color;
	}

	private Color getTitleColor()
	{
		Color background = getBackground();
		Color foreground = getForeground();
		double tint = brightness(foreground) < brightness(background) ? titleTintOnLightBackground : titleTint;
		return blend(background, foreground, isHighlighted ? tint * highlightedTintMultiplier : tint);
	}

	private static double brightness(Color color)
	{
		return 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
	}

	private static Color blend(Color from, Color to, double fractionOfTo)
	{
		double fractionOfFrom = 1.0 - fractionOfTo;
		return new Color((int) (from.getRed() * fractionOfFrom + to.getRed() * fractionOfTo),
				(int) (from.getGreen() * fractionOfFrom + to.getGreen() * fractionOfTo),
				(int) (from.getBlue() * fractionOfFrom + to.getBlue() * fractionOfTo));
	}

	private static Graphics2D createAntiAliasedGraphics(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		return g2;
	}

	private static double toDeviceX(AffineTransform transform, double x)
	{
		return transform.getTranslateX() + x * transform.getScaleX();
	}

	private static double toDeviceY(AffineTransform transform, double y)
	{
		return transform.getTranslateY() + y * transform.getScaleY();
	}

	/**
	 * The device x of the center of the outline's left side, for a card whose left edge is at x.
	 */
	private static double getOutlineLeft(AffineTransform transform, double x)
	{
		return Math.ceil(toDeviceX(transform, x + cardMargin)) + 0.5;
	}

	/**
	 * The device x of the center of the outline's right side, for a card whose left edge is at x.
	 */
	private static double getOutlineRight(AffineTransform transform, double x, double width)
	{
		return Math.floor(toDeviceX(transform, x + width - cardMargin)) - 0.5;
	}

	/**
	 * Draws the title strip's background, and the line under it when the card is expanded. The card paints this itself, rather than the
	 * title strip painting its own background, so that the fill can run under the card's border instead of stopping a fraction of a pixel
	 * short of it.
	 */
	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		Graphics2D g2 = createAntiAliasedGraphics(g);
		try
		{
			// When the card is collapsed the title strip is the whole card, so it rounds its bottom corners too. Otherwise it is filled tall
			// enough for its bottom corners to fall past the strip, and clipped to the strip.
			int stripBottom = titlePanel.getY() + titlePanel.getHeight();
			int height = (isCollapsed ? getHeight() - cardMargin : stripBottom + cornerArc) - cardMargin;
			if (!isCollapsed)
			{
				g2.clipRect(0, 0, getWidth(), stripBottom);
			}
			g2.setColor(getTitleColor());
			g2.fillRoundRect(cardMargin, cardMargin, getWidth() - cardMargin * 2, height, cornerArc, cornerArc);

			if (!isCollapsed)
			{
				AffineTransform transform = g2.getTransform();
				double lineY = Math.floor(toDeviceY(transform, stripBottom)) - 0.5;
				// The line ends where the outline's sides are drawn, so it doesn't stick out past them.
				double left = getOutlineLeft(transform, 0);
				double right = getOutlineRight(transform, 0, getWidth());
				g2.setTransform(new AffineTransform());
				g2.setStroke(new BasicStroke(1f));
				g2.setColor(getLineColor());
				g2.draw(new Line2D.Double(left, lineY, right, lineY));
			}
		}
		finally
		{
			g2.dispose();
		}
	}

	/**
	 * The triangle that shows whether the card is expanded. It is drawn rather than written as text so that it does not depend on which
	 * fonts are installed.
	 */
	private class DisclosureArrow extends JComponent
	{
		DisclosureArrow()
		{
			Dimension size = new Dimension(10, 10);
			setPreferredSize(size);
			setMinimumSize(size);
			setMaximumSize(size);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = createAntiAliasedGraphics(g);
			try
			{
				double width = getWidth();
				double height = getHeight();
				Polygon triangle = new Polygon();
				if (isCollapsed)
				{
					triangle.addPoint((int) (width * 0.25), (int) (height * 0.1));
					triangle.addPoint((int) (width * 0.25), (int) (height * 0.9));
					triangle.addPoint((int) (width * 0.75), (int) (height * 0.5));
				}
				else
				{
					triangle.addPoint((int) (width * 0.1), (int) (height * 0.3));
					triangle.addPoint((int) (width * 0.9), (int) (height * 0.3));
					triangle.addPoint((int) (width * 0.5), (int) (height * 0.8));
				}
				g2.setColor(blend(getBackground(), getForeground(), 0.75));
				g2.fill(triangle);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	/**
	 * The card's outline. It is drawn a pixel in from the card's edge so that cards stacked against each other keep a visible gap, and it
	 * is snapped to whole pixels of the display so that it stays a crisp line at fractional UI scales instead of being spread across two
	 * rows of pixels, which makes an edge look faint or missing.
	 */
	private static class CardBorder extends AbstractBorder
	{
		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
		{
			Graphics2D g2 = createAntiAliasedGraphics(g);
			try
			{
				AffineTransform transform = g2.getTransform();
				double left = getOutlineLeft(transform, x);
				double top = Math.ceil(toDeviceY(transform, y + cardMargin)) + 0.5;
				double right = getOutlineRight(transform, x, width);
				double bottom = Math.floor(toDeviceY(transform, y + height - cardMargin)) - 0.5;
				double arcWidth = cornerArc * transform.getScaleX();
				double arcHeight = cornerArc * transform.getScaleY();

				g2.setTransform(new AffineTransform());
				g2.setStroke(new BasicStroke(1f));
				g2.setColor(getLineColor());
				g2.draw(new RoundRectangle2D.Double(left, top, right - left, bottom - top, arcWidth, arcHeight));
			}
			finally
			{
				g2.dispose();
			}
		}

		@Override
		public Insets getBorderInsets(Component c, Insets insets)
		{
			int inset = cardMargin + 1;
			insets.set(inset, inset, inset, inset);
			return insets;
		}

		@Override
		public Insets getBorderInsets(Component c)
		{
			return getBorderInsets(c, new Insets(0, 0, 0, 0));
		}
	}
}
