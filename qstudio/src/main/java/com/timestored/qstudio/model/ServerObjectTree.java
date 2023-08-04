package com.timestored.qstudio.model;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.activation.UnsupportedDataTypeException;
import javax.sql.rowset.CachedRowSet;

import kx.c.Dict;
import kx.c.KException;
import lombok.AllArgsConstructor;
import lombok.Data;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.timestored.connections.ConnectionManager;
import com.timestored.connections.ServerConfig;
import com.timestored.cstore.CAtomTypes;
import com.timestored.kdb.KdbConnection;
import com.timestored.qstudio.QStudioLauncher;

/**
 * Provides programmatic access to a java representation of the objects
 * that exist in the KDB servers memory. At any time the selected possibilities
 * can be:
 * <ul>
 * 	<li>nothing selected (all null)</li>
 *  <li>namespace,category selected (category wont be element)</li>
 *  <li>namespace,category,element selected</li>
 *  </ul>
 */
public class ServerObjectTree {

    private static final Logger LOG = Logger.getLogger(ServerObjectTree.class.getName());
    public static final Set<String> BUILTIN_NS = ImmutableSet.of(".Q", ".j", ".q", ".h", ".o");

    /**
     * Query that returns a dictionary from
     * namespace->varNames->(type; count; isTable; isPartitioned; colnames/funcArgs; isView)
     */
    private static final String GET_TREE_QUERY = "/ qstudio - get server tree \r\n" +
            "{   nsl:\".\",/:string `,key `;    \r\n" +
            "    nsf:{[ns] \r\n        ff:{ [viewset; v; fullname; sname]\r\n" +
            "            findColArgs:{$[.Q.qt x; cols x; 100h~type x; (value x)1; `$()]};\r\n" +
            "            safeCount: {$[.Q.qp x; $[`pn in key `.Q; {$[count x;sum x;-1]} .Q.pn y; -1]; count x]};\r\n" +
            "            (@[type;v;0h]; .[safeCount;(v;fullname);-2]; @[.Q.qt;v;0b]; @[.Q.qp;v;0b]; @[findColArgs;v;()]; .[in;(sname;viewset);0b])};\r\n" +
            "        vws: system \"b \",ns;\r\n        n: asc key[`$ns] except `;\r\n" +
            "        fn: $[ns~enlist \".\"; n; ns,/:\".\",/:string n];\r\n" +
            "        n!.'[ ff[vws;;;]; flip ( @[`$ns; n]; fn; n)]};\r\n" +
            "    (`$nsl)!@[nsf;;()!()] each nsl}[]";

    private static final String DEFAULT_NAMESPACE = ".";

    private static final List<ServerQEntity> EMPTY_LIST = Collections.emptyList();
    private RefreshResult refreshResult;
    private final ServerConfig serverConfig;
    private final ConnectionManager connectionManager;
    private boolean errorRetrievingTree = false;

    @Data
    @AllArgsConstructor
    private static class RefreshResult {
        private final Map<String, NamespaceListing> namespaceListingMap;
        private final String errMsg;

        public RefreshResult() {
            this(new HashMap<>(), "");
        }
    }

    /**
     * Construct tree for given config / connection. Connection is allowed to be null.
     *
     * @param connectionManager connection to relevant server or null if no connection possible.
     * @param serverConfig
     */
    ServerObjectTree(ConnectionManager connectionManager, ServerConfig serverConfig) {

        this.serverConfig = Preconditions.checkNotNull(serverConfig);
        this.connectionManager = Preconditions.checkNotNull(connectionManager);

        refreshResult = new RefreshResult();
        refreshFromServer();
    }


    private void refreshFromServer() {
        refreshResult = refreshTree(serverConfig, connectionManager);
    }


    private static RefreshResult refreshTree(ServerConfig serverConfig, ConnectionManager connectionManager) {
        Preconditions.checkNotNull(serverConfig);
        Preconditions.checkNotNull(connectionManager);

        Exception e = null;
        String errMsg = "";
        Map<String, NamespaceListing> namespaceListingMap = Collections.emptyMap();

        try {
            if (serverConfig.isKDB()) {
                namespaceListingMap = getNSListing(serverConfig, connectionManager);
            } else {
                namespaceListingMap = getNSqlListing(serverConfig, connectionManager);
            }
        } catch (KException ke) {
            e = ke;
            errMsg = "Kdb Exception when querying server. Ensure server security settings ok.";
        } catch (IOException ioe) {
            e = ioe;
            errMsg = "IO Error communicating with Server: " + ioe.getMessage();
        } catch (Exception ex) {
            e = ex;
            errMsg = "Exception when querying server. Ensure server security settings ok.";
        }

        if (e != null) {
            LOG.log(Level.WARNING, errMsg, e);
        }
        return new RefreshResult(namespaceListingMap, errMsg);
    }


