package com.timestored.qstudio.servertree;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import kx.c.KException;

import com.google.common.base.Preconditions;
import com.timestored.kdb.KdbConnection;
import com.timestored.qstudio.QStudioLauncher;
import com.timestored.qstudio.kdb.KdbTableFactory;
import com.timestored.qstudio.model.AdminModel;
import com.timestored.theme.Theme;

/**
 * Displays tables as pa
 */
class PagingTablePanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(PagingTablePanel.class.getName());
    private static final long serialVersionUID = 1L;

    //TODO partitioned table with count below 200, .Q.ind causes error in PagingTablePanel
    /**
     * if partitioned use .Q.ind otherwise use sublist to index into table
     */
    private static final String SUBBLIST_Q = "{$[.Q.qp[y]; " +
            "$[count y; .Q.ind[y;`long$x[0] + til min (count y),x[1]]; select from y]; " +
            "sublist[`int$x;y]]}";

    private final long rowsShown = 200;
    private long offset = 0;
    private long count;

    private final String queryName;
    private final JButton firstButton;
    private final JButton prevButton;
    private final JButton nextButton;
    private JButton lastButton;
    private Component scrollPane;
    private final JLabel positionLabel;
    private final AdminModel adminModel;


    PagingTablePanel(AdminModel adminModel,
                     String queryName) throws IOException, KException {

        this.queryName = Preconditions.checkNotNull(queryName);
        this.adminModel = Preconditions.checkNotNull(adminModel);

        // paging controls
        firstButton = new JButton("<<");
        firstButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshToShow(0);
            }
        });
        prevButton = new JButton("<");
        prevButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshToShow(offset - rowsShown);
            }
        });
        nextButton = new JButton(">");
        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshToShow(offset + rowsShown);
            }
        });
        lastButton = new JButton(">>");
        lastButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PagingTablePanel.this.refreshToShow(count - rowsShown);
            }
        });
        positionLabel = new JLabel();

        new JLabel("Rows Shown");
        lastButton = new JButton(">>");
        lastButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PagingTablePanel.this.refreshToShow(count - rowsShown);
            }
        });

//		JToolBar toolbar = new JToolBar();
        JPanel toolbar = new JPanel();
        toolbar.add(firstButton);
        toolbar.add(prevButton);
        toolbar.add(nextButton);
        toolbar.add(lastButton);
        toolbar.add(positionLabel);

        scrollPane = new JScrollPane(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        refreshToShow(offset);
    }

    public static String getTableQuery(String tableName, long offset, long rowsShown) {

        String query = "(count " + tableName + ";" + SUBBLIST_Q +
                "[" + offset + " " + rowsShown
                + "j;" + tableName + "])";
        return query;
    }

    private void refreshToShow(long offset) {

        // wipe GUI
        KdbConnection kdbConnection = null;
        remove(scrollPane);
        Component sp = new JPanel();
        Exception e = null;

        this.offset = offset;
        // perform necessary query
        String query = getTableQuery(queryName, offset, rowsShown);

        kdbConnection = adminModel.getKdbConnection();
        boolean prevPossible = false;
        boolean nextPossible = false;
        String posText = "";

        try {

            if (kdbConnection != null) {
                Object[] resArray = (Object[]) kdbConnection.query(query);
                count = ((Number) resArray[0]).longValue();

                // configure controls
                prevPossible = (offset > 0);
                nextPossible = ((offset + rowsShown) < count);

                // table of results, update view
                long np = (offset + rowsShown);
                posText = offset + "-" + ((np > count) ? count : np) + " of " + count;

                sp = KdbTableFactory.getJXTable(resArray[1]);
            } else {
                String txt = "Could not establish a connection to server: " + adminModel.getSelectedServerName();
                sp = Theme.getErrorBox("No Connection", Theme.getTextArea("errTxt", txt));
            }

        } catch (KException ke) {
            e = ke;
        } catch (IOException ioe) {
            e = ioe;
        } catch (Exception ex) {
            e = ex;
        } finally {
            if (kdbConnection != null) {
                try {
                    kdbConnection.close();
                } catch (IOException ioe) {
                    LOG.log(Level.WARNING, "Problem closing KDB connection", e);
                }
            }
        }

        // set gui appearance
        firstButton.setEnabled(prevPossible);
        prevButton.setEnabled(prevPossible);
        nextButton.setEnabled(nextPossible);
        lastButton.setEnabled(nextPossible);
        positionLabel.setText(posText);

        // log and present user option to report in case this is a bug
        if (e != null) {
            String shortDesc = "Error showing selected item, try refreshing the servers object tree";
            LOG.log(Level.WARNING, shortDesc, e);
            sp = QStudioLauncher.ERR_REPORTER.getErrorReportLink(e, shortDesc);
        }

        // re-set GUI
        add(sp, BorderLayout.CENTER);
        scrollPane = sp;
        revalidate();
    }
}
