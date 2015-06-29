package com.bytezone.dm3270.commands;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.bytezone.dm3270.display.Cursor;
import com.bytezone.dm3270.display.Field;
import com.bytezone.dm3270.display.FieldManager;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.display.ScreenDetails;
import com.bytezone.dm3270.orders.BufferAddress;
import com.bytezone.dm3270.orders.BufferAddressSource;
import com.bytezone.dm3270.orders.Order;
import com.bytezone.dm3270.orders.SetBufferAddressOrder;
import com.bytezone.dm3270.orders.TextOrder;

public class AIDCommand extends Command implements BufferAddressSource, Iterable<Order>
{
  public static final byte NO_AID_SPECIFIED = 0x60;
  public static final byte AID_READ_PARTITION = 0x61;
  public static final byte AID_PA3 = 0x6B;
  public static final byte AID_PA1 = 0x6C;
  public static final byte AID_CLEAR = 0x6D;
  public static final byte AID_PA2 = 0x6E;
  public static final byte AID_ENTER = 0x7D;
  public static final byte AID_PF7 = (byte) 0xF7;
  public static final byte AID_PF8 = (byte) 0xF8;
  public static final byte AID_PF10 = (byte) 0x7A;
  public static final byte AID_PF11 = (byte) 0x7B;

  private static byte[] keys =
      { 0, NO_AID_SPECIFIED, AID_ENTER,    //
        (byte) 0xF1, (byte) 0xF2, (byte) 0xF3, (byte) 0xF4, (byte) 0xF5, (byte) 0xF6,
        (byte) 0xF7, (byte) 0xF8, (byte) 0xF9, (byte) 0x7A, (byte) 0x7B, (byte) 0x7C,
        (byte) 0xC1, (byte) 0xC2, (byte) 0xC3, (byte) 0xC4, (byte) 0xC5, (byte) 0xC6,
        (byte) 0xC7, (byte) 0xC8, (byte) 0xC9, (byte) 0x4A, (byte) 0x4B, (byte) 0x4C,
        AID_PA1, AID_PA2, AID_PA3, AID_CLEAR, (byte) 0x6A, AID_READ_PARTITION };

  private static String[] keyNames =
      { "Not found", "No AID", "ENTR",    //
        "PF1", "PF2", "PF3", "PF4", "PF5", "PF6", "PF7", "PF8", "PF9", "PF10", "PF11",
        "PF12", "PF13", "PF14", "PF15", "PF16", "PF17", "PF18", "PF19", "PF20", "PF21",
        "PF22", "PF23", "PF24",    //
        "PA1", "PA2", "PA3", "CLR", "CLR Partition", "Read Partition" };

  private int key;
  private byte keyCommand;
  private BufferAddress cursorAddress;

  private final List<ModifiedField> modifiedFields = new ArrayList<> ();
  private final List<Order> orders = new ArrayList<> ();
  private final List<Order> textOrders = new ArrayList<> ();

  // Called by Screen.readBuffer()
  public AIDCommand (Screen screen, byte[] buffer, int offset, int length)
  {
    super (buffer, offset, length, screen);    // copies buffer[offset:length] to data[]

    keyCommand = data[0];
    key = findKey (keyCommand);

    if (length <= 1)
    {
      cursorAddress = null;
      return;
    }

    cursorAddress = new BufferAddress (data[1], data[2]);

    int ptr = 3;
    int max = length;
    Order previousOrder = null;
    ModifiedField currentAIDField = null;

    while (ptr < max)
    {
      Order order = Order.getOrder (data, ptr, max);
      if (!order.rejected ())
      {
        if (previousOrder != null && previousOrder.matches (order))
          previousOrder.incrementDuplicates ();
        else
        {
          orders.add (order);
          previousOrder = order;
        }

        if (order instanceof SetBufferAddressOrder)
        {
          currentAIDField = new ModifiedField ((SetBufferAddressOrder) order);
          modifiedFields.add (currentAIDField);
        }
        else if (currentAIDField != null)
          currentAIDField.addOrder (order);

        if (order instanceof TextOrder)
          textOrders.add (order);
      }
      ptr += order.size ();
    }
  }

  public boolean isPAKey ()
  {
    // ignore any PA key reply caused by ReadModifiedAll
    return (data.length == 1
        && (keyCommand == AID_PA1 || keyCommand == AID_PA2 || keyCommand == AID_PA3));
  }

  public boolean matches (AIDCommand otherCommand)
  {
    if (data.length != otherCommand.data.length)
      return false;

    // skip cursor position
    for (int i = 3; i < data.length; i++)
      if (data[i] != otherCommand.data[i])
        return false;

    return true;
  }

  private int findKey (byte keyCommand)
  {
    for (int i = 1; i < keys.length; i++)
      if (keys[i] == keyCommand)
        return i;
    return 0;
  }

  public void scramble ()
  {
    for (ModifiedField aidField : modifiedFields)
      aidField.scramble ();
  }

