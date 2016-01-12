package com.bytezone.dm3270.filetransfer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.bytezone.dm3270.assistant.Dataset;
import com.bytezone.dm3270.display.ScreenWatcher;
import com.bytezone.dm3270.utilities.FileSaver;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

public class UploadDialog extends TransferDialog
{
  Label labelFromFolder = new Label ();
  Label labelFileDate = new Label ();
  Label labelDatasetDate = new Label ();

  public UploadDialog (ScreenWatcher screenWatcher, Path homePath, int baseLength)
  {
    super (screenWatcher, homePath, baseLength);

    GridPane grid = new GridPane ();

    grid.add (new Label ("Dataset"), 1, 1);
    grid.add (datasetComboBox, 2, 1);

    grid.add (new Label ("From folder"), 1, 2);
    grid.add (labelFromFolder, 2, 2);

    grid.add (new Label ("File date"), 1, 3);
    grid.add (labelFileDate, 2, 3);

    grid.add (new Label ("Dataset date"), 1, 4);
    grid.add (labelDatasetDate, 2, 4);

    grid.setHgap (10);
    grid.setVgap (10);

    dialog.setTitle ("Upload dataset");
    dialog.getDialogPane ().setContent (grid);

    ButtonType btnTypeOK = new ButtonType ("OK", ButtonData.OK_DONE);
    ButtonType btnTypeCancel = new ButtonType ("Cancel", ButtonData.CANCEL_CLOSE);
    dialog.getDialogPane ().getButtonTypes ().addAll (btnTypeOK, btnTypeCancel);

    Node okButton = dialog.getDialogPane ().lookupButton (btnTypeOK);
    okButton.setDisable (true);

    dialog.setResultConverter (btnType ->
    {
      if (btnType != btnTypeOK)
        return null;

      String datasetName = datasetComboBox.getSelectionModel ().getSelectedItem ();
      IndFileCommand indFileCommand =
          new IndFileCommand (getCommandText ("PUT", datasetName));

      String saveFolderName = FileSaver.getSaveFolderName (homePath, datasetName);
      Path saveFile = Paths.get (saveFolderName, datasetName);
      indFileCommand.setLocalFile (saveFile.toFile ());

      return indFileCommand;
    });

    labelFileDate.textProperty ().addListener ( (observable, oldValue,
        newValue) -> okButton.setDisable (newValue.trim ().isEmpty ()));

    refreshUpload ();
    datasetComboBox.setOnAction (event -> refreshUpload ());
  }

  private void refreshUpload ()
  {
    String datasetSelected = datasetComboBox.getSelectionModel ().getSelectedItem ();
    if (datasetSelected == null)    // could be rebuilding the list
      return;

    String saveFolderName = FileSaver.getSaveFolderName (homePath, datasetSelected);
    Path saveFile = Paths.get (saveFolderName, datasetSelected);

    labelFromFolder.setText (saveFolderName.substring (baseLength));
    Optional<Dataset> dataset = screenWatcher.getDataset (datasetSelected);
    if (dataset.isPresent ())
    {
      String date = dataset.get ().getReferredDate ();
      if (date.isEmpty ())
        labelDatasetDate.setText ("<no date>");
      else
      {
        String reformattedDate = date.substring (8) + "/" + date.substring (5, 7) + "/"
            + date.substring (0, 4);
        labelDatasetDate
            .setText (reformattedDate + " " + dataset.get ().getReferredTime ());
      }
    }
    else
      System.out.println ("not found");

    if (Files.exists (saveFile))
      labelFileDate.setText (formatDate (saveFile));
    else
      labelFileDate.setText ("");
  }
}