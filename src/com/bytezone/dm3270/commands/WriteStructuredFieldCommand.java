package com.bytezone.dm3270.commands;

import java.util.ArrayList;
import java.util.List;

import com.bytezone.dm3270.buffers.Buffer;
import com.bytezone.dm3270.buffers.MultiBuffer;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.filetransfer.FileTransferOutboundSF;
import com.bytezone.dm3270.structuredfields.DefaultStructuredField;
import com.bytezone.dm3270.structuredfields.EraseResetSF;
import com.bytezone.dm3270.structuredfields.Outbound3270DS;
import com.bytezone.dm3270.structuredfields.ReadPartitionSF;
import com.bytezone.dm3270.structuredfields.SetReplyModeSF;
import com.bytezone.dm3270.structuredfields.StructuredField;
import com.bytezone.dm3270.utilities.Utility;

public class WriteStructuredFieldCommand extends Command
{
  private static final String line =
      "\n-------------------------------------------------------------------------";

  private final List<StructuredField> fields = new ArrayList<StructuredField> ();
  private final List<Buffer> replies = new ArrayList<> ();

  public WriteStructuredFieldCommand (byte[] buffer, int offset, int length)
  {
    super (buffer, offset, length);

    assert buffer[offset] == Command.WRITE_STRUCTURED_FIELD_11
        || buffer[offset] == Command.WRITE_STRUCTURED_FIELD_F3;

    int ptr = offset + 1;
    int max = offset + length;

    while (ptr < max)
    {
      int size = Utility.unsignedShort (buffer, ptr) - 2;
      ptr += 2;

      switch (buffer[ptr])
      {
        // wrapper for original write commands - W. EW, EWA, EAU
        case StructuredField.OUTBOUND_3270DS:
          fields.add (new Outbound3270DS (buffer, ptr, size));
          break;

        // wrapper for original read commands - RB, RM, RMA
        case StructuredField.READ_PARTITION:
          fields.add (new ReadPartitionSF (buffer, ptr, size));
          break;

        case StructuredField.RESET_PARTITION:
          System.out.println ("SF_RESET_PARTITION (00) not written yet");
          fields.add (new DefaultStructuredField (buffer, ptr, size));
          break;

        case StructuredField.SET_REPLY_MODE:
          fields.add (new SetReplyModeSF (buffer, ptr, size));
          break;

        case StructuredField.ACTIVATE_PARTITION:
          System.out.println ("SF_ACTIVATE_PARTITION (0E) not written yet");
          fields.add (new DefaultStructuredField (buffer, ptr, size));
          break;

        case StructuredField.ERASE_RESET:
          fields.add (new EraseResetSF (buffer, ptr, size));
          break;

        case StructuredField.IND$FILE:
          fields.add (new FileTransferOutboundSF (buffer, ptr, size));
          break;

        default:
          fields.add (new DefaultStructuredField (buffer, ptr, size));
          break;
      }

      ptr += size;
    }
  }

  @Override
  public void process (Screen screen)
  {
    replies.clear ();

    for (StructuredField sf : fields)
    {
      sf.process (screen);
      Buffer reply = sf.getReply ();
      if (reply != null)
        replies.add (reply);
    }
  }

  @Override
  public Buffer getReply ()
  {
    if (replies.size () == 0)
      return null;

    if (replies.size () == 1)
      return replies.get (0);

    MultiBuffer multiBuffer = new MultiBuffer ();
    for (Buffer reply : replies)
      multiBuffer.addBuffer (reply);

    return multiBuffer;
  }

  @Override
  public String brief ()
  {
    StringBuilder text = new StringBuilder (String.format ("WSF (%d):", fields.size ()));

    for (StructuredField sf : fields)
      text.append ("\n       : " + sf.brief ());

    return text.toString ();
  }

  @Override
  public String getName ()
  {
    return "Write SF";
  }

  @Override
  public String toString ()
  {
    StringBuilder text = new StringBuilder (String.format ("WSF (%d):", fields.size ()));

    for (StructuredField sf : fields)
    {
      text.append (line);
      text.append ("\n");
      text.append (sf);
    }

    if (fields.size () > 0)
      text.append (line);

    return text.toString ();
  }
}