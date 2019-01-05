package nortantis;

import java.awt.Color;

public enum Biome
{
    /*
    Colors converted to rgb:
     OCEAN: [r=68,g=68,b=122]
		LAKE: [r=51,g=102,b=153]
		BEACH: [r=160,g=144,b=119]
		SNOW: [r=255,g=255,b=255]
		TUNDRA: [r=187,g=187,b=170]
		BARE: [r=136,g=136,b=136]
		SCORCHED: [r=85,g=85,b=85]
		TAIGA: [r=153,g=170,b=119]
		SHURBLAND: [r=136,g=153,b=119]
		TEMPERATE_DESERT: [r=201,g=210,b=155]
		HIGH_TEMPERATE_DESERT: [r=189,g=196,b=153]
	 	TEMPERATE_RAIN_FOREST: [r=68,g=136,b=85]
		TEMPERATE_DECIDUOUS_FOREST: [r=103,g=148,b=89]
		HIGH_TEMPERATE_DECIDUOUS_FOREST: [r=74,b=119,b=61],
		GRASSLAND: [r=136,g=170,b=85]
		SUBTROPICAL_DESERT: [r=210,g=185,b=139]
		SHRUBLAND: [r=136,g=153,b=119]
		ICE: [r=153,g=255,b=255]
		MARSH: [r=47,g=102,b=102]
		TROPICAL_RAIN_FOREST: [r=51,g=119,b=85]
		TROPICAL_SEASONAL_FOREST: [r=85,g=153,b=68]
		COAST: [r=51,g=51,b=90]
		LAKESHORE: [r=34,g=85,b=136]

  */

     OCEAN(0x44447a), 
     LAKE(0x336699),
     BEACH(0xa09077), 
     SNOW(0xffffff),
     TUNDRA(0xbbbbaa),
     BARE(0x888888), 
     SCORCHED(0x555555), 
     TAIGA(0x99aa77),
     SHURBLAND(0x889977), 
     TEMPERATE_DESERT(0xc9d29b),
     HIGH_TEMPERATE_DESERT(0xbdc499),
     TEMPERATE_RAIN_FOREST(0x448855), 
     TEMPERATE_DECIDUOUS_FOREST(0x679459), 
     HIGH_TEMPERATE_DECIDUOUS_FOREST(0x4a773d),
     GRASSLAND(0x88aa55), SUBTROPICAL_DESERT(0xd2b98b),
     SHRUBLAND(0x889977),
     ICE(0x99ffff), 
     MARSH(0x2f6666), 
     TROPICAL_RAIN_FOREST(0x337755),
     TROPICAL_SEASONAL_FOREST(0x559944), 
     COAST(0x33335a),
     LAKESHORE(0x225588);
     Color color;

     Biome(int color) 
     {
         this.color = new Color(color);
     }
}
