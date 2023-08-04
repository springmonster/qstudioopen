package com.timestored.qstudio;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.timestored.docs.Document;
import com.timestored.docs.OpenDocumentsModel;
import com.timestored.messages.Msg;
import com.timestored.messages.Msg.Key;
import com.timestored.qdoc.HtmlPqfOutputter;
import com.timestored.qstudio.QLicenser.Section;
import com.timestored.swingxx.SwingUtils;
import com.timestored.theme.Theme;

/**
 * Allows a user to choose a folder and generate qdoc documentation for al open files.
 */
public class GenerateDocumentationAction extends AbstractAction {

    private static final Logger LOG = Logger.getLogger(GenerateDocumentationAction.class.getName());

    private final OpenDocumentsModel openDocumentsModel;
    private File lastSelectedDocGenFolder;


    public GenerateDocumentationAction(final OpenDocumentsModel openDocumentsModel) {
        super(Msg.get(Key.GENERATE), Theme.CIcon.TEXT_HTML.get16());
        this.openDocumentsModel = Preconditions.checkNotNull(openDocumentsModel);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (QLicenser.requestPermission(Section.QDOC)) {
            runQDocAction();
        }
    }


    private void runQDocAction() {

        JFileChooser fc = lastSelectedDocGenFolder == null ? new JFileChooser() : new JFileChooser(lastSelectedDocGenFolder);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(Msg.get(Key.SELECT_DOC_DIR));
        fc.setDialogType(JFileChooser.SAVE_DIALOG);
        fc.setApproveButtonText(Msg.get(Key.GENERATE2));

        if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            lastSelectedDocGenFolder = fc.getSelectedFile();
            List<Document> docs = openDocumentsModel.getDocuments();
            List<String> errors = HtmlPqfOutputter.output(docs, lastSelectedDocGenFolder, null);
            if (errors.size() > 0) {
                String title = Msg.get(Key.ERROR_GENERATING_DOCS);
                String message = Joiner.on("\r\n").join(errors);
                JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
            } else {
                File f = new File(lastSelectedDocGenFolder.getAbsolutePath()
                        + File.separator + "index.html");
                SwingUtils.offerToOpenFile(Msg.get(Key.DOCS_GENERATED), f,
                        Msg.get(Key.OPEN_DOCS_NOW), Msg.get(Key.CLOSE));
            }
        } else {
            LOG.info("Generate documentation command cancelled by user.");
        }
    }
}