    private static Map<String, NamespaceListing> getNSqlListing(ServerConfig serverConfig, ConnectionManager connectionManager)
            throws IOException, KException, UnsupportedDataTypeException {

        Map<String, List<String>> tableNamesToColNames = Collections.emptyMap();
        try {
            tableNamesToColNames = connectionManager.getMetaInfo(serverConfig);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error creating SQL server tree", e);
        }

        List<ServerQEntity> allElements = new ArrayList<>();
        for (Entry<String, List<String>> tblCols : tableNamesToColNames.entrySet()) {
            String serverName = serverConfig.getName();
            boolean isView = false;
            String[] colNames = tblCols.getValue().toArray(new String[]{});
            short typNum = (short) CAtomTypes.TABLE.getTypeNum();
            ServerQEntity sqe = ServerQEntityFactory.get(serverName, ".", tblCols.getKey(), typNum, 0, true, false, isView, colNames);
            allElements.add(sqe);
        }

        HashMap<String, NamespaceListing> r = new HashMap<String, NamespaceListing>();
        r.put(".", new NamespaceListing(allElements));
        return r;
    }


    private static Map<String, NamespaceListing> getNSListing(ServerConfig serverConfig, ConnectionManager connectionManager)
            throws IOException, KException, UnsupportedDataTypeException {
        KdbConnection kdbConn = connectionManager.getKdbConnection(serverConfig);
        if (kdbConn == null) {
            throw new IOException("Could not connect to kdb server");
        }

        Object o = kdbConn.query(GET_TREE_QUERY);
        String errMsg = "";

        if (!(o instanceof Dict)) {
            errMsg = "Never received proper format reply from server.";
            throw new UnsupportedDataTypeException(errMsg);
        }

        Dict tree = (Dict) o;
        String[] namespaces = (String[]) tree.x;
        Object[] nsList = (Object[]) tree.y;
        Map<String, NamespaceListing> ns2e = new HashMap<String, NamespaceListing>(namespaces.length);
        ArrayList<String> problemNSs = new ArrayList<String>();

        for (int i = 0; i < nsList.length; i++) {

            String ns = namespaces[i];
            List<ServerQEntity> allElements;

            if (nsList[i] instanceof Dict) {
                Dict nsTree = (Dict) nsList[i];
                allElements = toElementListing(serverConfig.getName(), ns, nsTree);
                ns2e.put(ns, new NamespaceListing(allElements));
            } else {
                problemNSs.add(ns);
            }
        }

        if (!problemNSs.isEmpty()) {
            String msg = "Could not refresh the server tree namespaces:"
                    + Joiner.on(',').join(problemNSs);
            LOG.log(Level.SEVERE, msg);
            QStudioLauncher.ERR_REPORTER.showReportErrorDialog(msg);
        }

        try {
            kdbConn.close();
        } catch (IOException ioe) {
            LOG.log(Level.SEVERE, "Error closing kdbConn for server tree");
        }

        return ns2e;
    }

    public Set<String> getNamespaces() {
        return refreshResult.namespaceListingMap.keySet();
    }

    private static List<ServerQEntity> toElementListing(String serverName,
                                                        String namespace, Dict tree) {

        String[] elementNames = (String[]) tree.x;
        Object[] detailsArray = (Object[]) tree.y;

        if (elementNames.length > 0) {
            List<ServerQEntity> r = new ArrayList<ServerQEntity>(elementNames.length);
            for (int i = 0; i < elementNames.length; i++) {
                try {
                    Object[] d = (Object[]) detailsArray[i];
                    Short type = (d[0] instanceof Short) ? (Short) d[0] : 0;
                    boolean isTable = (d[2] instanceof Boolean) ? (Boolean) d[2] : false;
                    boolean partitioned = (d[3] instanceof Boolean) ? (Boolean) d[3] : false;
                    String[] colNames = d[4] instanceof String[] ? (String[]) d[4] : null;
                    boolean isView = (d[5] instanceof Boolean) ? (Boolean) d[5] : false;
                    long count = d[1] instanceof Number ? ((Number) d[1]).longValue() : -1;

                    ServerQEntity sqe;
                    sqe = ServerQEntityFactory.get(serverName, namespace, elementNames[i], type,
                            count, isTable, partitioned, isView, colNames);
                    if (sqe == null) {
                        LOG.warning("unrecognised ServerQEntity: " + namespace + "." + elementNames[i]);
                    } else {
                        r.add(sqe);
                    }
                } catch (IllegalArgumentException iae) {
                    String msg = "unrecognised ServerQEntity: " + namespace + "." + elementNames[i];
                    LOG.log(Level.WARNING, msg, iae);
                } catch (ClassCastException cce) {
                    String msg = "unrecognised ServerQEntity: " + namespace + "." + elementNames[i];
                    LOG.log(Level.WARNING, msg, cce);
                }
            }
            return r;
        }

        return Collections.emptyList();
    }