  // copy modified fields back to the screen - only used in Replay mode
  // Normally an AID is a reply command (which never has process() called)
  // Testing out whether the plugin reply should pass through here.
  @Override
  public void process ()
  {
    // test to see whether this is data entry that was null suppressed into moving
    // elsewhere on the screen (like the TSO logoff command) - purely aesthetic
    boolean done = modifiedFields.size () == 1 && checkForPrettyMove ();

    if (!done)
    {
      ScreenDetails screenDetails = screen.getScreenDetails ();
      FieldManager fieldManager = screen.getFieldManager ();
      Field tsoCommandField = screenDetails.getTSOCommandField ();

      if (modifiedFields.size () == 0 && screenDetails.isTSOCommandScreen ())
      {
        String tsoCommand = tsoCommandField.getText ();
        if (!tsoCommand.isEmpty ())
          screen.addTSOCommand (tsoCommand);
      }

      for (ModifiedField aidField : modifiedFields)
      {
        Field field = fieldManager.getField (aidField.getLocation ());
        if (field == null)
          continue;             // in replay mode we cannot rely on the fields list

        if (aidField.hasData ())
        {
          byte[] buffer = aidField.getBuffer ();
          field.setText (buffer);

          if (field == tsoCommandField)
            screen.addTSOCommand (field.getText ());
        }
        else
          field.erase ();

        field.draw ();
      }
    }

    // place cursor in new location
    if (cursorAddress != null)
      screen.getScreenCursor ().moveTo (cursorAddress.getLocation ());

    screen.lockKeyboard (keyNames[key]);
  }

  private boolean checkForPrettyMove ()
  {
    Cursor cursor = screen.getScreenCursor ();
    Field currentField = cursor.getCurrentField ();
    if (currentField != null)
    {
      int cursorOldLocation = cursor.getLocation ();
      if (cursorOldLocation != currentField.getFirstLocation ()
          && currentField.contains (cursorOldLocation))
      {
        int cursorDistance = cursorAddress.getLocation () - cursorOldLocation;
        byte[] buffer = modifiedFields.get (0).getBuffer ();
        if (buffer.length == cursorDistance)
        {
          // cannot call field.setText() as the data starts mid-field
          for (byte b : buffer)
            cursor.typeChar (b);   // send characters through the old cursor
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public BufferAddress getBufferAddress ()
  {
    return cursorAddress;
  }

  public byte getKeyCommand ()
  {
    return keyCommand;
  }

  @Override
  public String getName ()
  {
    return "AID : " + keyNames[key];
  }

  public static byte getKey (String name)
  {
    int ptr = 0;
    for (String keyName : keyNames)
    {
      if (keyName.equals (name))
        return keys[ptr];
      ptr++;
    }
    return -1;
  }

  public String getKeyName ()
  {
    return keyNames[key];
  }

  public int countTextOrders ()
  {
    return textOrders.size ();
  }

  public int countOrders ()
  {
    return orders.size ();
  }

  public byte[] getText (int index)
  {
    return textOrders.get (index).getBuffer ();
  }

  @Override
  public String toString ()
  {
    StringBuilder text = new StringBuilder ();
    text.append (String.format ("AID     : %-12s : %02X%n", keyNames[key], keyCommand));

    if (cursorAddress != null)
      text.append (String.format ("Cursor  : %s%n", cursorAddress));

    if (modifiedFields.size () > 0)
    {
      text.append (String.format ("%nModified fields  : %d", modifiedFields.size ()));
      for (ModifiedField aidField : modifiedFields)
      {
        text.append ("\nField   : ");
        text.append (aidField);
      }
    }
    // response to a read buffer request
    else if (orders.size () > 0)
    {
      text.append (String.format ("%nOrders  : %d%n",
                                  orders.size () - textOrders.size ()));
      text.append (String.format ("Text    : %d%n", textOrders.size ()));

      // if the list begins with a TextOrder then tab out the missing columns
      if (orders.size () > 0 && orders.get (0) instanceof TextOrder)
        text.append (String.format ("%40s", ""));

      for (Order order : orders)
      {
        String fmt = (order instanceof TextOrder) ? "%s" : "%n%-40s";
        text.append (String.format (fmt, order));
      }
    }
    return text.toString ();
  }

  // This class is used to collect information about each modified field specified
  // in the AIDCommand.
  private class ModifiedField
  {
    SetBufferAddressOrder sbaOrder;
    List<Order> orders = new ArrayList<> ();

    public ModifiedField (SetBufferAddressOrder sbaOrder)
    {
      this.sbaOrder = sbaOrder;
    }

    public void addOrder (Order order)
    {
      orders.add (order);
    }

    public int getLocation ()
    {
      return sbaOrder.getBufferAddress ().getLocation ();
    }

    public void scramble ()
    {
      for (Order order : orders)
        if (order instanceof TextOrder)
          ((TextOrder) order).scramble ();
    }

    public boolean hasData ()
    {
      return getBuffer ().length > 0;
    }

    public byte[] getBuffer ()
    {
      for (Order order : orders)
        if (order instanceof TextOrder)
          return ((TextOrder) order).getBuffer ();     // only returning the first one!!!
      return new byte[0];
    }

    @Override
    public String toString ()
    {
      StringBuilder text = new StringBuilder ();
      text.append (String.format ("%-40s", sbaOrder));
      for (Order order : orders)
      {
        if (!(order instanceof TextOrder))
          text.append (String.format ("\n        : %-40s", order));
        else
          text.append (order);
      }
      return text.toString ();
    }
  }

  @Override
  public Iterator<Order> iterator ()
  {
    return orders.iterator ();
  }
}