package nortantis.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Draws a checkbox as an on/off switch: a rounded track with a round knob that slides to the right when the checkbox is selected, while the
 * track fades to the accent color. Colors come from the look and feel's checkbox colors, so the switch follows light and dark themes.
 */
public class ToggleSwitchIcon implements Icon
{
	private static final int trackWidth = 28;
	private static final int trackHeight = 16;
	/**
	 * Space around the track for the focus ring.
	 */
	private static final int focusWidth = 2;
	private static final int knobInset = 3;
	private static final float disabledAlpha = 0.4f;
	/**
	 * How long the knob takes to slide all the way across.
	 */
	private static final double slideMillis = 150.0;
	private static final int frameIntervalMillis = 15;

	private final AbstractButton button;
	/**
	 * Where the knob is, from 0 at the left (off) to 1 at the right (on).
	 */
	private double knobPosition;
	private double slideStartPosition;
	/**
	 * When the current slide started, or null if it hasn't had its first frame yet.
	 */
	private Long slideStartNanos;
	private final Timer slideTimer;

	/**
	 * @param button
	 *            The checkbox this icon draws. The icon slides its knob whenever the checkbox is selected or deselected.
	 */
	public ToggleSwitchIcon(AbstractButton button)
	{
		this.button = button;
		knobPosition = getTargetPosition();
		slideTimer = new Timer(frameIntervalMillis, e -> advanceSlide());
		button.addItemListener(e -> startSlide());
	}

	private double getTargetPosition()
	{
		return button.isSelected() ? 1.0 : 0.0;
	}

	private void startSlide()
	{
		if (!button.isShowing())
		{
			// Nobody would see the slide, such as when a map's settings are loaded into a tab that isn't selected.
			slideTimer.stop();
			knobPosition = getTargetPosition();
			button.repaint();
			return;
		}

		slideStartPosition = knobPosition;
		// The slide's clock starts at its first frame rather than now, because toggling a section keeps the event dispatch thread busy for a
		// moment, and a clock that started now would skip the start of the slide.
		slideStartNanos = null;
		slideTimer.start();
	}

	private void advanceSlide()
	{
		if (slideStartNanos == null)
		{
			slideStartNanos = System.nanoTime();
		}
		double target = getTargetPosition();
		double distance = Math.abs(target - slideStartPosition);
		double elapsedMillis = (System.nanoTime() - slideStartNanos) / 1_000_000.0;
		double progress = distance == 0 ? 1.0 : Math.min(1.0, elapsedMillis / (slideMillis * distance));
		// Ease out, so the knob slows as it arrives.
		double eased = 1.0 - Math.pow(1.0 - progress, 3);
		knobPosition = slideStartPosition + (target - slideStartPosition) * eased;
		if (progress >= 1.0)
		{
			knobPosition = target;
			slideTimer.stop();
		}
		button.repaint();
	}

	@Override
	public int getIconWidth()
	{
		return trackWidth + 2 * focusWidth;
	}

	@Override
	public int getIconHeight()
	{
		return trackHeight + 2 * focusWidth;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		// The sliders' blue, so the switches match the sliders beside them.
		Color accent = getColor(new Color(0x2675BF), "Slider.thumbColor", "Component.accentColor", "CheckBox.icon.selectedBackground", "textHighlight");
		Color border = getColor(Color.gray, "CheckBox.icon.borderColor", "Component.borderColor", "controlShadow");
		Color offTrack = getColor(button.getBackground(), "CheckBox.icon.background", "control");
		Color onKnob = getOnKnobColor(accent);

		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			double left = x + focusWidth;
			double top = y + focusWidth;

			if (button.isFocusOwner() && button.isFocusPainted())
			{
				g2.setColor(getColor(accent, "Component.focusColor"));
				g2.fill(new RoundRectangle2D.Double(x, y, getIconWidth(), getIconHeight(), getIconHeight(), getIconHeight()));
			}

			if (!button.isEnabled())
			{
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, disabledAlpha));
			}

			g2.setColor(blend(offTrack, accent, knobPosition));
			g2.fill(new RoundRectangle2D.Double(left, top, trackWidth, trackHeight, trackHeight, trackHeight));
			if (knobPosition < 1.0)
			{
				g2.setColor(blend(border, accent, knobPosition));
				g2.draw(new RoundRectangle2D.Double(left + 0.5, top + 0.5, trackWidth - 1, trackHeight - 1, trackHeight - 1, trackHeight - 1));
			}

			double knobDiameter = trackHeight - 2 * knobInset;
			double knobTravel = trackWidth - 2 * knobInset - knobDiameter;
			double knobLeft = left + knobInset + knobTravel * knobPosition;
			g2.setColor(blend(border, onKnob, knobPosition));
			g2.fill(new Ellipse2D.Double(knobLeft, top + knobInset, knobDiameter, knobDiameter));
		}
		finally
		{
			g2.dispose();
		}
	}

	/**
	 * The knob's color when the switch is on: the look and feel's checkmark color, which dark themes make a soft light gray, unless it doesn't
	 * stand out on the accent color, as in light themes whose checkmarks are the accent color, in which case white.
	 */
	private static Color getOnKnobColor(Color accent)
	{
		final double minBrightnessDifference = 60.0;
		Color checkmark = UIManager.getColor("CheckBox.icon.checkmarkColor");
		if (checkmark != null && getBrightness(checkmark) - getBrightness(accent) >= minBrightnessDifference)
		{
			return checkmark;
		}
		return Color.white;
	}

	/**
	 * Perceived brightness, from 0 to 255.
	 */
	private static double getBrightness(Color color)
	{
		return 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
	}

	/**
	 * Mixes two colors, from all of the first at 0 to all of the second at 1.
	 */
	private static Color blend(Color from, Color to, double amount)
	{
		double a = Math.max(0.0, Math.min(1.0, amount));
		return new Color((int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * a), (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * a),
				(int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * a), (int) Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * a));
	}

	/**
	 * The first of the given look and feel colors that is defined, or the fallback if none are.
	 */
	private static Color getColor(Color fallback, String... keys)
	{
		for (String key : keys)
		{
			Color color = UIManager.getColor(key);
			if (color != null)
			{
				return color;
			}
		}
		return fallback;
	}
}
