package com.bytezone.dm3270.attributes;

import com.bytezone.dm3270.display.Pen;

public class ExtendedHighlight extends Attribute
{
  private static String[] highlights =
      { "xx", "Blink", "Reverse video", "bb", "Underscore" };

  public ExtendedHighlight (byte value)
  {
    super (AttributeType.HIGHLIGHT, Attribute.XA_HIGHLIGHTING, value);
  }

  @Override
  public void process (Pen pen)
  {
    pen.setHighlight (attributeValue);
  }

  @Override
  public String toString ()
  {
    String valueText = attributeValue == 0 ? "Reset" : highlights[attributeValue & 0x0F];
    return String.format ("%-12s : %02X %s", name (), attributeValue, valueText);
  }
}