    /**
     * @return List of all elements contained in all namespaces
     */
    public List<ServerQEntity> getAll() {
        List<ServerQEntity> entities = Lists.newArrayList();
        for (Entry<String, NamespaceListing> e : refreshResult.namespaceListingMap.entrySet()) {
            NamespaceListing nsl = e.getValue();
            entities.addAll(nsl.getAllElements());
            if (nsl.getAllElements().size() > 0) {
                ServerQEntity firstItem = nsl.getAllElements().get(0);
                entities.add(ServerQEntityFactory.getDict(firstItem.getSource(), firstItem.getNamespace(), "", 1));
            }
        }
        return entities;
    }

    /**
     * @return List of all elements contained in selected namespace
     */
    List<ServerQEntity> getAll(final String namespace) {
        NamespaceListing nsl = refreshResult.namespaceListingMap.get(namespace);
        return nsl == null ? EMPTY_LIST : nsl.getAllElements();
    }

    public List<ServerQEntity> getAll(Collection<String> namespaces) {
        List<ServerQEntity> r = new ArrayList<ServerQEntity>();
        for (String ns : namespaces) {
            NamespaceListing nsl = refreshResult.namespaceListingMap.get(ns);
            if (nsl != null) {
                r.addAll(nsl.getAllElements());
            }
        }
        return r;
    }

    /**
     * @return List of tables contained in selected namespace
     */
    public List<TableSQE> getTables(final String namespace) {
        NamespaceListing nsl = refreshResult.namespaceListingMap.get(namespace);
        List<TableSQE> r = Collections.emptyList();
        return nsl == null ? r : nsl.getTables();
    }

    /**
     * @return List of views contained in selected namespace
     */
    public List<ServerQEntity> getViews(final String namespace) {
        NamespaceListing nsl = refreshResult.namespaceListingMap.get(namespace);
        return nsl == null ? EMPTY_LIST : nsl.getViews();
    }

    /**
     * @return List of variables contained in selected namespace
     */
    public List<ServerQEntity> getVariables(final String namespace) {
        NamespaceListing nsl = refreshResult.namespaceListingMap.get(namespace);
        return nsl == null ? EMPTY_LIST : nsl.getVariables();
    }

    /**
     * @return List of functions contained in selected namespace
     */
    public List<ServerQEntity> getFunctions(final String... namespaces) {
        return getFunctions(Arrays.asList(namespaces));
    }


    public List<ServerQEntity> getFunctions(Collection<String> namespaces) {
        List<ServerQEntity> r = new ArrayList<ServerQEntity>();
        for (String ns : namespaces) {
            NamespaceListing nsl = refreshResult.namespaceListingMap.get(ns);
            if (nsl != null) {
                r.addAll(nsl.getFunctions());
            }
        }
        return r;
    }

    /**
     * @return List of tables contained in default namespace
     */
    List<TableSQE> getTables() {
        return getTables(DEFAULT_NAMESPACE);
    }

    /**
     * @return List of views contained in default namespace
     */
    List<ServerQEntity> getViews() {
        return getViews(DEFAULT_NAMESPACE);
    }

    /**
     * @return List of variables contained in default namespace
     */
    List<ServerQEntity> getVariables() {
        return getVariables(DEFAULT_NAMESPACE);
    }

    /**
     * @return List of functions contained in default namespace
     */
    List<ServerQEntity> getFunctions() {
        return getFunctions(DEFAULT_NAMESPACE);
    }

    /**
     * @return If there was an error retrieving the tree this will contain some details.
     */
    public String getErrMsg() {
        return refreshResult.errMsg;
    }

    /**
     * @return true only if there was an exception while last retrieving the tree from server
     */
    public boolean isErrorRetrievingTree() {
        return errorRetrievingTree;
    }

    /**
     * @return true only if the namespace exists
     **/
    boolean namespaceExists(String namespace) {
        return refreshResult.namespaceListingMap.get(namespace) != null;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("namespaceListingMap", refreshResult.namespaceListingMap)
                .toString();
    }

    public boolean elementExists(String namespace, QEntity element) {
        NamespaceListing nsListing = refreshResult.namespaceListingMap.get(namespace);
        if (nsListing != null) {
            return nsListing.contains(element);
        }
        return false;
    }


}
