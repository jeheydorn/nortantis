package nortantis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import nortantis.platform.Color;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;

public class TextStyleOverridesTest
{
	@BeforeAll
	public static void setUpBeforeClass()
	{
		PlatformFactory.setInstance(new AwtFactory());
	}

	private static TextStyle createTypeStyle()
	{
		return new TextStyle(Font.create("Georgia", FontStyle.Plain, 20), Color.black, TextBackground.createDefault());
	}

	@Test
	public void textWithNoOverridesIsDrawnWithItsTypesStyle()
	{
		TextStyle typeStyle = createTypeStyle();
		assertSame(typeStyle, new TextStyleOverrides().resolve(typeStyle));
	}

	@Test
	public void partsWithoutOverridesFollowChangesToTheTypesStyle()
	{
		TextStyle typeStyle = createTypeStyle();
		TextStyleOverrides overrides = new TextStyleOverrides();
		TextStyle ownStyle = typeStyle.copy();
		ownStyle.font = ownStyle.font.deriveFont(FontStyle.Plain, 36);
		overrides.set(TextStylePart.Size, ownStyle);

		typeStyle.font = Font.create("Tangerine", FontStyle.Italic, 12);
		typeStyle.color = Color.create(10, 20, 30, 255);
		TextStyle resolved = overrides.resolve(typeStyle);

		assertEquals("Tangerine", resolved.font.getName(), "The font family follows the type.");
		assertEquals(FontStyle.Italic, resolved.font.getStyle());
		assertEquals(36f, resolved.font.getSize(), "The text keeps its own size.");
		assertEquals(typeStyle.color, resolved.color);
		assertEquals(EnumSet.of(TextStylePart.Size), overrides.getParts());
	}

	@Test
	public void clearingAPartMakesItFollowTheTypeAgain()
	{
		TextStyle typeStyle = createTypeStyle();
		TextStyleOverrides overrides = new TextStyleOverrides();
		TextStyle ownStyle = typeStyle.copy();
		ownStyle.color = Color.create(200, 0, 0, 255);
		ownStyle.background.effect = TextBackgroundEffect.Glow;
		overrides.set(TextStylePart.Color, ownStyle);
		overrides.set(TextStylePart.Background, ownStyle);

		overrides.clear(TextStylePart.Color);

		assertEquals(EnumSet.of(TextStylePart.Background), overrides.getParts());
		assertEquals(typeStyle.color, overrides.resolve(typeStyle).color);
		assertEquals(TextBackgroundEffect.Glow, overrides.resolve(typeStyle).background.effect);
	}

	@Test
	public void overridesAreSavedAndReadBack()
	{
		TextStyle ownStyle = createTypeStyle();
		ownStyle.font = Font.create("Tangerine", FontStyle.BoldItalic, 27);
		ownStyle.color = Color.create(1, 2, 3, 255);
		ownStyle.background.effect = TextBackgroundEffect.Scroll;
		ownStyle.background.fadeBehind = 0.3;
		TextStyleOverrides overrides = new TextStyleOverrides();
		for (TextStylePart part : TextStylePart.values())
		{
			overrides.set(part, ownStyle);
		}

		assertEquals(overrides, TextStyleOverrides.fromJson(overrides.toJson()));
		assertTrue(TextStyleOverrides.fromJson(new TextStyleOverrides().toJson()).isEmpty());
	}
}
